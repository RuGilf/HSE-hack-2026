package ru.lifety.core.planner

import kotlinx.datetime.*
import ru.lifety.core.model.*
import ru.lifety.core.planning.*
import kotlin.test.*

class ScheduleTest {
    private val date = LocalDate(2026, 10, 30) // Friday
    private val zone = TimeZone.UTC
    private fun at(hour: Int, minute: Int = 0) = plannerTimestamp(date, hour, minute, zone)

    @Test fun weeklyAndOneOffCommitmentsOnlyAppearOnTheirDates() {
        val weekly = ScheduleBlock("weekly", "Пары", 9 * 60, 11 * 60, setOf(1, 5))
        val once = ScheduleBlock("once", "Встреча", 12 * 60, 13 * 60, date = date.toString())
        val blocks = listOf(once, weekly)
        assertEquals(listOf("weekly", "once"), scheduleOnDate(blocks, date, zone).map { it.block.id })
        assertTrue(scheduleOnDate(blocks, date.plus(DatePeriod(days = 1)), zone).isEmpty())
        assertEquals(listOf("weekly"), scheduleOnDate(blocks, date.plus(DatePeriod(days = 3)), zone).map { it.block.id })
    }

    @Test fun overnightOccurrencesAreVisibleAndClippedOnBothDays() {
        val night = ScheduleBlock("night", "Смена", 23 * 60, 2 * 60, date = date.toString())
        val first = dayTimeline(emptyList(), emptyMap(), date, zone, 0, 24, listOf(night))
        val second = dayTimeline(emptyList(), emptyMap(), date.plus(DatePeriod(days = 1)), zone, 0, 24, listOf(night))
        assertEquals(60, first.busyMinutes)
        assertEquals(120, second.busyMinutes)
        assertEquals(first.schedule.single(), second.schedule.single())
        assertEquals(at(23), first.schedule.single().start)
        assertTrue(scheduleOnDate(listOf(night), date.plus(DatePeriod(days = 2)), zone).isEmpty())
    }

    @Test fun fixedAndTaskIntervalsShareOneBusyUnionAndHalfOpenConflicts() {
        val blocks = listOf(ScheduleBlock("fixed", "Работа", 9 * 60, 11 * 60, setOf(5)))
        val tasks = listOf(Task("overlap", "Дело", at(10), null, false), Task("touch", "После", at(11), null, false))
        val plans = mapOf("overlap" to TaskPlan("overlap", 30), "touch" to TaskPlan("touch", 60))
        val result = dayTimeline(tasks, plans, date, zone, 8, 13, blocks)
        assertEquals(180, result.busyMinutes)
        assertEquals(listOf(true, false), result.tasks.map { it.overlaps })
        assertEquals(listOf(TimeWindow(at(8), at(9)), TimeWindow(at(12), at(13))), result.free)
    }

    @Test fun unknownDurationInsideScheduleHasAConflictButStaysUnknown() {
        val result = dayTimeline(listOf(Task("a", "Дело", at(10), null, false)), emptyMap(), date, zone,
            schedule = listOf(ScheduleBlock("fixed", "Пара", 9 * 60, 11 * 60, setOf(5))))
        assertTrue(result.tasks.single().overlaps)
        assertNull(result.tasks.single().end)
        assertEquals(1, result.unknownDurations)
        assertEquals(120, result.busyMinutes)
    }

    @Test fun invalidDatesAndWeekdaySelectionsCannotBeSaved() {
        val block = ScheduleBlock("a", "Работа", 540, 600, setOf(1))
        assertNull(scheduleError(block))
        assertNotNull(scheduleError(block.copy(weekdays = emptySet())))
        assertNotNull(scheduleError(block.copy(weekdays = setOf(8))))
        assertNotNull(scheduleError(block.copy(startMinute = -1)))
        assertNotNull(scheduleError(block.copy(date = "2026-02-30", weekdays = emptySet())))
        assertNotNull(scheduleError(block.copy(date = date.toString())))
        assertNotNull(scheduleError(block.copy(title = " ")))
    }

    @Test fun fullDayAndDstUseLocalBoundaries() {
        val berlin = TimeZone.of("Europe/Berlin")
        val spring = LocalDate(2026, 3, 29)
        val fall = LocalDate(2026, 10, 25)
        val block = ScheduleBlock("a", "Сутки", 0, 0, setOf(7))
        assertEquals(1380, dayTimeline(emptyList(), emptyMap(), spring, berlin, 0, 24, listOf(block)).busyMinutes)
        assertEquals(1500, dayTimeline(emptyList(), emptyMap(), fall, berlin, 0, 24, listOf(block)).busyMinutes)
    }

    @Test fun snapshotFingerprintIncludesScheduleButIgnoresWeekdaySetOrder() {
        val block = ScheduleBlock("a", "Пары", 540, 600, linkedSetOf(1, 5))
        val snapshot = PlanSnapshot(emptyList(), emptyList(), emptyList(), listOf(block))
        assertEquals(snapshotFingerprint(snapshot), snapshotFingerprint(snapshot.copy(schedule = listOf(block.copy(weekdays = linkedSetOf(5, 1))))))
        assertNotEquals(snapshotFingerprint(snapshot), snapshotFingerprint(snapshot.copy(schedule = listOf(block.copy(title = "Работа")))))
    }

    @Test fun importedPlansRejectBusyTimeAndAllowAnAdjacentTask() {
        val tz = TimeZone.currentSystemDefault()
        val start = plannerTimestamp(date, 9, 0, tz)
        val task = Task("a", "Дело", null, null, false)
        val block = ScheduleBlock("b", "Пара", 540, 600, setOf(5))
        val snapshot = PlanSnapshot(listOf(task), emptyList(), emptyList(), listOf(block))
        fun plan(at: Long) = ImportedPlan("lifety-plan-v1", snapshotFingerprint(snapshot), "План", listOf(
            PlannedTask("a", kotlin.time.Instant.fromEpochMilliseconds(at).toString(), 30, "Сделать")))
        assertFailsWith<IllegalArgumentException> { validateImportedPlan(plan(start), snapshot, start - 1) }
        validateImportedPlan(plan(start + 60 * 60_000), snapshot, start - 1)
        val prompt = prepareChatGptDraft(snapshot, ChatGptPreferences(), null, null, true, "", "req", start - 1, tz).prompt
        assertTrue(prompt.contains("fixedSchedule"))
        assertTrue(prompt.contains("Пара"))
        val excluded = prepareChatGptDraft(snapshot, ChatGptPreferences(includeTasks = false), null, null, true, "", "req", start - 1, tz).prompt
        assertFalse(excluded.contains("Пара"))
    }
}
