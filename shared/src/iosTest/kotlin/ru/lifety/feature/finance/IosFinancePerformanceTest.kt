package ru.lifety.feature.finance

import platform.Foundation.*
import kotlin.test.*
import kotlin.time.measureTime

class IosFinancePerformanceTest {
    @Test fun calendarConversionMatchesFormerFormatterAndReportsComparison() {
        val timestamps = List(1000) { 1_730_000_000_000L + it * 37_000_000L }
        fun former(timestamp: Long): FinanceDate = requireNotNull(FinanceDate.parse(NSDateFormatter().apply {
            locale = NSLocale(localeIdentifier = "en_US_POSIX")
            timeZone = NSTimeZone.defaultTimeZone
            dateFormat = "yyyy-MM-dd"
            lenient = false
        }.stringFromDate(NSDate.dateWithTimeIntervalSince1970(timestamp / 1000.0))))
        timestamps.take(20).forEach { former(it); localFinanceDate(it) }
        var oldResult = emptyList<FinanceDate>()
        var newResult = emptyList<FinanceDate>()
        val oldSamples = mutableListOf<Double>()
        val newSamples = mutableListOf<Double>()
        repeat(3) {
            oldSamples += measureTime { oldResult = timestamps.map(::former) }.inWholeNanoseconds / 1e6
            newSamples += measureTime { newResult = timestamps.map(::localFinanceDate) }.inWholeNanoseconds / 1e6
            assertEquals(oldResult, newResult)
        }
        // Informational benchmark; no timing assertion on shared CI/simulator hardware.
        println("LIFETY_BENCH financeDate 1000 records, median ms: formatter=${oldSamples.sorted()[1]}, calendar=${newSamples.sorted()[1]}")
    }
}
