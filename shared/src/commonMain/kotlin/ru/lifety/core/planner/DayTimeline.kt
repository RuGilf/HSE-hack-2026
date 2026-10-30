package ru.lifety.core.planner

import kotlinx.datetime.*
import ru.lifety.core.model.Task
import ru.lifety.core.model.TaskPlan
import ru.lifety.core.model.ScheduleBlock
import ru.lifety.core.time.localDate
import kotlin.time.Clock
import kotlin.time.Instant

data class TimeWindow(val start: Long, val end: Long) { val minutes get() = ((end - start) / 60_000).toInt() }
data class TimedTask(val task: Task, val start: Long, val end: Long?, val overlaps: Boolean, val missesDeadline: Boolean)
data class DayTimeline(val tasks: List<TimedTask>, val free: List<TimeWindow>, val busyMinutes: Int, val unknownDurations: Int,
    val schedule: List<ScheduleOccurrence> = emptyList(), val window: TimeWindow? = null)

/** Offers today's remaining time on quarter-hour boundaries; past days keep their appointments only. */
fun availableTimeWindows(windows: List<TimeWindow>, date: LocalDate, now: Long,
    zone: TimeZone = TimeZone.currentSystemDefault()): List<TimeWindow> {
    val today = localDate(now, zone)
    if (date < today) return emptyList()
    if (date > today) return windows
    val step = 15 * 60_000L
    val remainder = ((now % step) + step) % step
    val from = now + if (remainder == 0L) 0 else step - remainder
    return windows.map { it.copy(start = maxOf(it.start, from)) }.filter { it.end > it.start }
}

/** Intersections are half-open; occupied time is a union, so overlapping work is never double-counted. */
fun dayTimeline(tasks: List<Task>, plans: Map<String, TaskPlan>, date: LocalDate,
    zone: TimeZone = TimeZone.currentSystemDefault(), fromHour: Int = 8, untilHour: Int = 22,
    schedule: List<ScheduleBlock> = emptyList()): DayTimeline {
    require(fromHour in 0..23 && untilHour in 1..24 && fromHour < untilHour)
    val dayStart = date.atStartOfDayIn(zone).toEpochMilliseconds()
    val dayEnd = date.plus(DatePeriod(days = 1)).atStartOfDayIn(zone).toEpochMilliseconds()
    if (dayEnd <= dayStart) return DayTimeline(emptyList(), emptyList(), 0, 0)
    val windowStart = if (fromHour == 0) dayStart else LocalDateTime(date, LocalTime(fromHour, 0)).toInstant(zone).toEpochMilliseconds()
    val windowEnd = if (untilHour == 24) dayEnd else LocalDateTime(date, LocalTime(untilHour, 0)).toInstant(zone).toEpochMilliseconds()
    val entries = tasks.mapNotNull { task ->
        val start = task.scheduledAt ?: return@mapNotNull null
        val duration = plans[task.id]?.durationMinutes?.takeIf { it in 1..1440 }
        val end = duration?.let { start.takeIf { it <= Long.MAX_VALUE - duration * 60_000L }?.plus(duration * 60_000L) }
        if (start >= dayEnd || (end ?: (start + 1)) <= dayStart) return@mapNotNull null
        TimedTask(task, start, end, overlaps = false, missesDeadline = end != null && task.deadlineAt?.let { end > it } == true)
    }.sortedWith(compareBy<TimedTask> { it.start }.thenBy { it.task.id })
    val fixed = scheduleOnDate(schedule, date, zone)
    val marked = entries.map { item -> item.copy(overlaps = entries.any { other ->
        other.task.id != item.task.id && other.end != null && item.start < other.end && other.start < (item.end ?: item.start + 1)
    } || fixed.any { item.start < it.end && it.start < (item.end ?: item.start + 1) }) }
    val occupied = (entries.mapNotNull { item -> item.end?.let { TimeWindow(item.start, it) } } + fixed.map { TimeWindow(it.start, it.end) })
        .map { TimeWindow(maxOf(windowStart, it.start), minOf(windowEnd, it.end)) }
        .filter { it.end > it.start }.sortedBy { it.start }
    val merged = mutableListOf<TimeWindow>()
    occupied.forEach { next ->
        val last = merged.lastOrNull()
        if (last != null && next.start <= last.end) merged[merged.lastIndex] = last.copy(end = maxOf(last.end, next.end)) else merged += next
    }
    val free = buildList {
        var cursor = windowStart
        merged.forEach { interval -> if (interval.start > cursor) add(TimeWindow(cursor, interval.start)); cursor = interval.end }
        if (cursor < windowEnd) add(TimeWindow(cursor, windowEnd))
    }
    return DayTimeline(marked, free, merged.sumOf { it.minutes }, entries.count { it.end == null }, fixed, TimeWindow(windowStart, windowEnd))
}

fun taskIntervalError(task: Task, duration: Int?, now: Long = Clock.System.now().toEpochMilliseconds()): String? {
    taskTimingError(task, now)?.let { return it }
    if (duration == null) return null
    if (duration !in 1..1440) return "Укажите длительность от 1 до 1440 минут"
    val start = task.scheduledAt ?: return null
    if (start > Long.MAX_VALUE - duration * 60_000L) return "Проверьте время выполнения"
    return if (!task.isCompleted && task.deadlineAt != null && task.deadlineAt >= now && start + duration * 60_000L > task.deadlineAt)
        "Задача заканчивается позже дедлайна. Измените начало, длительность или срок." else null
}

/** An earlier clock time means the following day; actual elapsed minutes account for DST. */
fun durationUntil(start: Long, hour: Int, minute: Int, zone: TimeZone = TimeZone.currentSystemDefault()): Int {
    val local = Instant.fromEpochMilliseconds(start).toLocalDateTime(zone)
    val time = LocalTime(hour, minute)
    val endDate = if (time <= local.time) local.date.plus(DatePeriod(days = 1)) else local.date
    val end = plannerTimestamp(endDate, hour, minute, zone)
    val duration = ((end - start) / 60_000).toInt()
    require(duration in 1..1440) { "Интервал должен занимать от 1 минуты до 24 часов" }
    return duration
}
