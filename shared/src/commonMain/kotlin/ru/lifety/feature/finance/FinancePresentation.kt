package ru.lifety.feature.finance

import kotlinx.datetime.*
import ru.lifety.core.model.Expense
import kotlin.time.Instant

/** Calendar conversion without allocating a native date formatter for each transaction. */
internal fun financeCalendarDate(timestamp: Long, zone: TimeZone): FinanceDate {
    val date = Instant.fromEpochMilliseconds(timestamp).toLocalDateTime(zone).date
    return FinanceDate(date.year, date.month.number, date.day)
}
internal fun financeCalendarDayStart(date: FinanceDate, zone: TimeZone): Long =
    LocalDate(date.year, date.month, date.day).atStartOfDayIn(zone).toEpochMilliseconds()

internal data class FinancePresentation(val summary: ExpenseSummary?, val groups: Map<FinanceDate, List<Expense>>,
    val quickTotals: Map<FinancePeriod, Long?>)

/** Run once per data/range change on Default, never in a lazy-list builder. */
internal fun financePresentation(expenses: List<Expense>, currency: String, bounds: Pair<Long, Long>?,
    today: FinanceDate, zone: TimeZone): FinancePresentation {
    val summary = bounds?.let { (start, end) -> runCatching { summarizeExpenses(expenses, currency, start, end) }.getOrNull() }
    // A corrupt/overflowed total must not make individual records inaccessible.
    val visible = summary?.expenses ?: bounds?.let { (start, end) -> selectExpenses(expenses, currency, start, end) }.orEmpty()
    val quick = FinancePeriod.entries.associateWith { period ->
        val range = financePeriodDates(period, today)
        runCatching {
            val from = financeCalendarDayStart(range.from, zone)
            val until = financeCalendarDayStart(range.until, zone)
            var total = 0L
            expenses.forEach { expense ->
                if (expense.currency == currency && expense.timestamp >= from && expense.timestamp < until) {
                    require(expense.amountMinor > 0)
                    if (expense.amountMinor > Long.MAX_VALUE - total) throw ArithmeticException("Expense total overflow")
                    total += expense.amountMinor
                }
            }
            total
        }.getOrNull()
    }
    return FinancePresentation(summary, visible.groupBy { financeCalendarDate(it.timestamp, zone) }, quick)
}
