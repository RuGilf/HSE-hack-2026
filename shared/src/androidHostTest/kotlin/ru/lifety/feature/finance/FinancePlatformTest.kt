package ru.lifety.feature.finance

import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class FinancePlatformTest {
    @Test fun localPeriodsRespectSpringAndAutumnDstChanges() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            assertEquals(1710046800000L, financeDayStart(FinanceDate(2024, 3, 10)))
            assertEquals(23 * 60 * 60 * 1000L,
                financeDayStart(FinanceDate(2024, 3, 11)) - financeDayStart(FinanceDate(2024, 3, 10)))
            assertEquals(25 * 60 * 60 * 1000L,
                financeDayStart(FinanceDate(2024, 11, 4)) - financeDayStart(FinanceDate(2024, 11, 3)))
            assertEquals(FinanceDate(2024, 3, 9), localFinanceDate(1710046799999L))
            assertEquals(FinanceDate(2024, 3, 10), localFinanceDate(1710129599999L))
        } finally { TimeZone.setDefault(previous) }
    }
}
