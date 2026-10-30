package ru.lifety.core.time

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.number
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import ru.lifety.core.model.Task
import kotlin.time.Clock
import kotlin.time.Instant

fun today(zone: TimeZone = TimeZone.currentSystemDefault()): LocalDate = Clock.System.now().toLocalDateTime(zone).date

fun localDate(timestamp: Long, zone: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
    Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(zone).date

fun formatDate(date: LocalDate): String = "${date.day.toString().padStart(2, '0')}.${date.month.number.toString().padStart(2, '0')}.${date.year}"

fun formatDateTime(timestamp: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val value = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(zone)
    return "${formatDate(value.date)} · ${value.hour.toString().padStart(2, '0')}:${value.minute.toString().padStart(2, '0')}"
}

/** DatePicker encodes a civil date at UTC midnight, independently of the local zone. */
fun datePickerMillis(timestamp: Long, zone: TimeZone = TimeZone.currentSystemDefault()): Long =
    localDate(timestamp, zone).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

/** UIKit's date-only picker uses the local calendar, including its Today marker.
 * Noon avoids DST transitions at midnight; decode its result in the same zone. */
internal fun localDatePickerMillis(date: LocalDate, zone: TimeZone = TimeZone.currentSystemDefault()): Long =
    LocalDateTime(date, kotlinx.datetime.LocalTime(12, 0)).toInstant(zone).toEpochMilliseconds()

fun changeDate(timestamp: Long, pickerMillis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): Long {
    val date = localDate(pickerMillis, TimeZone.UTC)
    val time = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(zone)
    return LocalDateTime(date.year, date.month.number, date.day, time.hour, time.minute).toInstant(zone).toEpochMilliseconds()
}

fun changeTime(timestamp: Long, hour: Int, minute: Int, zone: TimeZone = TimeZone.currentSystemDefault()): Long {
    val date = localDate(timestamp, zone)
    return LocalDateTime(date.year, date.month.number, date.day, hour, minute).toInstant(zone).toEpochMilliseconds()
}

/** Reject non-existent local times around a DST spring-forward instead of silently shifting. */
fun isLocalTimePreserved(timestamp: Long, hour: Int, minute: Int, zone: TimeZone): Boolean {
    val actual = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(zone)
    return actual.hour == hour && actual.minute == minute
}

fun tasksOnDate(tasks: List<Task>, date: LocalDate, zone: TimeZone = TimeZone.currentSystemDefault()): List<Task> =
    tasks.filter { task ->
        task.scheduledAt?.let { localDate(it, zone) == date } == true ||
            task.deadlineAt?.let { localDate(it, zone) == date } == true
    }.sortedWith(compareBy<Task> { task ->
        listOfNotNull(task.scheduledAt, task.deadlineAt).filter { localDate(it, zone) == date }.minOrNull()
    }.thenBy { it.id })

fun monthGrid(month: LocalDate): List<LocalDate> {
    val first = LocalDate(month.year, month.month.number, 1)
    val start = first.plus(DatePeriod(days = -(first.dayOfWeek.ordinal)))
    return List(42) { start.plus(DatePeriod(days = it)) }
}

val russianMonths = listOf("Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь")
