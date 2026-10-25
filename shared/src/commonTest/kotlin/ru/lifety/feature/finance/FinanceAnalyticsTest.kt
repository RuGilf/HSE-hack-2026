package ru.lifety.feature.finance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FinanceAnalyticsTest {
    @Test fun visualFractionsAreClampedWithoutIntegerOverflow() {
        assertEquals(0f, expenseShare(0, 0))
        assertEquals(0.25f, expenseShare(25, 100))
        assertEquals(1f, expenseShare(150, 100))
        assertEquals(0.5f, expenseShare(4611686018427387903L, Long.MAX_VALUE), 0.00001f)
        assertFailsWith<IllegalArgumentException> { expenseShare(-1, 100) }
    }

    @Test fun restoredMonthlyLimitNeverLeaksIntoAnotherMonth() {
        val saved = mapOf<String, Any?>("month" to "2026-10", "RUB" to 10000L, "USD" to 5000L)
        assertEquals(mapOf("RUB" to 10000L, "USD" to 5000L), restoreMonthlyBudgets(saved, "2026-10"))
        assertTrue(restoreMonthlyBudgets(saved, "2026-11").isEmpty())
        assertTrue(restoreMonthlyBudgets(mapOf("RUB" to 10000L), "2026-11").isEmpty())
        assertEquals(mapOf("RUB" to 10000L), restoreMonthlyBudgets(
            mapOf("month" to "2026-10", "RUB" to 10000L, "EUR" to -1L, "USD" to "bad"), "2026-10"))
    }

    @Test fun categoryHintsHandleTransportBeforeSupermarketNames() {
        assertEquals("transport", inferFinanceCategory("METRO TICKET"))
        assertEquals("food", inferFinanceCategory("METRO CASH AND CARRY"))
        assertEquals("food", inferFinanceCategory("COFFEE POINT"))
        assertEquals("health", inferFinanceCategory("Аптека 24"))
        assertEquals("other", inferFinanceCategory("Unknown shop"))
        assertTrue(financeCategories.containsKey(inferFinanceCategory("Unknown shop")))
    }
}
