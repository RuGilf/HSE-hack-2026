package ru.lifety.core.time

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import ru.lifety.core.model.Task
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class PlannerDatesTest {
    @Test
    fun nativeDatePickerPreservesCivilDatesInBothExtremeOffsetsAndDstChanges() {
        for (zoneId in listOf("Pacific/Kiritimati", "Etc/GMT+12", "Europe/Moscow", "Europe/Berlin", "America/Sao_Paulo")) {
            val zone = TimeZone.of(zoneId)
            for (date in listOf(LocalDate(2026, 10, 30), LocalDate(2026, 3, 29), LocalDate(2026, 10, 25), LocalDate(2018, 11, 4), LocalDate(2028, 2, 29))) {
                val timestamp = localDatePickerMillis(date, zone)
                assertEquals(date, localDate(timestamp, zone), "$zoneId: $date")
                assertEquals(12, Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(zone).hour)
            }
        }
    }

    @Test
    fun calendarUsesLocalDatesAndDoesNotDuplicateScheduleAndDeadline() {
        val zone = TimeZone.of("Europe/Moscow")
        val day = LocalDate(2026, 10, 25)
        val scheduled = Instant.parse("2026-10-24T22:30:00Z").toEpochMilliseconds()
        val deadline = Instant.parse("2026-10-25T12:00:00Z").toEpochMilliseconds()
        val task = Task("one", "Одна задача", scheduled, deadline, false)
        val deadlineOnly = task.copy(id = "two", scheduledAt = null)
        assertEquals(listOf(task, deadlineOnly), tasksOnDate(listOf(deadlineOnly, task), day, zone))
        assertTrue(tasksOnDate(listOf(task), LocalDate(2026, 10, 24), zone).isEmpty())
    }

    @Test
    fun datePickerUtcMidnightIsConvertedAsCivilDateWithoutTimezoneShift() {
        val zone = TimeZone.of("America/Los_Angeles")
        val original = Instant.parse("2026-10-25T16:30:00Z").toEpochMilliseconds()
        val pickerDate = LocalDate(2026, 10, 27).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        val changed = Instant.fromEpochMilliseconds(changeDate(original, pickerDate, zone)).toLocalDateTime(zone)
        assertEquals(LocalDate(2026, 10, 27), changed.date)
        assertEquals(9, changed.hour)
        assertEquals(30, changed.minute)
        assertEquals(pickerDate, datePickerMillis(changeDate(original, pickerDate, zone), zone))
    }

    @Test
    fun calendarGridStartsOnMondayAndContainsLeapDay() {
        val dates = monthGrid(LocalDate(2028, 2, 1))
        assertEquals(42, dates.size)
        assertEquals(DayOfWeek.MONDAY, dates.first().dayOfWeek)
        assertEquals(42, dates.distinct().size)
        assertTrue(LocalDate(2028, 2, 29) in dates)
    }

    @Test
    fun nonExistentDaylightSavingTimeIsDetected() {
        val zone = TimeZone.of("Europe/Berlin")
        val original = Instant.parse("2026-03-29T00:30:00Z").toEpochMilliseconds()
        val nonexistent = changeTime(original, 2, 30, zone)
        assertFalse(isLocalTimePreserved(nonexistent, 2, 30, zone))
        val valid = changeTime(original, 4, 30, zone)
        assertTrue(isLocalTimePreserved(valid, 4, 30, zone))
    }
}
