package ru.lifety.feature.finance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import ru.lifety.core.model.Expense

class FinanceMathTest {
    @Test fun parsesMoneyWithoutRoundingOrFloatingPoint() {
        assertEquals(123456L, parseMinorAmount("1\u00a0234,56"))
        assertEquals(10L, parseMinorAmount("0.1"))
        assertEquals(9007199254740993L, parseMinorAmount("90071992547409.93"))
        assertEquals(Long.MAX_VALUE, parseMinorAmount("92233720368547758.07"))
        assertEquals("90071992547409.93", minorAmountInput(9007199254740993L))
    }

    @Test fun rejectsInvalidAmountsInsteadOfTruncating() {
        listOf("", "0", "-1", "+1", "1.234", "1,2.3", "1 2", "1e2", "NaN",
            "92233720368547758.08").forEach { assertNull(parseMinorAmount(it), it) }
    }

    @Test fun keepsCurrencyTotalsSeparateAndIncludesBothPeriodBoundaries() {
        val expenses = listOf(
            expense("a", 100L, "RUB", "food", 10),
            expense("b", 205L, "RUB", "food", 20),
            expense("c", 300L, "USD", "food", 15),
            expense("d", 90L, "RUB", "transport", 21),
        )
        val summary = summarizeExpenses(expenses, "RUB", 10, 21)
        assertEquals(305L, summary.totalMinor)
        assertEquals(mapOf("food" to 305L), summary.byCategory)
        assertEquals(listOf("b", "a"), summary.expenses.map { it.id })
        assertEquals(195L, remainingBudget(500L, summary.totalMinor))
        assertEquals(-5L, remainingBudget(300L, summary.totalMinor))
    }

    @Test fun reportsOverflowInsteadOfWrappingTotals() {
        assertFailsWith<ArithmeticException> {
            summarizeExpenses(listOf(expense("a", Long.MAX_VALUE), expense("b", 1)), "RUB", 0, 100)
        }
    }

    @Test fun rejectsCorruptNegativeExpenses() {
        assertFailsWith<IllegalArgumentException> {
            summarizeExpenses(listOf(expense("a", -1)), "RUB", 0, 100)
        }
    }

    private fun expense(id: String, amount: Long, currency: String = "RUB",
        category: String = "other", timestamp: Long = 10) =
        Expense(id, amount, currency, "Shop", category, timestamp, "manual")
}
