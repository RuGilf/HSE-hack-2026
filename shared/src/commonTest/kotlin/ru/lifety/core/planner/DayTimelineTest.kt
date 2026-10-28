package ru.lifety.core.planner

import kotlinx.datetime.*
import ru.lifety.core.model.Task
import ru.lifety.core.model.TaskPlan
import kotlin.test.*
import kotlin.time.Instant

class DayTimelineTest {
    private val date = LocalDate(2026, 10, 28)
    private val zone = TimeZone.UTC
    private fun at(hour: Int, minute: Int = 0) = plannerTimestamp(date, hour, minute, zone)
    private fun task(id: String, start: Long, deadline: Long? = null) = Task(id, id, start, deadline, false)

    @Test fun freeWindowsNeverOfferElapsedTimeTodayOrOnPastDays() {
        val windows = listOf(TimeWindow(at(8), at(9)), TimeWindow(at(10), at(22)))
        assertEquals(listOf(TimeWindow(at(13, 30), at(22))), availableTimeWindows(windows, date, at(13, 23), zone))
        assertEquals(listOf(TimeWindow(at(13, 30), at(22))), availableTimeWindows(windows, date, at(13, 30), zone))
        assertTrue(availableTimeWindows(windows, date, at(22), zone).isEmpty())
        assertTrue(availableTimeWindows(windows, date, at(8) + 86_400_000, zone).isEmpty())
        assertEquals(windows, availableTimeWindows(windows, date, at(8) - 86_400_000, zone))
    }

    @Test fun overlappingIntervalsAreMarkedAndTheirUnionDoesNotDoubleCountBusyTime() {
        val tasks = listOf(task("a", at(9)), task("b", at(9, 30)), task("c", at(10, 30)))
        val details = tasks.associate { it.id to TaskPlan(it.id, 60) }
        val result = dayTimeline(tasks, details, date, zone, 8, 12)
        assertEquals(listOf(true, true, false), result.tasks.map { it.overlaps })
        assertEquals(150, result.busyMinutes)
        assertEquals(listOf(TimeWindow(at(8), at(9)), TimeWindow(at(11, 30), at(12))), result.free)
    }

    @Test fun unknownDurationsStayUnknownAndUnscheduledDeadlinesAreNotInventedAsAppointments() {
        val tasks = listOf(task("unknown", at(10)), Task("deadline", "Срок", null, at(12), false))
        val result = dayTimeline(tasks, emptyMap(), date, zone)
        assertEquals(listOf("unknown"), result.tasks.map { it.task.id })
        assertEquals(1, result.unknownDurations); assertNull(result.tasks.single().end)
        assertEquals(0, result.busyMinutes)
        assertEquals(listOf(TimeWindow(at(8), at(22))), result.free)
    }

    @Test fun overnightWorkIsVisibleOnBothDaysAndClippedForFreeTimeCalculations() {
        val start = Instant.parse("2026-10-27T23:00:00Z").toEpochMilliseconds()
        val tasks = listOf(task("night", start))
        val plans = mapOf("night" to TaskPlan("night", 120))
        val result = dayTimeline(tasks, plans, date, zone, 0, 24)
        assertEquals(start, result.tasks.single().start)
        assertEquals(at(1), result.tasks.single().end)
        assertEquals(60, result.busyMinutes)
        assertEquals(at(1), result.free.single().start)
    }

    @Test fun aFullLocalDayUsesDstBoundariesRatherThanAssuming24Hours() {
        val berlin = TimeZone.of("Europe/Berlin")
        assertEquals(1380, dayTimeline(emptyList(), emptyMap(), LocalDate(2026, 3, 29), berlin, 0, 24).free.single().minutes)
        assertEquals(1500, dayTimeline(emptyList(), emptyMap(), LocalDate(2026, 10, 25), berlin, 0, 24).free.single().minutes)
    }

    @Test fun validationChecksFinishAgainstDeadlineAndKeepsOverdueReschedulingPossible() {
        val task = task("a", at(10), at(11))
        assertNull(taskIntervalError(task, 60, at(8)))
        assertNotNull(taskIntervalError(task, 61, at(8)))
        assertNull(taskIntervalError(task, 120, at(12)))
        assertNotNull(taskIntervalError(task, 0, at(8)))
        assertNotNull(taskIntervalError(task, 1441, at(8)))
    }

    @Test fun pickingAnEndTimeSupportsMidnightAndRejectsDstGaps() {
        assertEquals(90, durationUntil(at(23), 0, 30, zone))
        assertEquals(1440, durationUntil(at(9), 9, 0, zone))
        val berlin = TimeZone.of("Europe/Berlin")
        val start = plannerTimestamp(LocalDate(2026, 3, 29), 1, 0, berlin)
        assertEquals(60, durationUntil(start, 3, 0, berlin))
        assertFailsWith<IllegalArgumentException> { durationUntil(start, 2, 30, berlin) }
    }
}
