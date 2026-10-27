package ru.lifety.feature.finance

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.lifety.core.model.Expense
import ru.lifety.core.ui.isIos
import ru.lifety.core.ui.LifetyMotion
import ru.lifety.core.ui.LocalRepositories
import ru.lifety.core.ui.LocalBottomNavigationInset
import ru.lifety.core.ui.LifetyDateField
import ru.lifety.core.ui.LifetyEmptyState
import ru.lifety.core.ui.LifetyEmotion
import ru.lifety.core.ui.LifetyIcon
import ru.lifety.core.ui.LifetySymbol
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ru.lifety.feature.finance.notifications.*

@Composable
fun FinanceScreen(modifier: Modifier = Modifier) {
    val repository = LocalRepositories.current?.expenses
    val scope = rememberCoroutineScope()
    val controller = remember(repository, scope) { repository?.let { FinanceController(it, scope) } }
    val state = controller?.state?.collectAsState()?.value ?: FinanceUiState(loading = false)
    val categoryRepository = LocalRepositories.current?.expenseCategories
    val categoryController = remember(categoryRepository, scope) { categoryRepository?.let { FinanceCategoryController(it, scope) } }
    val categoryState = categoryController?.state?.collectAsState()?.value ?: CategoryUiState(loading = false)
    val categories = remember(categoryState.categories, state.expenses) {
        resolveExpenseCategories(categoryState.categories, state.expenses.map { it.category })
    }
    var showCategories by rememberSaveable { mutableStateOf(false) }
    CompositionLocalProvider(LocalExpenseCategories provides categories) {
        FinanceContent(
            state = state,
            storageConnected = repository != null,
            onSave = { controller?.save(it) ?: false },
            onDelete = { controller?.delete(it) ?: false },
            onRetry = { controller?.retry() },
            onConfirm = { controller?.confirmNotification(it) ?: false },
            modifier = modifier,
            onManageCategories = if (categoryController != null) ({ showCategories = true }) else null,
        )
        if (showCategories && categoryController != null) CategoryManagerSheet(categoryState.copy(categories = categories),
            onSave = categoryController::save, onRetry = categoryController::retry, onDismiss = { showCategories = false })
    }
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
    onConfirm: suspend (Expense) -> Boolean = onSave,
    onManageCategories: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val bottomInset = LocalBottomNavigationInset.current
    val today = localFinanceDate(financeNow())
    var currency by rememberSaveable { mutableStateOf("RUB") }
    var fromText by rememberSaveable { mutableStateOf(today.copy(day = 1).toString()) }
    var toText by rememberSaveable { mutableStateOf(today.firstDayOfNextMonth().previousDay().toString()) }
    var allTime by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable(stateSaver = listSaver<Expense?, Any>(
        save = { expense -> expense?.let { listOf(it.id, it.amountMinor, it.currency, it.merchant, it.category, it.timestamp, it.source) }.orEmpty() },
        restore = { values -> if (values.isEmpty()) null else Expense(values[0] as String, values[1] as Long, values[2] as String, values[3] as String, values[4] as String, values[5] as Long, values[6] as String) },
    )) { mutableStateOf<Expense?>(null) }
    var suggestionId by rememberSaveable { mutableStateOf<String?>(null) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Expense?>(null) }
    var showBudget by remember { mutableStateOf(false) }
    var showDates by rememberSaveable { mutableStateOf(false) }
    var showImport by rememberSaveable { mutableStateOf(false) }
    // shortcut: session-only configuration; durable budgets need a Core preferences contract.
    val budgetMonth = today.toString().take(7)
    var budgets by rememberSaveable(budgetMonth, stateSaver = mapSaver<Map<String, Long>>(
        save = { mapOf<String, Any?>("month" to budgetMonth) + it },
        restore = { saved -> restoreMonthlyBudgets(saved, budgetMonth) },
    )) { mutableStateOf(emptyMap<String, Long>()) }
    val pending by FinanceNotifications.inbox.pending.collectAsState()
    val importResult by FinanceNotifications.intake.lastResult.collectAsState()
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

    Scaffold(modifier = modifier.fillMaxSize(), contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (storageConnected) FloatingActionButton(onClick = {
                if (!state.loading && !state.saving) { editing = null; suggestionId = null; showEditor = true }
            }, containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.padding(bottom = bottomInset).semantics { contentDescription = "Добавить расход" }) {
                LifetyIcon(LifetySymbol.Plus)
            }
        }) { padding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!storageConnected) item {
            Notice("Локальное хранилище пока недоступно. Сохранение расходов станет доступно после подключения.")
        }
        if (state.error != null) item {
            Notice(state.error)
            TextButton(onClick = onRetry, enabled = !state.saving) { Text("Повторить загрузку") }
        }
        if (state.loading || state.saving) item { LinearProgressIndicator(Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.outlineVariant) }
        if (onManageCategories != null) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onManageCategories, enabled = !state.saving) {
                    LifetyIcon(LifetySymbol.Settings, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Категории")
                }
            }
        }
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
            TextButton(onClick = { showDates = !showDates }) {
                LifetyIcon(LifetySymbol.Calendar, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (allTime) "Весь период" else "$fromText — $toText")
            }
            AnimatedVisibility(showDates, enter = LifetyMotion.RevealEnter, exit = LifetyMotion.RevealExit) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LifetyDateField("С", fromText, { fromText = it; allTime = false }, modifier = Modifier.weight(1f))
                    LifetyDateField("По", toText, { toText = it; allTime = false }, modifier = Modifier.weight(1f))
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (listOf("RUB", "USD", "EUR") + state.expenses.map { it.currency }).distinct().forEach { code ->
                    FilterChip(selected = currency == code, onClick = { currency = code }, label = { Text(code) })
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

                    }
                }
            }
            if (summary.byCategory.isNotEmpty()) item { CategoryChart(summary, currency) }
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
        item {
            if (storageConnected && !state.loading) {
                MonthlyBudgetCard(budgetMonth, currency, quickTotals[FinancePeriod.MONTH], budgets[currency],
                    onEdit = { showBudget = true })
            }
        }
        if (!isIos) item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                ListItem(
                    modifier = Modifier.clickable { showImport = !showImport },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    headlineContent = { Text("Импорт уведомлений") },
                    supportingContent = { Text(if (suggestions.isEmpty()) "Банки и доступ к уведомлениям" else "Ожидают подтверждения: ${suggestions.size}") },
                    trailingContent = { Text(if (showImport) "Скрыть" else "Настроить", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) },
                )
                AnimatedVisibility(showImport, enter = LifetyMotion.RevealEnter, exit = LifetyMotion.RevealExit) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FinanceNotificationAccess()
                        OutlinedButton(onClick = {
                            FinanceNotifications.intake.demo(financeNow(), "demo-${financeNow()}-${kotlin.random.Random.nextLong()}")
                        }) { Text("ДЕМО: тестовое уведомление") }
                        Text("Тестовая запись будет отмечена ДЕМО. Расходы сохраняются после подтверждения.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (importResult == ImportOutcome.UNREADABLE || importResult == ImportOutcome.UNRECOGNIZED) {
                Text(if (importResult == ImportOutcome.UNREADABLE)
                    "Содержимое уведомления скрыто. Добавьте расход вручную."
                    else "Не удалось распознать уведомление. Добавьте расход вручную.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { FinanceNotifications.intake.clearResult() }) { Text("Понятно") }
            }
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
        if (visible.isEmpty() && !state.loading && periodValid && storageConnected) item {
            LifetyEmptyState("Пока без расходов", "Добавь первый расход, чтобы видеть свой ритм трат.",
                emotion = LifetyEmotion.Calm, mascotSize = 80.dp)
        }
        visible.groupBy { localFinanceDate(it.timestamp) }.forEach { (date, expenses) ->
            item(key = "date:$date") { Text(date.toString(), style = MaterialTheme.typography.titleSmall) }
            items(expenses, key = { "expense:${it.id}" }, contentType = { "expense" }) { expense ->
                Box(Modifier.animateItem(placementSpec = tween(LifetyMotion.Standard), fadeInSpec = tween(LifetyMotion.Quick), fadeOutSpec = tween(LifetyMotion.Quick))) {
                ExpenseRow(expense, enabled = storageConnected && !state.saving, onEdit = {
                    editing = expense; suggestionId = null; showEditor = true
                }, onDelete = { deleting = expense })
                }
            }
        }
    }

    }

    if (showEditor) ExpenseEditorDialog(initial = editing, busy = state.saving, error = state.error,
        onDismiss = { showEditor = false }, onSave = { expense ->
            val saved = if (suggestionId != null) onConfirm(expense) else onSave(expense)
            if (saved) {
                suggestionId?.let { FinanceNotifications.inbox.dismiss(it) }
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
    var menu by remember { mutableStateOf(false) }
    ListItem(
        modifier = Modifier.clickable(enabled = enabled, onClick = onEdit),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        headlineContent = { Text(expense.merchant, maxLines = 2) },
        leadingContent = { CategoryMarker(expense.category) },
        supportingContent = {
            Text(categoryDisplayName(expense.category) + when {
                expense.source == "demo-notification" -> " · ДЕМО"
                expense.source.startsWith("notification:") -> " · Из уведомления"
                else -> ""
            })
        },
        trailingContent = {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(formatMoney(expense.amountMinor, expense.currency), style = MaterialTheme.typography.titleSmall)
                Box {
                    IconButton(onClick = { menu = true }, enabled = enabled,
                        modifier = Modifier.semantics { contentDescription = "Действия с расходом ${expense.merchant}" }) { LifetyIcon(LifetySymbol.More) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Изменить") }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Удалить", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        },
    )
}
