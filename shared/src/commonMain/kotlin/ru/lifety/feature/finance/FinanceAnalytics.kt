package ru.lifety.feature.finance

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun MonthlyBudgetCard(month: String, currency: String, spent: Long?, budget: Long?, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Месячный лимит · $month · $currency", style = MaterialTheme.typography.titleMedium)
            when {
                spent == null -> Text("Не удалось рассчитать месячные расходы.", color = MaterialTheme.colorScheme.error)
                budget == null -> Text("Задайте лимит, чтобы видеть остаток бюджета.")
                else -> {
                    val remaining = remainingBudget(budget, spent)
                    Text("${formatMoney(spent, currency)} из ${formatMoney(budget, currency)}")
                    LinearProgressIndicator(progress = { expenseShare(spent, budget) }, modifier = Modifier.fillMaxWidth(),
                        color = if (remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.outlineVariant)
                    Text((if (remaining < 0) "Превышение: " else "Остаток: ") +
                        formatMoney(if (remaining < 0) -remaining else remaining, currency),
                        color = if (remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            }
            TextButton(onClick = onEdit) { Text(if (budget == null) "Установить лимит" else "Изменить лимит") }
            Text("На текущий календарный месяц. Лимит хранится в сессии экрана.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun CategoryChart(summary: ExpenseSummary, currency: String) {
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Расходы по категориям", style = MaterialTheme.typography.titleMedium)
            summary.byCategory.entries.sortedByDescending { it.value }.forEach { (category, amount) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(categoryTitle(category), Modifier.weight(1f))
                        Text(formatMoney(amount, currency))
                    }
                    LinearProgressIndicator(progress = { expenseShare(amount, summary.totalMinor) },
                        modifier = Modifier.fillMaxWidth().height(8.dp), trackColor = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

/** A saved October limit cannot be reinterpreted as a November limit after process restore. */
internal fun restoreMonthlyBudgets(saved: Map<String, Any?>, currentMonth: String): Map<String, Long> {
    if (saved["month"] != currentMonth) return emptyMap()
    return saved.entries.mapNotNull { (currency, amount) ->
        if (Regex("[A-Z]{3}").matches(currency) && amount is Long && amount > 0) currency to amount else null
    }.toMap()
}
