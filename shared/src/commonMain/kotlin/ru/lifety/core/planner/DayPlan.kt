package ru.lifety.core.planner

import kotlinx.datetime.*
import ru.lifety.core.model.Task
import ru.lifety.core.time.localDate
import ru.lifety.core.time.tasksOnDate
import kotlin.time.Instant
import kotlin.time.Clock

data class DayPlan(
    val overdue: List<Task>,
    val due: List<Task>,
    val scheduled: List<Task>,
    val undated: List<Task>,
    val completed: List<Task>,
) {
    val activeCount get() = overdue.size + due.size + scheduled.size
}

/** A task appears once per day. Past outstanding deadlines carry into today's plan only. */
fun dayPlan(tasks: List<Task>, date: LocalDate, now: Long,
    zone: TimeZone = TimeZone.currentSystemDefault()): DayPlan {
    val active = tasks.filterNot { it.isCompleted }
    val today = localDate(now, zone)
    val overdue = if (date == today) active.filter { it.deadlineAt?.let { deadline -> deadline < now } == true }
        .sortedWith(compareBy<Task> { it.deadlineAt }.thenBy { it.id }) else emptyList()
    val overdueIds = overdue.mapTo(mutableSetOf()) { it.id }
    val due = active.filter { it.id !in overdueIds && it.deadlineAt?.let { time -> localDate(time, zone) == date } == true }
        .sortedWith(compareBy<Task> { it.deadlineAt }.thenBy { it.id })
    val dueIds = due.mapTo(mutableSetOf()) { it.id }
    val scheduled = active.filter { it.id !in overdueIds && it.id !in dueIds &&
        it.scheduledAt?.let { time -> localDate(time, zone) == date } == true }
        .sortedWith(compareBy<Task> { it.scheduledAt }.thenBy { it.id })
    val undated = if (date == today) active.filter { it.scheduledAt == null && it.deadlineAt == null }.sortedBy { it.id }
        else emptyList()
    return DayPlan(overdue, due, scheduled, undated, tasksOnDate(tasks, date, zone).filter { it.isCompleted })
}

/** Civil time must round-trip, including the date (some time zones have skipped whole days). */
fun plannerTimestamp(date: LocalDate, hour: Int, minute: Int,
    zone: TimeZone = TimeZone.currentSystemDefault()): Long {
    val civil = LocalDateTime(date, LocalTime(hour, minute))
    val instant = civil.toInstant(zone)
    require(instant.toLocalDateTime(zone) == civil) { "Такого времени нет в этом часовом поясе. Выберите другое время." }
    return instant.toEpochMilliseconds()
}

// An overdue task may be rescheduled without erasing its original missed deadline.
fun taskTimingError(task: Task, now: Long = Clock.System.now().toEpochMilliseconds()): String? =
    if (!task.isCompleted && task.scheduledAt != null && task.deadlineAt != null && task.deadlineAt >= now && task.scheduledAt > task.deadlineAt)
        "Время выполнения позже дедлайна. Перенесите выполнение или измените срок." else null

fun formatPlannerTime(timestamp: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val time = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(zone)
    return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
}
