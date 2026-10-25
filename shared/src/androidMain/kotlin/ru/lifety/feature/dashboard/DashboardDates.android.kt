package ru.lifety.feature.dashboard

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal actual fun dashboardPeriod(now: Long): DashboardPeriod {
    val day = Calendar.getInstance().apply {
        timeInMillis = now
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val start = day.timeInMillis
    val end = (day.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
    val month = (day.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1) }
    val monthStart = month.timeInMillis
    month.add(Calendar.MONTH, 1)
    return DashboardPeriod(start, end, monthStart, month.timeInMillis, SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now)))
}

internal actual fun dashboardDate(timestamp: Long): String = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(timestamp))
