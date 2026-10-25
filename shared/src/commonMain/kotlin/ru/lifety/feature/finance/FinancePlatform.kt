package ru.lifety.feature.finance

import androidx.compose.runtime.Composable

internal expect fun financeNow(): Long
internal expect fun localFinanceDate(timestamp: Long): FinanceDate
internal expect fun financeDayStart(date: FinanceDate): Long

@Composable
internal expect fun FinanceNotificationAccess()
