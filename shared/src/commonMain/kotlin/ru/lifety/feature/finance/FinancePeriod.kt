package ru.lifety.feature.finance

enum class FinancePeriod(val title: String) {
    DAY("День"), WEEK("Неделя"), MONTH("Месяц"),
}

data class FinanceDateRange(val from: FinanceDate, val until: FinanceDate)

/** Week means the last seven local dates; month includes the entire calendar month. */
fun financePeriodDates(period: FinancePeriod, today: FinanceDate): FinanceDateRange = when (period) {
    FinancePeriod.DAY -> FinanceDateRange(today, today.nextDay())
    FinancePeriod.WEEK -> {
        var start = today
        repeat(6) { start = start.previousDay() }
        FinanceDateRange(start, today.nextDay())
    }
    FinancePeriod.MONTH -> FinanceDateRange(today.copy(day = 1), today.firstDayOfNextMonth())
}

fun FinanceDate.firstDayOfNextMonth(): FinanceDate =
    if (month == 12) FinanceDate(year + 1, 1, 1) else FinanceDate(year, month + 1, 1)
