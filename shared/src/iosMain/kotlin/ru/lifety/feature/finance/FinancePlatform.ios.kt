package ru.lifety.feature.finance

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.native.concurrent.ThreadLocal
import platform.Foundation.NSTimeZone
import platform.Foundation.defaultTimeZone

// Kotlin/Native reconstructs regional timezone rules in currentSystemDefault().
// Keep a worker-local object, but check the system ID so travel/settings changes remain visible.
@ThreadLocal
private var cachedZone: Pair<String, TimeZone>? = null

private fun financeZone(): TimeZone {
    val id = NSTimeZone.defaultTimeZone.name
    cachedZone?.takeIf { it.first == id }?.let { return it.second }
    return TimeZone.currentSystemDefault().also { cachedZone = id to it }
}

internal actual fun financeNow(): Long = Clock.System.now().toEpochMilliseconds()
internal actual fun localFinanceDate(timestamp: Long): FinanceDate = financeCalendarDate(timestamp, financeZone())
internal actual fun financeDayStart(date: FinanceDate): Long = financeCalendarDayStart(date, financeZone())

@Composable
internal actual fun FinanceNotificationAccess() { Text("Автоимпорт уведомлений доступен на Android.") }
