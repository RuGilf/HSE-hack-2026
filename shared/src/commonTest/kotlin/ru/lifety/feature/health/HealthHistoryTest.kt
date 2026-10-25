package ru.lifety.feature.health

import kotlin.test.*
import kotlin.time.Instant
import kotlinx.datetime.*
import ru.lifety.core.model.HealthSnapshot

class HealthHistoryTest {
    private val utc = TimeZone.UTC
    private val date = LocalDate(2026, 10, 25)
    private fun row(at: String, sleep: Int? = null, steps: Int? = null) = HealthSnapshot(Instant.parse(at).toEpochMilliseconds(), sleep, steps)

    @Test fun usesLatestKnownMetricPerDayWithoutSummingRepeatedCumulativeReadings() {
        val result = healthTimeline(listOf(row("2026-10-25T08:00:00Z", 420, 1000),
            row("2026-10-25T20:00:00Z", steps = 8000), row("2026-10-25T12:00:00Z", 450, 4000)), HealthPeriod.WEEK, date, utc)
        assertEquals(7, result.days.size)
        assertEquals(450, result.days.last().sleepMinutes)
        assertEquals(8000, result.days.last().steps)
        assertEquals(8000L, result.totalSteps)
        assertEquals(450, result.averageSleep)
        assertEquals(1, result.sleepDays)
        assertNull(result.days.first().sleepMinutes)
    }

    @Test fun realZeroIsIncludedButMissingDaysAreExcludedFromAverages() {
        val result = healthTimeline(listOf(row("2026-10-24T12:00:00Z", 0, 0), row("2026-10-25T12:00:00Z", 480, 10000)), HealthPeriod.MONTH, date, utc)
        assertEquals(240, result.averageSleep)
        assertEquals(5000L, result.averageSteps)
        assertEquals(2, result.stepDays)
        assertEquals(30, result.calendarDays)
    }

    @Test fun allTimeRetainsOldHistoryAndDoesNotInventIntermediateRecords() {
        val rows = listOf(row("2023-01-01T00:00:00Z", 400), row("2026-10-25T12:00:00Z", 480))
        val all = healthTimeline(rows, HealthPeriod.ALL, date, utc)
        assertEquals(2, all.days.size)
        assertEquals(2, all.records)
        assertEquals(440, all.averageSleep)
        assertTrue(all.calendarDays > 1000)
        assertEquals(1, healthTimeline(rows, HealthPeriod.WEEK, date, utc).records)
    }

    @Test fun localDaysAndDstUseCalendarDatesInsteadOfFixed24HourBuckets() {
        val berlin = TimeZone.of("Europe/Berlin")
        val result = healthTimeline(listOf(row("2026-10-24T22:30:00Z", 400), row("2026-10-25T23:30:00Z", 500)),
            HealthPeriod.WEEK, LocalDate(2026, 10, 26), berlin)
        assertEquals(400, result.days[result.days.lastIndex - 1].sleepMinutes)
        assertEquals(500, result.days.last().sleepMinutes)
    }

    @Test fun emptyHistoryStaysUnknownAndStepTotalsUseLong() {
        assertNull(healthTimeline(emptyList(), HealthPeriod.WEEK, date, utc).averageSleep)
        assertEquals(emptyList(), healthTimeline(emptyList(), HealthPeriod.ALL, date, utc).days)
        val result = healthTimeline(listOf(row("2026-10-24T12:00:00Z", steps = Int.MAX_VALUE), row("2026-10-25T12:00:00Z", steps = Int.MAX_VALUE)), HealthPeriod.WEEK, date, utc)
        assertEquals(Int.MAX_VALUE.toLong() * 2, result.totalSteps)
    }
}
