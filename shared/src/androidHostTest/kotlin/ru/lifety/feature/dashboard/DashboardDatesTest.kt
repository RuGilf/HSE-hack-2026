package ru.lifety.feature.dashboard

import java.util.TimeZone
import kotlin.test.*

class DashboardDatesTest {
    @Test fun localDayRespectsDstAndMonthRollover() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))
            val now = java.time.Instant.parse("2026-10-25T12:00:00Z").toEpochMilli()
            val period = dashboardPeriod(now)
            assertEquals(25L * 60 * 60 * 1000, period.dayEnd - period.dayStart)
            assertEquals(java.time.Instant.parse("2026-10-24T22:00:00Z").toEpochMilli(), period.dayStart)
            assertEquals(java.time.Instant.parse("2026-10-01T00:00:00+02:00").toEpochMilli(), period.monthStart)
            assertEquals(java.time.Instant.parse("2026-11-01T00:00:00+01:00").toEpochMilli(), period.monthEnd)
        } finally { TimeZone.setDefault(original) }
    }
}
