package ru.lifety.feature.health

import kotlinx.datetime.*
import ru.lifety.core.model.HealthSnapshot
import ru.lifety.core.time.localDate

enum class HealthPeriod(val label: String, val days: Int?) { WEEK("7 дней", 7), MONTH("30 дней", 30), ALL("Всё время", null) }

data class HealthDay(val date: LocalDate, val sleepMinutes: Int?, val steps: Int?, val records: Int)
data class HealthTimeline(val days: List<HealthDay>, val calendarDays: Int, val records: Int) {
    val sleepDays get() = days.count { it.sleepMinutes != null }
    val stepDays get() = days.count { it.steps != null }
    val averageSleep get() = days.mapNotNull { it.sleepMinutes }.takeIf { it.isNotEmpty() }?.average()?.toInt()
    val totalSteps get() = days.mapNotNull { it.steps }.takeIf { it.isNotEmpty() }?.sumOf { it.toLong() }
    val averageSteps get() = if (stepDays == 0) null else totalSteps!! / stepDays
}

/** Snapshots are cumulative measurements, never increments. Use latest known value per metric/day. */
fun healthTimeline(history: List<HealthSnapshot>, period: HealthPeriod, today: LocalDate,
    zone: TimeZone = TimeZone.currentSystemDefault()): HealthTimeline {
    val grouped = history.groupBy { localDate(it.timestamp, zone) }
    val start = period.days?.let { today.plus(DatePeriod(days = 1 - it)) }
    val dates = if (start != null) List(period.days) { start.plus(DatePeriod(days = it)) }
        else grouped.keys.sorted()
    val days = dates.map { date ->
        val rows = grouped[date].orEmpty().sortedByDescending { it.timestamp }
        HealthDay(date, rows.firstNotNullOfOrNull { it.sleepMinutes }, rows.firstNotNullOfOrNull { it.steps }, rows.size)
    }
    val calendarDays = if (period.days != null) period.days else
        if (dates.isEmpty()) 0 else dates.first().daysUntil(dates.last()) + 1
    return HealthTimeline(days, calendarDays, days.sumOf { it.records })
}

internal fun sleepLabel(minutes: Int?): String = minutes?.let { "${it / 60} ч ${it % 60} мин" } ?: "Нет данных"
