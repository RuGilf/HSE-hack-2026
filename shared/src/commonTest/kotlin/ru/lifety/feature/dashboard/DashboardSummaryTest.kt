package ru.lifety.feature.dashboard

import kotlin.test.*
import ru.lifety.core.model.Expense
import ru.lifety.core.model.Task

class DashboardSummaryTest {
    private val period = DashboardPeriod(100, 200, 50, 300, "today")
    @Test fun dayBoundariesCompletionAndDeadlines() {
        val tasks = listOf(
            Task("a", "Scheduled", 100, null, false),
            Task("b", "Due", null, 199, true),
            Task("c", "Tomorrow", 200, 220, false),
            Task("d", "Old", null, 99, false),
            Task("e", "Unscheduled", null, null, false),
            Task("f", "Both", 120, 180, false),
        )
        val result = summarizeDashboard(tasks, emptyList(), null, 150, period)
        assertEquals(listOf("a", "f", "b"), result.today.map { it.id })
        assertEquals(1, result.completed)
        assertEquals(2, result.remaining)
        assertEquals(1, result.overdue)
        assertEquals(listOf("f", "c"), result.deadlines.map { it.id })
    }

    @Test fun monthTotalsKeepCurrenciesSeparateAndExcludeFuture() {
        fun expense(id: String, amount: Long, currency: String, time: Long) = Expense(id, amount, currency, "private", "other", time, "manual")
        val data = listOf(expense("1", 9_007_199_254_740_993, "RUB", 50), expense("2", -2, "RUB", 140),
            expense("3", 300, "USD", 150), expense("4", 900, "RUB", 49), expense("5", 999, "RUB", 151))
        val result = summarizeDashboard(emptyList(), data, null, 150, period)
        assertEquals(3, result.expenseCount)
        assertEquals(listOf(CurrencySpending("RUB", 9_007_199_254_740_991), CurrencySpending("USD", 300)), result.spending)
    }

    @Test fun emptyDoesNotInventHealthOrMoney() {
        val result = summarizeDashboard(emptyList(), emptyList(), null, 150, period)
        assertEquals(0, result.remaining)
        assertTrue(result.spending.isEmpty())
        assertNull(result.health)
    }

    @Test fun overflowIsUnknownRatherThanNegative() {
        val expenses = listOf(Expense("a", Long.MAX_VALUE, "RUB", "", "", 100, "manual"), Expense("b", 1, "RUB", "", "", 100, "manual"))
        assertNull(summarizeDashboard(emptyList(), expenses, null, 150, period).spending.single().amountMinor)
    }

    @Test fun exactMoneyFormatting() {
        assertEquals("90071992547409,93 RUB", formatMinor(9_007_199_254_740_993, "RUB"))
        assertEquals("−92233720368547758,08 RUB", formatMinor(Long.MIN_VALUE, "RUB"))
        assertEquals("12 JPY", formatMinor(12, "JPY"))
        assertEquals("0,012 KWD", formatMinor(12, "KWD"))
    }
}
