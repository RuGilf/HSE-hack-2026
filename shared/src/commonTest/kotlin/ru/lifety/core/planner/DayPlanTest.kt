package ru.lifety.core.planner

import kotlinx.datetime.*
import ru.lifety.core.model.Task
import ru.lifety.core.time.localDate
import kotlin.test.*
import kotlin.time.Instant

class DayPlanTest {
    private val zone = TimeZone.of("Europe/Moscow")
    private val date = LocalDate(2026, 10, 26)
    private fun at(iso: String) = Instant.parse(iso).toEpochMilliseconds()
    private val now = at("2026-10-26T09:00:00Z")
    private fun task(id: String, scheduled: Long? = null, deadline: Long? = null, done: Boolean = false) = Task(id, id, scheduled, deadline, done)

    @Test fun deadlineTakesPrecedenceWithoutDuplicatingATaskScheduledOnTheSameDay() {
        val both = task("both", at("2026-10-26T08:00:00Z"), at("2026-10-26T15:00:00Z"))
        val scheduled = task("scheduled", at("2026-10-26T10:00:00Z"), at("2026-10-28T15:00:00Z"))
        val onlyDue = task("due", deadline = at("2026-10-26T12:00:00Z"))
        val plan = dayPlan(listOf(both, scheduled, onlyDue), date, now, zone)
        assertEquals(listOf(onlyDue, both), plan.due)
        assertEquals(listOf(scheduled), plan.scheduled)
        assertEquals(3, plan.activeCount)
        assertEquals(3, (plan.due + plan.scheduled).map { it.id }.distinct().size)
    }

    @Test fun oldOutstandingDeadlinesCarryIntoTodayButNotEveryFutureDay() {
        val old = task("old", deadline = at("2026-10-20T12:00:00Z"))
        val thisMorning = task("morning", deadline = at("2026-10-26T08:00:00Z"))
        val finished = old.copy(id = "done", isCompleted = true)
        val plan = dayPlan(listOf(thisMorning, old, finished), date, now, zone)
        assertEquals(listOf(old, thisMorning), plan.overdue)
        assertTrue(plan.due.isEmpty())
        assertEquals(2, plan.activeCount)
        assertTrue(dayPlan(listOf(old), date.plus(DatePeriod(days = 1)), now, zone).overdue.isEmpty())
        assertTrue(dayPlan(listOf(old), LocalDate(2026, 10, 20), now, zone).due.contains(old))
    }

    @Test fun undatedBacklogIsExplicitAndDoesNotPretendToBeScheduledTomorrow() {
        val undated = task("backlog")
        val finished = undated.copy(id = "done", isCompleted = true)
        val today = dayPlan(listOf(undated, finished), date, now, zone)
        assertEquals(listOf(undated), today.undated)
        assertEquals(0, today.activeCount)
        assertTrue(today.completed.isEmpty()) // The model has no completion-date field.
        assertTrue(dayPlan(listOf(undated), date.plus(DatePeriod(days = 1)), now, zone).undated.isEmpty())
    }

    @Test fun localMidnightAndCompletedTasksUseTheActualSelectedDate() {
        val due = task("after-midnight", deadline = at("2026-10-25T22:30:00Z"))
        val complete = due.copy(id = "complete", isCompleted = true)
        val plan = dayPlan(listOf(due, complete), date, now, zone)
        assertEquals(listOf(due), plan.overdue)
        assertEquals(listOf(complete), plan.completed)
        assertTrue(dayPlan(listOf(due, complete), LocalDate(2026, 10, 25), now, zone).due.isEmpty())
    }

    @Test fun aDeadlineAtTheCurrentInstantIsDueRatherThanOverdue() {
        val due = task("now", deadline = now)
        val plan = dayPlan(listOf(due), date, now, zone)
        assertEquals(listOf(due), plan.due)
        assertTrue(plan.overdue.isEmpty())
    }

    @Test fun datePresetsKeepCivilDateTimeAndHandleDstWithoutFixed24HourOffsets() {
        val berlin = TimeZone.of("Europe/Berlin")
        val spring = LocalDate(2026, 3, 29)
        assertFailsWith<IllegalArgumentException> { plannerTimestamp(spring, 2, 30, berlin) }
        val deadline = plannerTimestamp(spring, 23, 59, berlin)
        assertEquals(spring, localDate(deadline, berlin))
        assertEquals("23:59", formatPlannerTime(deadline, berlin))
        assertEquals(at("2026-03-29T21:59:00Z"), deadline)
        assertFailsWith<IllegalArgumentException> {
            plannerTimestamp(LocalDate(2011, 12, 30), 9, 0, TimeZone.of("Pacific/Apia"))
        }
    }

    @Test fun futureDeadlineCannotPrecedeScheduleButOverdueWorkCanBeReplanned() {
        val past = at("2026-10-20T09:00:00Z")
        assertNull(taskTimingError(task("deadline-only", deadline = past), now))
        assertNull(taskTimingError(task("schedule-only", scheduled = now), now))
        assertNull(taskTimingError(task("equal", now, now), now))
        assertNotNull(taskTimingError(task("invalid", now + 120_000, now + 60_000), now))
        assertNull(taskTimingError(task("replanned-overdue", now + 120_000, past), now))
        assertNull(taskTimingError(task("completed", now + 120_000, now + 60_000, true), now))
        assertNull(taskTimingError(task("past-valid", past, past + 60_000), now))
    }
}
