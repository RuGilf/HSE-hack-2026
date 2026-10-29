import Foundation
import HealthKit
import LifetyShared

/// Read-only adapter. Apple intentionally hides read-authorization status from apps.
final class AppleHealthSource: IosHealthSource {
    private let store = HKHealthStore()
    private let lock = NSLock()
    private var queries: [HKQuery] = []
    private var generation = UUID()
    private var pending: HealthReadState?
    var available: Bool { HKHealthStore.isHealthDataAvailable() }

    func requestAccess(sleep: Bool, steps: Bool, completion: @escaping (String?) -> Void) {
        var types = Set<HKObjectType>()
        if sleep, let type = HKObjectType.categoryType(forIdentifier: .sleepAnalysis) { types.insert(type) }
        if steps, let type = HKObjectType.quantityType(forIdentifier: .stepCount) { types.insert(type) }
        guard available, !types.isEmpty else { completion("Apple Health недоступен"); return }
        store.requestAuthorization(toShare: [], read: types) { success, error in
            DispatchQueue.main.async { completion(success ? nil : error?.localizedDescription ?? "Не удалось открыть доступ") }
        }
    }

    func cancelRead() {
        lock.lock()
        let previous = queries
        queries = []
        generation = UUID()
        pending?.cancel(); pending = nil
        lock.unlock()
        previous.forEach(store.stop)
    }

    func readDays(sleep: Bool, steps: Bool, days: Int32,
                  completion: @escaping ([HealthSnapshot]?, String?) -> Void) {
        cancelRead()
        guard available, (1...30).contains(days), sleep || steps else { completion(nil, "Apple Health недоступен"); return }
        let calendar = Calendar.current
        let end = Date()
        let today = calendar.startOfDay(for: end)
        guard let start = calendar.date(byAdding: .day, value: 1 - Int(days), to: today) else { return }
        let dates = (0..<Int(days)).compactMap { calendar.date(byAdding: .day, value: $0, to: start) }
        let predicate = HKQuery.predicateForSamples(withStart: start, end: end, options: [])
        lock.lock(); let token = generation; lock.unlock()
        let state = HealthReadState(count: (sleep ? 1 : 0) + (steps ? 1 : 0), dates: dates, today: today, end: end) { [weak self] rows, error in
            guard let self else { return }
            self.lock.lock(); let current = self.generation == token
            let finished = self.queries; if current { self.queries = []; self.pending = nil }; self.lock.unlock()
            guard current else { return }
            finished.forEach(self.store.stop)
            completion(rows, error)
        }
        lock.lock(); pending = state; lock.unlock()
        func execute(_ query: HKQuery) {
            lock.lock(); let current = generation == token
            if current { queries.append(query) }; lock.unlock()
            if current { store.execute(query) }
        }
        if steps, let type = HKObjectType.quantityType(forIdentifier: .stepCount) {
            let query = HKStatisticsCollectionQuery(quantityType: type, quantitySamplePredicate: predicate,
                options: .cumulativeSum, anchorDate: start, intervalComponents: DateComponents(day: 1))
            query.initialResultsHandler = { [weak state] _, collection, error in
                guard let state else { return }
                var totals: [Date: Int] = [:]
                collection?.enumerateStatistics(from: start, to: end) { statistics, _ in
                    if let count = statistics.sumQuantity()?.doubleValue(for: .count()),
                       count.isFinite, count >= 0, count <= Double(Int32.max) {
                        totals[calendar.startOfDay(for: statistics.startDate)] = Int(count)
                    }
                }
                state.finish(sleep: nil, steps: totals, error: error)
            }
            execute(query)
        }
        if sleep, let type = HKObjectType.categoryType(forIdentifier: .sleepAnalysis) {
            let query = HKSampleQuery(sampleType: type, predicate: predicate, limit: HKObjectQueryNoLimit, sortDescriptors: nil) { [weak state] _, samples, error in
                guard let state else { return }
                // Stage records and overlapping sources are intervals, never values to simply sum.
                let asleep = Set([HKCategoryValueSleepAnalysis.asleepUnspecified.rawValue,
                    HKCategoryValueSleepAnalysis.asleepCore.rawValue,
                    HKCategoryValueSleepAnalysis.asleepDeep.rawValue,
                    HKCategoryValueSleepAnalysis.asleepREM.rawValue])
                let intervals = (samples as? [HKCategorySample] ?? []).filter { asleep.contains($0.value) }
                    .map { DateInterval(start: $0.startDate, end: $0.endDate) }
                let totals = SleepHistory.totals(intervals: intervals, dates: dates, end: end, calendar: calendar)
                state.finish(sleep: totals, steps: nil, error: error)
            }
            execute(query)
        }
    }
}

/// Stopped HKQuery instances need not call their handler. Cancellation releases the callback
/// directly instead of leaving an unfinished dispatch group holding a Compose continuation.
private final class HealthReadState {
    private let lock = NSLock()
    private var remaining: Int
    private var sleepByDay: [Date: Int] = [:]
    private var stepsByDay: [Date: Int] = [:]
    private var failed = false
    private let dates: [Date], today: Date, end: Date
    private var completion: (([HealthSnapshot]?, String?) -> Void)?
    init(count: Int, dates: [Date], today: Date, end: Date, completion: @escaping ([HealthSnapshot]?, String?) -> Void) {
        remaining = count; self.dates = dates; self.today = today; self.end = end; self.completion = completion
    }
    func cancel() { lock.lock(); completion = nil; lock.unlock() }
    func finish(sleep: [Date: Int]?, steps: [Date: Int]?, error: Error?) {
        lock.lock()
        guard completion != nil else { lock.unlock(); return }
        if let sleep { sleepByDay = sleep }
        if let steps { stepsByDay = steps }
        failed = failed || error != nil
        remaining -= 1
        guard remaining == 0, let callback = completion else { lock.unlock(); return }
        completion = nil
        let rows = dates.compactMap { day -> HealthSnapshot? in
            let sleep = sleepByDay[day], steps = stepsByDay[day]
            guard sleep != nil || steps != nil else { return nil }
            return HealthSnapshot(timestamp: Int64((day == today ? end : day).timeIntervalSince1970 * 1000),
                sleepMinutes: sleep.map { KotlinInt(int: Int32($0)) }, steps: steps.map { KotlinInt(int: Int32($0)) })
        }
        let failure = failed ? "Не удалось прочитать Apple Health" : nil
        lock.unlock()
        DispatchQueue.main.async { callback(failure == nil ? rows : nil, failure) }
    }
}

/// Additional read-only categories. Each response is an alternative daily value, never a missing zero.
final class AppleMetricSource: IosMetricSource {
    private let store = HKHealthStore()
    private var queries: [HKQuery] = []
    private var generation = UUID()
    private let gate = NSLock()
    private var pending: MetricReadState?
    var available: Bool { HKHealthStore.isHealthDataAvailable() }
    private func specification(_ key: String) -> (HKQuantityType, HKUnit, HKStatisticsOptions)? {
        let identifier: HKQuantityTypeIdentifier
        let unit: HKUnit
        let option: HKStatisticsOptions
        switch key {
        case "weight": identifier = .bodyMass; unit = .gramUnit(with: .kilo); option = .mostRecent
        case "resting-heart-rate": identifier = .restingHeartRate; unit = .count().unitDivided(by: .minute()); option = .mostRecent
        case "water": identifier = .dietaryWater; unit = .literUnit(with: .milli); option = .cumulativeSum
        case "active-energy": identifier = .activeEnergyBurned; unit = .kilocalorie(); option = .cumulativeSum
        default: return nil
        }
        guard let type = HKQuantityType.quantityType(forIdentifier: identifier) else { return nil }
        return (type, unit, option)
    }
    func requestAccess(keys: [String], completion: @escaping (String?) -> Void) {
        let types = Set(keys.compactMap { specification($0)?.0 })
        guard available, !types.isEmpty, types.count == keys.count else { completion("Тип данных недоступен"); return }
        store.requestAuthorization(toShare: [], read: types) { success, error in
            DispatchQueue.main.async { completion(success ? nil : error?.localizedDescription ?? "Ошибка доступа") }
        }
    }
    func cancel() {
        gate.lock(); let previous = queries; queries = []; generation = UUID()
        pending?.cancel(); pending = nil; gate.unlock()
        previous.forEach(store.stop)
    }
    func readDays(keys: [String], completion: @escaping ([HealthApiSample]?, String?) -> Void) {
        cancel()
        guard available, !keys.isEmpty, Set(keys).count == keys.count, keys.allSatisfy({ specification($0) != nil }) else { completion(nil, "Тип данных недоступен"); return }
        let calendar = Calendar.current
        let end = Date(), today = calendar.startOfDay(for: Date())
        guard let start = calendar.date(byAdding: .day, value: -29, to: today) else { completion(nil, "Ошибка даты"); return }
        gate.lock(); let token = generation; gate.unlock()
        let state = MetricReadState(count: keys.count) { [weak self] rows, error in
            guard let self else { return }
            self.gate.lock(); let current = self.generation == token
            let finished = current ? self.queries : []
            if current { self.queries = []; self.pending = nil }; self.gate.unlock()
            guard current else { return }
            finished.forEach(self.store.stop)
            completion(rows, error)
        }
        gate.lock(); pending = state; gate.unlock()
        for key in keys {
            let (type, unit, option) = specification(key)!
            let predicate = HKQuery.predicateForSamples(withStart: start, end: end, options: [])
            let query = HKStatisticsCollectionQuery(quantityType: type, quantitySamplePredicate: predicate,
                options: option, anchorDate: start, intervalComponents: DateComponents(day: 1))
            query.initialResultsHandler = { [weak state] _, collection, error in
                guard let state else { return }
                var values: [HealthApiSample] = []
                collection?.enumerateStatistics(from: start, to: end) { statistics, _ in
                    let quantity = option == .cumulativeSum ? statistics.sumQuantity() : statistics.mostRecentQuantity()
                    if let value = quantity?.doubleValue(for: unit), value.isFinite, value >= 0, value <= 1e9 {
                        let day = calendar.startOfDay(for: statistics.startDate)
                        values.append(HealthApiSample(key: key, timestamp: Int64(day.timeIntervalSince1970 * 1000), value: value))
                    }
                }
                state.finish(values: values, error: error)
            }
            gate.lock(); let current = generation == token
            if current { queries.append(query) }; gate.unlock()
            if current { store.execute(query) }
        }
    }
}

private final class MetricReadState {
    private let lock = NSLock()
    private var remaining: Int
    private var output: [HealthApiSample] = []
    private var failed = false
    private var completion: (([HealthApiSample]?, String?) -> Void)?
    init(count: Int, completion: @escaping ([HealthApiSample]?, String?) -> Void) {
        remaining = count; self.completion = completion
    }
    func cancel() { lock.lock(); completion = nil; output = []; lock.unlock() }
    func finish(values: [HealthApiSample], error: Error?) {
        lock.lock()
        guard completion != nil else { lock.unlock(); return }
        output.append(contentsOf: values); failed = failed || error != nil; remaining -= 1
        guard remaining == 0, let callback = completion else { lock.unlock(); return }
        completion = nil
        let rows = output.sorted { $0.timestamp == $1.timestamp ? $0.key < $1.key : $0.timestamp < $1.timestamp }
        let failure = failed ? "Ошибка чтения Apple Health" : nil
        output = []; lock.unlock()
        DispatchQueue.main.async { callback(failure == nil ? rows : nil, failure) }
    }
}
