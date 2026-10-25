@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package ru.lifety.feature.dashboard

import platform.Foundation.*

internal actual fun dashboardPeriod(now: Long): DashboardPeriod {
    val calendar = NSCalendar.currentCalendar
    val date = NSDate(timeIntervalSince1970 = now / 1000.0)
    val start = calendar.startOfDayForDate(date)
    val end = requireNotNull(calendar.dateByAddingUnit(NSCalendarUnitDay, 1, start, 0u))
    val components = calendar.components(NSCalendarUnitYear or NSCalendarUnitMonth, date)
    components.day = 1
    val month = requireNotNull(calendar.dateFromComponents(components))
    val monthEnd = requireNotNull(calendar.dateByAddingUnit(NSCalendarUnitMonth, 1, month, 0u))
    return DashboardPeriod(start.millis(), end.millis(), month.millis(), monthEnd.millis(), NSDateFormatter().apply { dateFormat = "EEEE, d MMMM" }.stringFromDate(date))
}

private fun NSDate.millis(): Long = (timeIntervalSince1970 * 1000).toLong()

internal actual fun dashboardDate(timestamp: Long): String = NSDateFormatter().apply { dateFormat = "d MMM yyyy, HH:mm" }
    .stringFromDate(NSDate(timeIntervalSince1970 = timestamp / 1000.0))
