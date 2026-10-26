import Foundation

/// Clip to calendar days, then union overlapping stages/sources before summing duration.
enum SleepHistory {
    static func totals(intervals: [DateInterval], dates: [Date], end: Date, calendar: Calendar) -> [Date: Int] {
        var totals: [Date: Int] = [:]
        for day in dates {
            guard let next = calendar.date(byAdding: .day, value: 1, to: day) else { continue }
            let clipped = intervals.compactMap { interval -> DateInterval? in
                let lower = max(day, interval.start), upper = min(next, end, interval.end)
                return upper > lower ? DateInterval(start: lower, end: upper) : nil
            }.sorted { $0.start < $1.start }
            var merged: [DateInterval] = []
            for interval in clipped {
                if let last = merged.last, interval.start <= last.end {
                    merged[merged.count - 1] = DateInterval(start: last.start, end: max(last.end, interval.end))
                } else { merged.append(interval) }
            }
            if !merged.isEmpty { totals[day] = Int(merged.reduce(0) { $0 + $1.duration } / 60) }
        }
        return totals
    }
}
