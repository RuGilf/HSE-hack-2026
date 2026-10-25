package ru.lifety.feature.finance

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSLocale
import platform.Foundation.NSTimeZone
import platform.Foundation.defaultTimeZone
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

internal actual fun financeNow(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

private fun dateFormatter() = NSDateFormatter().apply {
    locale = NSLocale(localeIdentifier = "en_US_POSIX")
    timeZone = NSTimeZone.defaultTimeZone
    dateFormat = "yyyy-MM-dd"
    lenient = false
}

internal actual fun localFinanceDate(timestamp: Long): FinanceDate =
    requireNotNull(FinanceDate.parse(dateFormatter().stringFromDate(NSDate.dateWithTimeIntervalSince1970(timestamp.toDouble() / 1000))))

internal actual fun financeDayStart(date: FinanceDate): Long =
    (requireNotNull(dateFormatter().dateFromString(date.toString())).timeIntervalSince1970 * 1000).toLong()

@Composable
internal actual fun FinanceNotificationAccess() { Text("Автоимпорт уведомлений доступен на Android.") }
