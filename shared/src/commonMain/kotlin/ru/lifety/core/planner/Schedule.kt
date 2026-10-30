package ru.lifety.core.planner

import kotlinx.datetime.*
import ru.lifety.core.model.ScheduleBlock

data class ScheduleOccurrence(val block: ScheduleBlock, val start: Long, val end: Long)

fun scheduleError(block: ScheduleBlock): String? {
    if (block.id.isBlank() || block.title.isBlank() || block.title.length > 200) return "Укажите название до 200 символов"
    if (block.startMinute !in 0..1439 || block.endMinute !in 0..1439) return "Проверьте время"
    if (block.date == null) {
        if (block.weekdays.isEmpty() || block.weekdays.any { it !in 1..7 }) return "Выберите дни недели"
    } else {
        if (block.weekdays.isNotEmpty()) return "Выберите дату или дни недели"
        try { LocalDate.parse(block.date) } catch (_: IllegalArgumentException) { return "Проверьте дату" }
    }
    return null
}

/** Expand only the selected day and preceding day, including overnight events. */
fun scheduleOnDate(blocks: List<ScheduleBlock>, date: LocalDate,
    zone: TimeZone = TimeZone.currentSystemDefault()): List<ScheduleOccurrence> {
    val dayStart = date.atStartOfDayIn(zone).toEpochMilliseconds()
    val dayEnd = date.plus(DatePeriod(days = 1)).atStartOfDayIn(zone).toEpochMilliseconds()
    if (dayEnd <= dayStart) return emptyList()
    return blocks.filter { scheduleError(it) == null }.flatMap { block ->
        listOf(date.plus(DatePeriod(days = -1)), date).mapNotNull { startDate ->
            val occurs = if (block.date != null) block.date == startDate.toString() else startDate.dayOfWeek.isoDayNumber in block.weekdays
            if (!occurs) return@mapNotNull null
            val endDate = if (block.endMinute <= block.startMinute) startDate.plus(DatePeriod(days = 1)) else startDate
            val start = startDate.atTime(block.startMinute / 60, block.startMinute % 60).toInstant(zone).toEpochMilliseconds()
            val end = endDate.atTime(block.endMinute / 60, block.endMinute % 60).toInstant(zone).toEpochMilliseconds()
            // A nonexistent civil interval during a DST gap must not become negative.
            if (end <= start || start >= dayEnd || end <= dayStart) null else ScheduleOccurrence(block, start, end)
        }
    }.sortedWith(compareBy<ScheduleOccurrence> { it.start }.thenBy { it.block.id })
}
