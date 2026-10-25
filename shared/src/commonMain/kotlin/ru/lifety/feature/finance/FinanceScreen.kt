package ru.lifety.feature.finance

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.lifety.core.model.Expense
import ru.lifety.core.ui.LocalRepositories
import ru.lifety.feature.finance.notifications.*

@Composable
fun FinanceScreen(modifier: Modifier = Modifier) {
    val repository = LocalRepositories.current?.expenses
    val scope = rememberCoroutineScope()
    val controller = remember(repository, scope) { repository?.let { FinanceController(it, scope) } }
    val state = controller?.state?.collectAsState()?.value ?: FinanceUiState(loading = false)
    FinanceContent(
        state = state,
        storageConnected = repository != null,
        onSave = { controller?.save(it) ?: false },
        onDelete = { controller?.delete(it) ?: false },
        onRetry = { controller?.retry() },
        modifier = modifier,
    )
}

/** Explicit state and callbacks also allow previews/tests without changing Core DI. */
@Composable
fun FinanceContent(
    state: FinanceUiState,
    onSave: suspend (Expense) -> Boolean,
    onDelete: suspend (String) -> Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    storageConnected: Boolean = true,
) {
    val scope = rememberCoroutineScope()
    val today = localFinanceDate(financeNow())
    var currency by rememberSaveable { mutableStateOf("RUB") }
    var fromText by rememberSaveable { mutableStateOf(today.copy(day = 1).toString()) }
    var toText by rememberSaveable { mutableStateOf(today.firstDayOfNextMonth().previousDay().toString()) }
    var allTime by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Expense?>(null) }
    var suggestionId by remember { mutableStateOf<String?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Expense?>(null) }
    var showBudget by remember { mutableStateOf(false) }
    // shortcut: session-only configuration; durable budgets need a Core preferences contract.
    var budgets by remember { mutableStateOf(emptyMap<String, Long>()) }
    val pending by FinanceNotifications.inbox.pending.collectAsState()
    val savedIds = state.expenses.map { it.id }.toSet()
    val suggestions = pending.filterNot { it.id in savedIds }
    val from = FinanceDate.parse(fromText)
    val to = FinanceDate.parse(toText)
    val periodValid = allTime || (from != null && to != null && from <= to)
    val bounds = remember(from, to, allTime) {
        runCatching {
            require(periodValid)
            (if (allTime) Long.MIN_VALUE else financeDayStart(requireNotNull(from))) to
                (if (allTime) Long.MAX_VALUE else financeDayStart(requireNotNull(to).nextDay()))
        }.getOrNull()
    }
    val summary = remember(state.expenses, currency, bounds) {
        bounds?.let { (start, end) -> runCatching { summarizeExpenses(state.expenses, currency, start, end) }.getOrNull() }
    }
    val quickTotals = remember(state.expenses, currency, today) {
        FinancePeriod.entries.associateWith { period ->
            val range = financePeriodDates(period, today)
            runCatching { summarizeExpenses(state.expenses, currency,
                financeDayStart(range.from), financeDayStart(range.until)).totalMinor }.getOrNull()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Финансы", style = MaterialTheme.typography.headlineMedium)
                Button(enabled = storageConnected && !state.loading && !state.saving,
                    onClick = { editing = null; suggestionId = null; showEditor = true }) { Text("Добавить") }
            }
        }
        if (!storageConnected) item {
            Notice("Локальное хранилище пока недоступно. Сохранение расходов станет доступно после подключения.")
        }
        if (state.error != null) item {
            Notice(state.error)
            TextButton(onClick = onRetry, enabled = !state.saving) { Text("Повторить загрузку") }
        }
        if (state.loading || state.saving) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FinancePeriod.entries.forEach { period ->
                    val range = financePeriodDates(period, today)
                    val end = range.until.previousDay().toString()
                    FilterChip(selected = !allTime && fromText == range.from.toString() && toText == end,
                        onClick = { allTime = false; fromText = range.from.toString(); toText = end },
                        label = { Text(period.title) })
                }
                FilterChip(selected = allTime, onClick = { allTime = true }, label = { Text("Всё время") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = fromText, onValueChange = { fromText = it.take(10); allTime = false },
                    label = { Text("С (ГГГГ-ММ-ДД)") }, singleLine = true,
                    isError = !allTime && from == null, modifier = Modifier.weight(1f))
                OutlinedTextField(value = toText, onValueChange = { toText = it.take(10); allTime = false },
                    label = { Text("По (включительно)") }, singleLine = true,
                    isError = !allTime && to == null, modifier = Modifier.weight(1f))
            }
            Text("Даты в часовом поясе устройства · неделя — последние 7 дней", style = MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("RUB", "USD", "EUR") + state.expenses.map { it.currency }).distinct().forEach { code ->
                    FilterChip(selected = currency == code, onClick = { currency = code }, label = { Text(code) })
                }
            }
        }
        if (storageConnected && !state.loading) item {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FinancePeriod.entries.forEach { period ->
                        Column(Modifier.weight(1f)) {
                            Text(period.title, style = MaterialTheme.typography.labelMedium)
                            val amount = quickTotals[period]
                            Text(amount?.let { formatMoney(it, currency) } ?: "Ошибка суммы",
                                style = MaterialTheme.typography.titleSmall,
                                color = if (amount == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        if (!periodValid) item { Notice("Укажите корректные даты; начало периода должно быть не позже конца.") }
        else if (summary == null) item { Notice("Итог нельзя рассчитать: переполнение суммы или некорректная запись. Исправьте расходы.") }
        if (summary != null) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Расходы за период · $currency", style = MaterialTheme.typography.labelLarge)
                        Text(formatMoney(summary.totalMinor, currency), style = MaterialTheme.typography.headlineLarge)
                        Text("${summary.expenses.size} операций · валюты считаются отдельно")
                        budgets[currency]?.let { budget ->
                            val remaining = remainingBudget(budget, summary.totalMinor)
                            Text("Бюджет: ${formatMoney(budget, currency)}")
                            Text((if (remaining < 0) "Превышение: " else "Остаток: ") +
                                formatMoney(if (remaining < 0) -remaining else remaining, currency),
                                color = if (remaining < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        }
                        TextButton(onClick = { showBudget = true }) { Text("Бюджет на период (в этой сессии)") }
                    }
                }
            }
            if (summary.byCategory.isNotEmpty()) item {
                Text("По категориям", style = MaterialTheme.typography.titleMedium)
                summary.byCategory.entries.sortedByDescending { it.value }.forEach { (category, amount) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(categoryTitle(category), Modifier.weight(1f))
                        Text(formatMoney(amount, currency))
                    }
                }
            }
        }
        item {
            HorizontalDivider()
            Text("Из уведомлений", style = MaterialTheme.typography.titleMedium)
            Text("Только покупки. Перед записью проверьте сумму, магазин и дату.", style = MaterialTheme.typography.bodySmall)
            FinanceNotificationAccess()
            OutlinedButton(onClick = {
                val demo = DemoNotifications.purchase(financeNow(), "demo-${financeNow()}-${kotlin.random.Random.nextLong()}")
                BankNotificationParser().parse(demo)?.let { FinanceNotifications.inbox.offer(it) }
            }) { Text("ДЕМО: тестовое уведомление") }
            Text("Демо не требует разрешений. Тестовая запись будет отмечена [ДЕМО].", style = MaterialTheme.typography.bodySmall)
        }
        items(suggestions, key = { "pending:${it.id}" }) { candidate ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text((if (candidate.isDemo) "[ДЕМО] " else "") + candidate.merchant,
                        style = MaterialTheme.typography.titleSmall)
                    Text("${formatMoney(candidate.amountMinor, candidate.currency)} · ${localFinanceDate(candidate.timestamp)}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(enabled = storageConnected && !state.saving && !state.loading, onClick = {
                            editing = candidate.toExpense(); suggestionId = candidate.id; showEditor = true
                        }) { Text("Проверить и сохранить") }
                        TextButton(onClick = { FinanceNotifications.inbox.dismiss(candidate.id) }) { Text("Отклонить") }
                    }
                }
            }
        }
        item { Text("Транзакции", style = MaterialTheme.typography.titleLarge) }
        // Keep individual entries accessible even if a corrupt total cannot be calculated.
        val visible = summary?.expenses ?: bounds?.let { (start, end) ->
            selectExpenses(state.expenses, currency, start, end)
        }.orEmpty()
        if (visible.isEmpty() && !state.loading && periodValid && storageConnected) item { Text("За этот период расходов нет. Добавьте первый расход.") }
        visible.groupBy { localFinanceDate(it.timestamp) }.forEach { (date, expenses) ->
            item(key = "date:$date") { Text(date.toString(), style = MaterialTheme.typography.titleSmall) }
            items(expenses, key = { "expense:${it.id}" }) { expense ->
                ExpenseRow(expense, enabled = storageConnected && !state.saving, onEdit = {
                    editing = expense; suggestionId = null; showEditor = true
                }, onDelete = { deleting = expense })
            }
        }
    }

    if (showEditor) ExpenseEditorDialog(initial = editing, busy = state.saving, error = state.error,
        onDismiss = { showEditor = false }, onSave = { expense ->
            if (onSave(expense)) {
                suggestionId?.let { FinanceNotifications.inbox.dismiss(it) }
                showEditor = false
                true
            } else false
        })
    deleting?.let { expense ->
        AlertDialog(onDismissRequest = { if (!state.saving) deleting = null },
            title = { Text("Удалить расход?") },
            text = { Column { Text("${expense.merchant}: ${formatMoney(expense.amountMinor, expense.currency)}")
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) } } },
            confirmButton = { TextButton(enabled = !state.saving, onClick = {
                scope.launch { if (onDelete(expense.id)) deleting = null }
            }) { Text("Удалить") } },
            dismissButton = { TextButton(enabled = !state.saving, onClick = { deleting = null }) { Text("Отмена") } })
    }
    if (showBudget) BudgetDialog(currency, budgets[currency], onDismiss = { showBudget = false }, onSave = {
        budgets = if (it == null) budgets - currency else budgets + (currency to it)
        showBudget = false
    })
}

@Composable
private fun Notice(message: String) {
    Text(message, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), color = MaterialTheme.colorScheme.error)
}

@Composable
private fun ExpenseRow(expense: Expense, enabled: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(expense.merchant, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Text(formatMoney(expense.amountMinor, expense.currency), style = MaterialTheme.typography.titleSmall)
            }
            Text(categoryTitle(expense.category), style = MaterialTheme.typography.bodySmall)
            if (expense.source == "demo-notification") Text("ДЕМО · тестовая запись", color = MaterialTheme.colorScheme.tertiary)
            else if (expense.source.startsWith("notification:")) Text("Из уведомления · подтверждено", style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = onEdit, enabled = enabled) { Text("Изменить") }
                TextButton(onClick = onDelete, enabled = enabled) { Text("Удалить") }
            }
        }
    }
}
