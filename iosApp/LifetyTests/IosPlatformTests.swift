import XCTest
import Security
@testable import Lifety

final class IosPlatformTests: XCTestCase {
    private func date(_ value: String) -> Date { ISO8601DateFormatter().date(from: value)! }
    private var utc: Calendar { var calendar = Calendar(identifier: .gregorian); calendar.timeZone = TimeZone(secondsFromGMT: 0)!; return calendar }

    func testOverlappingSleepStagesAreNotDoubleCountedAndCrossMidnightIsSplit() {
        let first = date("2026-10-24T00:00:00Z"), second = date("2026-10-25T00:00:00Z")
        let intervals = [DateInterval(start: date("2026-10-24T22:00:00Z"), end: date("2026-10-25T06:00:00Z")),
                         DateInterval(start: date("2026-10-24T23:00:00Z"), end: date("2026-10-25T02:00:00Z"))]
        let result = SleepHistory.totals(intervals: intervals, dates: [first, second], end: date("2026-10-26T00:00:00Z"), calendar: utc)
        XCTAssertEqual(result[first], 120)
        XCTAssertEqual(result[second], 360)
    }

    func testMissingSleepStaysUnknownAndCurrentDayIsClipped() {
        let day = date("2026-10-25T00:00:00Z"), end = date("2026-10-25T03:00:00Z")
        XCTAssertTrue(SleepHistory.totals(intervals: [], dates: [day], end: end, calendar: utc).isEmpty)
        let result = SleepHistory.totals(intervals: [DateInterval(start: day, end: date("2026-10-25T06:00:00Z"))], dates: [day], end: end, calendar: utc)
        XCTAssertEqual(result[day], 180)
    }

    func testDaylightSavingDayUsesCalendarBoundaries() {
        var calendar = Calendar(identifier: .gregorian); calendar.timeZone = TimeZone(identifier: "Europe/Berlin")!
        let day = date("2026-10-24T22:00:00Z"), next = date("2026-10-25T23:00:00Z")
        let result = SleepHistory.totals(intervals: [DateInterval(start: day, end: next)], dates: [day], end: next, calendar: calendar)
        XCTAssertEqual(result[day], 25 * 60)
    }

    func testKeychainPersistsAcrossStoreInstancesAndUpdatesExistingEntry() {
        let service = "ru.lifety.ios.tests.\(UUID().uuidString)"
        defer { SecItemDelete([kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service] as CFDictionary) }
        let first = KeychainStore(service: service)
        XCTAssertNil(first.read().value)
        XCTAssertTrue(first.write(value: "synthetic-test-credentials"))
        let second = KeychainStore(service: service)
        XCTAssertEqual(second.read().value, "synthetic-test-credentials")
        XCTAssertNil(second.read().error)
        XCTAssertTrue(second.write(value: "synthetic-updated"))
        XCTAssertEqual(first.read().value, "synthetic-updated")
    }
}
