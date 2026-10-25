package ru.lifety.feature.finance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import ru.lifety.core.model.Expense

class FinancePeriodTest {
    @Test fun dayWeekAndMonthHaveCalendarBoundaries() {
        val today = FinanceDate(2026, 1, 3)
        assertEquals(FinanceDateRange(FinanceDate(2026, 1, 3), FinanceDate(2026, 1, 4)),
            financePeriodDates(FinancePeriod.DAY, today))
        assertEquals(FinanceDateRange(FinanceDate(2025, 12, 28), FinanceDate(2026, 1, 4)),
            financePeriodDates(FinancePeriod.WEEK, today))
        assertEquals(FinanceDateRange(FinanceDate(2026, 1, 1), FinanceDate(2026, 2, 1)),
            financePeriodDates(FinancePeriod.MONTH, today))
        assertEquals(FinanceDate(2025, 1, 1), FinanceDate(2024, 12, 9).firstDayOfNextMonth())
        assertEquals(FinanceDateRange(FinanceDate(2024, 2, 1), FinanceDate(2024, 3, 1)),
            financePeriodDates(FinancePeriod.MONTH, FinanceDate(2024, 2, 29)))
    }

    @Test fun selectionStillRespectsPeriodWhenTotalsOverflow() {
        val values = listOf(
            Expense("a", Long.MAX_VALUE, "RUB", "Shop", "other", 10, "manual"),
            Expense("b", 1, "RUB", "Shop", "other", 20, "manual"),
            Expense("old", 100, "RUB", "Shop", "other", 1, "manual"),
            Expense("usd", 100, "USD", "Shop", "other", 15, "manual"),
        )
        assertEquals(listOf("b", "a"), selectExpenses(values, "RUB", 10, 21).map { it.id })
        assertTrue(selectExpenses(values, "RUB", 20, 20).isEmpty())
    }
}
