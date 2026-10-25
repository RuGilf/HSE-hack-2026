package ru.lifety.feature.finance

/** Calendar dates are kept separate from UTC instants, including on DST transition days. */
data class FinanceDate(val year: Int, val month: Int, val day: Int) : Comparable<FinanceDate> {
    override fun toString() = "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
    override fun compareTo(other: FinanceDate): Int =
        compareValuesBy(this, other, { it.year }, { it.month }, { it.day })

    fun nextDay(): FinanceDate = when {
        day < daysInMonth(year, month) -> copy(day = day + 1)
        month < 12 -> FinanceDate(year, month + 1, 1)
        else -> FinanceDate(year + 1, 1, 1)
    }

    fun previousDay(): FinanceDate = when {
        day > 1 -> copy(day = day - 1)
        month > 1 -> FinanceDate(year, month - 1, daysInMonth(year, month - 1))
        else -> FinanceDate(year - 1, 12, 31)
    }

    companion object {
        fun parse(text: String): FinanceDate? {
            if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(text)) return null
            val year = text.substring(0, 4).toInt()
            val month = text.substring(5, 7).toInt()
            val day = text.substring(8, 10).toInt()
            if (year !in 1900..9999 || month !in 1..12 || day !in 1..daysInMonth(year, month)) return null
            return FinanceDate(year, month, day)
        }
    }
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}
