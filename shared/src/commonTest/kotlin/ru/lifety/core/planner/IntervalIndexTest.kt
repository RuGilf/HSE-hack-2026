package ru.lifety.core.planner

import kotlin.random.Random
import kotlin.test.*
import ru.lifety.core.model.Task
import ru.lifety.core.model.TaskPlan
import kotlinx.datetime.*

class IntervalIndexTest {
    @Test fun matchesBruteForceForNestedAdjacentEqualAndRandomIntervals() {
        val random = Random(42)
        val intervals = listOf(TimeWindow(0, 1000), TimeWindow(10, 20), TimeWindow(20, 30), TimeWindow(10, 20)) +
            List(500) { val start = random.nextLong(-1000, 1000); TimeWindow(start, start + random.nextLong(1, 600)) }
        val index = IntervalIndex(intervals)
        repeat(2000) {
            val start = random.nextLong(-1200, 1600)
            val end = start + random.nextLong(1, 300)
            assertEquals(intervals.count { it.start < end && start < it.end }, index.count(start, end))
        }
        assertEquals(0, index.count(20, 20))
        assertEquals(0, IntervalIndex(emptyList()).count(0, 10))
    }

    @Test fun plannerFlagsMatchPreviousPairwiseRuleIncludingUnknownDuration() {
        val date = LocalDate(2026, 10, 31)
        val base = date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        val random = Random(123)
        val tasks = List(600) { i -> Task("$i", "$i", base + random.nextInt(0, 1439) * 60_000L, null, false) }
        val plans = tasks.mapIndexedNotNull { i, task -> if (i % 4 == 0) null else task.id to TaskPlan(task.id, random.nextInt(1, 240)) }.toMap()
        val result = dayTimeline(tasks, plans, date, TimeZone.UTC)
        result.tasks.forEach { item ->
            val expected = result.tasks.any { other -> other.task.id != item.task.id && other.end != null && item.start < other.end && other.start < (item.end ?: item.start + 1) }
            assertEquals(expected, item.overlaps, item.task.id)
        }
    }
}
