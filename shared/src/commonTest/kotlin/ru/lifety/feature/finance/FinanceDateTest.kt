package ru.lifety.feature.finance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FinanceDateTest {
    @Test fun validatesLeapDatesAndMovesAcrossYears() {
        assertNull(FinanceDate.parse("2025-02-29"))
        assertNull(FinanceDate.parse("2026-13-01"))
        assertNull(FinanceDate.parse("2026-04-31"))
        assertEquals("2024-03-01", FinanceDate.parse("2024-02-29")!!.nextDay().toString())
        assertEquals("2025-12-31", FinanceDate.parse("2026-01-01")!!.previousDay().toString())
        assertNull(FinanceDate.parse("2026-1-1"))
    }
}
