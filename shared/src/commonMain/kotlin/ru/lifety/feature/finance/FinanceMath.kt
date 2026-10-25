package ru.lifety.feature.finance

import ru.lifety.core.model.Expense

private val moneyPattern = Regex("([0-9]+|[0-9]{1,3}(?:[ \\u00a0\\u202f][0-9]{3})+)(?:[.,]([0-9]{1,2}))?")

/** Strict decimal input; rejects rounding, negative/zero amounts and Long overflow. */
fun parseMinorAmount(input: String): Long? {
    val match = moneyPattern.matchEntire(input.trim()) ?: return null
    val whole = match.groupValues[1].filter { it in '0'..'9' }.toLongOrNull() ?: return null
    val fraction = match.groupValues[2].padEnd(2, '0').toLong()
    if (whole > (Long.MAX_VALUE - fraction) / 100) return null
    return (whole * 100 + fraction).takeIf { it > 0 }
}

fun minorAmountInput(amount: Long): String {
    val digits = amount.toString().removePrefix("-").padStart(3, '0')
    return (if (amount < 0) "-" else "") + digits.dropLast(2) + "." + digits.takeLast(2)
}

fun formatMoney(amount: Long, currency: String): String {
    val input = minorAmountInput(amount)
    val symbol = when (currency) { "RUB" -> "₽"; "USD" -> "$"; "EUR" -> "€"; else -> currency }
    return "${input.replace('.', ',')} $symbol"
}

data class ExpenseSummary(
    val expenses: List<Expense>,
    val totalMinor: Long,
    val byCategory: Map<String, Long>,
)

/** Half-open UTC timestamps, derived by the UI from inclusive local calendar dates. */
fun summarizeExpenses(expenses: List<Expense>, currency: String, from: Long, until: Long): ExpenseSummary {
    require(from <= until)
    val selected = expenses.filter { it.currency == currency && it.timestamp >= from && it.timestamp < until }
        .sortedWith(compareByDescending<Expense> { it.timestamp }.thenBy { it.id })
    var total = 0L
    val categories = mutableMapOf<String, Long>()
    selected.forEach {
        require(it.amountMinor > 0) { "Expense amount must be positive" }
        if (it.amountMinor > Long.MAX_VALUE - total) throw ArithmeticException("Expense total overflow")
        total += it.amountMinor
        categories[it.category] = (categories[it.category] ?: 0L) + it.amountMinor
    }
    return ExpenseSummary(selected, total, categories)
}

fun remainingBudget(budget: Long, spent: Long): Long {
    require(budget >= 0 && spent >= 0)
    return budget - spent
}

val financeCategories = linkedMapOf(
    "food" to "Продукты и кафе", "transport" to "Транспорт", "shopping" to "Покупки",
    "bills" to "Дом и счета", "health" to "Здоровье", "leisure" to "Досуг",
    "education" to "Учёба", "other" to "Другое",
)

fun categoryTitle(category: String): String = financeCategories[category] ?: category
