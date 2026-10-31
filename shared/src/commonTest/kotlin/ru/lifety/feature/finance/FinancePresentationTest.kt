package ru.lifety.feature.finance

import kotlinx.datetime.*
import ru.lifety.core.model.Expense
import kotlin.test.*
import kotlin.time.Instant

class FinancePresentationTest {
    @Test fun datesUseLocalDaysIncludingDstAndExtremeOffsets() {
        val instant = Instant.parse("2026-10-31T00:30:00Z").toEpochMilliseconds()
        assertEquals(FinanceDate(2026, 10, 30), financeCalendarDate(instant, TimeZone.of("America/Los_Angeles")))
        assertEquals(FinanceDate(2026, 10, 31), financeCalendarDate(instant, TimeZone.of("Pacific/Kiritimati")))
        val zone = TimeZone.of("Europe/Berlin")
        assertEquals(23 * 60 * 60_000L, financeCalendarDayStart(FinanceDate(2026, 3, 30), zone) - financeCalendarDayStart(FinanceDate(2026, 3, 29), zone))
        assertEquals(25 * 60 * 60_000L, financeCalendarDayStart(FinanceDate(2026, 10, 26), zone) - financeCalendarDayStart(FinanceDate(2026, 10, 25), zone))
    }

    @Test fun groupedRecordsAndQuickTotalsMatchExistingCalculations() {
        val today = FinanceDate(2026, 10, 31)
        val zone = TimeZone.UTC
        val start = financeCalendarDayStart(today, zone)
        val expenses = listOf(
            Expense("a", 100, "RUB", "a", "food", start + 10, "manual"),
            Expense("b", 250, "RUB", "b", "food", start - 100, "manual"),
            Expense("c", 999, "USD", "c", "other", start, "manual"),
            Expense("d", 500, "RUB", "d", "other", start + 86_400_000, "manual"))
        val result = financePresentation(expenses, "RUB", start - 86_400_000 to start + 86_400_000, today, zone)
        assertEquals(listOf("a", "b"), result.groups.values.flatten().map { it.id })
        assertEquals(350L, result.summary!!.totalMinor)
        FinancePeriod.entries.forEach { period ->
            val range = financePeriodDates(period, today)
            assertEquals(summarizeExpenses(expenses, "RUB", financeCalendarDayStart(range.from, zone), financeCalendarDayStart(range.until, zone)).totalMinor, result.quickTotals[period])
        }
    }

    @Test fun overflowKeepsIndividualTransactionsAccessible() {
        val date = FinanceDate(2026, 10, 31)
        val start = financeCalendarDayStart(date, TimeZone.UTC)
        val rows = listOf(Expense("a", Long.MAX_VALUE, "RUB", "a", "food", start, "manual"), Expense("b", 1, "RUB", "b", "food", start, "manual"))
        val result = financePresentation(rows, "RUB", Long.MIN_VALUE to Long.MAX_VALUE, date, TimeZone.UTC)
        assertNull(result.summary)
        assertEquals(2, result.groups.values.flatten().size)
        assertNull(result.quickTotals[FinancePeriod.DAY])
    }
}
