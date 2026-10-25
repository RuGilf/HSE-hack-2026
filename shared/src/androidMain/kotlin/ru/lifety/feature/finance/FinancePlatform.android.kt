package ru.lifety.feature.finance

import androidx.compose.runtime.Composable
import java.util.Calendar
import java.util.GregorianCalendar
import ru.lifety.platform.notifications.AndroidNotificationAccess

internal actual fun financeNow(): Long = System.currentTimeMillis()

internal actual fun localFinanceDate(timestamp: Long): FinanceDate =
    GregorianCalendar().apply { timeInMillis = timestamp }.let {
        FinanceDate(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
    }

internal actual fun financeDayStart(date: FinanceDate): Long = GregorianCalendar().apply {
    clear()
    set(date.year, date.month - 1, date.day, 0, 0, 0)
}.timeInMillis

@Composable
internal actual fun FinanceNotificationAccess() { AndroidNotificationAccess() }
