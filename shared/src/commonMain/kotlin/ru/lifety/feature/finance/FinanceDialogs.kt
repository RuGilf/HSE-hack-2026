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

@Composable
internal fun ExpenseEditorDialog(
    initial: Expense?,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: suspend (Expense) -> Boolean,
) {
    val scope = rememberCoroutineScope()
    val id = remember { initial?.id ?: "manual:${financeNow()}:${kotlin.random.Random.nextLong()}" }
    var merchant by rememberSaveable { mutableStateOf(initial?.merchant ?: "") }
    var amount by rememberSaveable { mutableStateOf(initial?.let { minorAmountInput(it.amountMinor) } ?: "") }
    var currency by rememberSaveable { mutableStateOf(initial?.currency ?: "RUB") }
    var category by rememberSaveable { mutableStateOf(initial?.category ?: "other") }
    var date by rememberSaveable { mutableStateOf(localFinanceDate(initial?.timestamp ?: financeNow()).toString()) }
    var attempted by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    val parsedDate = FinanceDate.parse(date)
    val minor = parseMinorAmount(amount)
    val valid = merchant.isNotBlank() && minor != null && parsedDate != null
    val locked = busy || submitting
    AlertDialog(
        onDismissRequest = { if (!locked) onDismiss() },
        title = { Text(when {
            initial?.source == "demo-notification" -> "Подтвердить ДЕМО-расход"
            initial?.source?.startsWith("notification:") == true -> "Проверить расход"
            initial == null -> "Новый расход"
            else -> "Изменить расход"
        }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(merchant, { merchant = it.take(120) }, label = { Text("Магазин") },
                    singleLine = true, isError = attempted && merchant.isBlank(), enabled = !locked)
                OutlinedTextField(amount, { amount = it.take(40) }, label = { Text("Сумма") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true, isError = attempted && minor == null, enabled = !locked,
                    supportingText = { Text("Больше нуля, не более двух знаков после запятой") })
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf("RUB", "USD", "EUR") + currency).distinct().forEach { code ->
                        FilterChip(selected = currency == code, enabled = !locked,
                            onClick = { currency = code }, label = { Text(code) })
                    }
                }
                Text("Категория", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (financeCategories.keys + category).distinct().forEach { key ->
                        FilterChip(selected = category == key, enabled = !locked,
                            onClick = { category = key }, label = { Text(categoryTitle(key)) })
                    }
                }
                OutlinedTextField(date, { date = it.take(10) }, label = { Text("Дата: ГГГГ-ММ-ДД") },
                    singleLine = true, isError = attempted && parsedDate == null, enabled = !locked)
                if (attempted && !valid) Text("Проверьте магазин, сумму и дату.", color = MaterialTheme.colorScheme.error)
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                if (initial?.source == "demo-notification") Text("Тестовая запись будет сохранена с меткой ДЕМО.")
            }
        },
        confirmButton = {
            TextButton(enabled = !locked, onClick = {
                attempted = true
                if (valid) {
                    submitting = true
                    scope.launch {
                        try {
                            val timestamp = if (initial != null && localFinanceDate(initial.timestamp) == parsedDate)
                                initial.timestamp else financeDayStart(requireNotNull(parsedDate))
                            onSave(Expense(id, requireNotNull(minor), currency, merchant.trim(), category,
                                timestamp, initial?.source ?: "manual"))
                        } finally { submitting = false }
                    }
                }
            }) { Text(if (locked) "Сохранение…" else "Сохранить") }
        },
        dismissButton = { TextButton(enabled = !locked, onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
internal fun BudgetDialog(currency: String, initial: Long?, onDismiss: () -> Unit, onSave: (Long?) -> Unit) {
    var input by rememberSaveable { mutableStateOf(initial?.let(::minorAmountInput) ?: "") }
    var attempted by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Бюджет · $currency") },
        text = { Column {
            Text("Для выбранного периода. Хранится только в текущей сессии экрана.")
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
