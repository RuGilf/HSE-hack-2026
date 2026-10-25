package ru.lifety.feature.finance

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.lifety.core.model.Expense
import ru.lifety.core.ui.LifetyDateField
import ru.lifety.core.ui.LifetyIcon
import ru.lifety.core.ui.LifetySymbol
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExpenseEditorDialog(
    initial: Expense?,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: suspend (Expense) -> Boolean,
) {
    val scope = rememberCoroutineScope()
    val id = rememberSaveable { initial?.id ?: "manual:${financeNow()}:${kotlin.random.Random.nextLong()}" }
    var merchant by rememberSaveable { mutableStateOf(initial?.merchant ?: "") }
    var amount by rememberSaveable { mutableStateOf(initial?.let { minorAmountInput(it.amountMinor) } ?: "") }
    var currency by rememberSaveable { mutableStateOf(initial?.currency ?: "RUB") }
    var category by rememberSaveable { mutableStateOf(initial?.category ?: "other") }
    var date by rememberSaveable { mutableStateOf(localFinanceDate(initial?.timestamp ?: financeNow()).toString()) }
    var attempted by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val parsedDate = FinanceDate.parse(date)
    val minor = parseMinorAmount(amount)
    val valid = merchant.isNotBlank() && minor != null && parsedDate != null
    val locked = busy || submitting || closing
    val currentLocked by rememberUpdatedState(locked)
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !currentLocked || closing })
    ModalBottomSheet(onDismissRequest = { if (!locked) onDismiss() }, sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).imePadding().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(when {
                    initial?.source == "demo-notification" -> "Подтвердить ДЕМО-расход"
                    initial?.source?.startsWith("notification:") == true -> "Проверить расход"
                    initial == null -> "Новый расход"
                    else -> "Изменить расход"
                }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { scope.launch { closing = true; sheet.hide(); onDismiss() } }, enabled = !locked, modifier = Modifier.semantics { contentDescription = "Закрыть расход" }) {
                    LifetyIcon(LifetySymbol.Close, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(amount, { amount = it.take(40) }, label = { Text("Сумма") },
                    textStyle = MaterialTheme.typography.headlineLarge,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    isError = attempted && minor == null, enabled = !locked,
                    supportingText = { Text("Больше нуля, до двух знаков после запятой") })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf("RUB", "USD", "EUR") + currency).distinct().forEach { code ->
                        FilterChip(selected = currency == code, enabled = !locked,
                            onClick = { currency = code }, label = { Text(code) })
                    }
                }
                OutlinedTextField(merchant, { merchant = it.take(120) }, label = { Text("Магазин") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    isError = attempted && merchant.isBlank(), enabled = !locked)
                Text("Категория", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val suggestedCategory = inferFinanceCategory(merchant)
                if (category == "other" && suggestedCategory != "other") {
                    SuggestionChip(onClick = { category = suggestedCategory }, enabled = !locked,
                        label = { Text("Предложение: ${categoryTitle(suggestedCategory)}") })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (financeCategories.keys + category).distinct().forEach { key ->
                        FilterChip(selected = category == key, enabled = !locked,
                            onClick = { category = key }, label = { Text(categoryTitle(key)) })
                    }
                }
                LifetyDateField("Дата расхода", date, { date = it }, enabled = !locked)
                if (attempted && !valid) Text("Проверь магазин, сумму и дату.", color = MaterialTheme.colorScheme.error)
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                if (initial?.source == "demo-notification") Text("Тестовая запись будет сохранена с меткой ДЕМО.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(enabled = !locked, modifier = Modifier.fillMaxWidth().height(48.dp), onClick = {
                attempted = true
                if (valid) {
                    submitting = true
                    scope.launch {
                        try {
                            val timestamp = if (initial != null && localFinanceDate(initial.timestamp) == parsedDate)
                                initial.timestamp else financeDayStart(requireNotNull(parsedDate))
                            if (onSave(Expense(id, requireNotNull(minor), currency, merchant.trim(), category,
                                    timestamp, initial?.source ?: "manual"))) {
                                closing = true
                                sheet.hide()
                                onDismiss()
                            }
                        } finally { submitting = false }
                    }
                }
            }) { Text(if (locked) "Сохранение…" else "Сохранить") }
        }
    }
}

@Composable
internal fun BudgetDialog(currency: String, initial: Long?, onDismiss: () -> Unit, onSave: (Long?) -> Unit) {
    var input by rememberSaveable { mutableStateOf(initial?.let(::minorAmountInput) ?: "") }
    var attempted by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Месячный лимит · $currency") },
        text = { Column {
            Text("Для текущего календарного месяца. Хранится в сессии экрана.")
            OutlinedTextField(input, { input = it.take(40) }, label = { Text("Сумма бюджета") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                isError = attempted && parseMinorAmount(input) == null)
            TextButton(onClick = { onSave(null) }) { Text("Убрать бюджет") }
        } },
        confirmButton = { TextButton(onClick = {
            attempted = true
            parseMinorAmount(input)?.let(onSave)
        }) { Text("Применить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}
