@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ru.lifety.feature.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.json.Json
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.datetime.*
import ru.lifety.core.model.*
import ru.lifety.core.model.HealthMetric as CustomMetric
import ru.lifety.core.planning.*
import ru.lifety.core.time.*
import ru.lifety.core.ui.*
import kotlin.time.Clock
import kotlin.uuid.Uuid

private val metricKindSaver = Saver<MetricKind, String>(save = { it.name }, restore = { MetricKind.valueOf(it) })
private val metricAggregationSaver = Saver<MetricAggregation, String>(save = { it.name }, restore = { MetricAggregation.valueOf(it) })
private val healthEntrySaver = Saver<HealthEntry?, String>(save = { Json.encodeToString(it) }, restore = { Json.decodeFromString<HealthEntry?>(it) })

internal fun metricValueLabel(metric: CustomMetric, value: String): String = when (metric.kind) {
    MetricKind.BOOLEAN -> if (value == "true") "Да" else "Нет"
    MetricKind.TEXT -> value
    MetricKind.NUMBER -> "${value.toDoubleOrNull()?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: value} ${metric.unit}".trim()
}

@Composable internal fun MetricEditorContent(initial: CustomMetric, hasEntries: Boolean, busy: Boolean, error: String?, onDismiss: () -> Unit, onSave: (CustomMetric) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial.name) }; var unit by rememberSaveable { mutableStateOf(initial.unit) }
    var kind by rememberSaveable(stateSaver = metricKindSaver) { mutableStateOf(initial.kind) }; var aggregation by rememberSaveable(stateSaver = metricAggregationSaver) { mutableStateOf(initial.aggregation) }
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Название") }, enabled = !busy)
            if (initial.sourceKey == null && !hasEntries) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MetricKind.entries.forEach { option ->
                FilterChip(kind == option, { kind = option; if (kind != MetricKind.NUMBER) aggregation = MetricAggregation.LATEST }, enabled = !busy,
                    label = { Text(when (option) { MetricKind.NUMBER -> "Число"; MetricKind.TEXT -> "Заметка"; MetricKind.BOOLEAN -> "Да / нет" }) })
            }
            }
            if (kind == MetricKind.NUMBER) {
                OutlinedTextField(unit, { unit = it.take(24) }, Modifier.fillMaxWidth(), label = { Text("Единица · необязательно") }, enabled = !busy && initial.sourceKey == null)
                Text("Итог за день", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MetricAggregation.entries.forEach { option -> FilterChip(aggregation == option, { aggregation = option }, enabled = !busy && initial.sourceKey == null,
                    label = { Text(when (option) { MetricAggregation.LATEST -> "Последнее значение"; MetricAggregation.SUM -> "Сумма"; MetricAggregation.AVERAGE -> "Среднее" }) }) }
            }
            }
            if (initial.sourceKey != null) Text("Единица и способ подсчёта заданы источником данных.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { onSave(initial.copy(name = name.trim(), unit = if (kind == MetricKind.NUMBER) unit.trim() else "", kind = kind, aggregation = aggregation)) }, enabled = name.isNotBlank() && !busy) { Text(if (initial.name.isBlank()) "Продолжить" else "Сохранить") }
            if (hasEntries || initial.name.isNotBlank()) TextButton(onClick = { onSave(initial.copy(archived = !initial.archived)) }, enabled = !busy) { Text(if (initial.archived) "Вернуть из архива" else "В архив · записи сохранятся") }
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Отмена") }
        }
}

@Composable internal fun MetricDetailContent(metric: CustomMetric, entries: List<HealthEntry>, busy: Boolean, error: String?, onDismiss: () -> Unit,
    startAdding: Boolean = false, onEdit: () -> Unit, onSave: (HealthEntry) -> Unit, onDelete: (HealthEntry) -> Unit) {
    var entry by rememberSaveable(stateSaver = healthEntrySaver) { mutableStateOf<HealthEntry?>(null) }
    var deleting by remember { mutableStateOf<HealthEntry?>(null) }
    var date by rememberSaveable { mutableStateOf(today().toString()) }
    var value by rememberSaveable { mutableStateOf(if (metric.kind == MetricKind.BOOLEAN) "true" else "") }; var form by rememberSaveable { mutableStateOf(startAdding) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val daily = remember(metric, entries) { metricDailyValues(metric, entries, Clock.System.now().toEpochMilliseconds(), TimeZone.currentSystemDefault()).entries.sortedBy { it.key }.associate { it.key to it.value } }
        LazyColumn(Modifier.fillMaxWidth(), state = list, contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row { TextButton(onClick = onEdit, enabled = !busy) { Text("Изменить показатель") } }
            if (!metric.archived && !form) Button(onClick = { entry = null; value = if (metric.kind == MetricKind.BOOLEAN) "true" else ""; date = today().toString(); form = true }) { Text("Добавить запись") }
            if (form) {
                LifetyDateField("Дата записи", date, { date = it }, enabled = !busy)
                if (metric.kind == MetricKind.BOOLEAN) Row { listOf("true" to "Да", "false" to "Нет").forEach { (v, title) -> FilterChip(value == v, { value = v }, enabled = !busy, label = { Text(title) }) } }
                else OutlinedTextField(value, { value = it.take(if (metric.kind == MetricKind.TEXT) 4000 else 80) }, Modifier.fillMaxWidth(), label = { Text(if (metric.unit.isBlank()) "Значение" else "Значение · ${metric.unit}") }, enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = if (metric.kind == MetricKind.NUMBER) KeyboardType.Decimal else KeyboardType.Text), maxLines = if (metric.kind == MetricKind.TEXT) 6 else 1)
                val normalized = runCatching { normalizedMetricValue(metric, value) }.getOrNull()
                Button(onClick = {
                    val timestamp = if (entry != null && localDate(entry!!.timestamp).toString() == date) entry!!.timestamp
                        else if (date == today().toString()) Clock.System.now().toEpochMilliseconds() else LocalDate.parse(date).atTime(12, 0).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                    onSave(HealthEntry(entry?.id ?: Uuid.random().toString(), metric.id, timestamp, requireNotNull(normalized)))
                }, enabled = !busy && normalized != null) { Text("Сохранить запись") }
                TextButton(onClick = { form = false }, enabled = !busy) { Text("Свернуть ввод") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (metric.kind == MetricKind.NUMBER && daily.isNotEmpty()) {
                val points = daily.entries.mapNotNull { (day, value) -> value.toFloatOrNull()?.takeIf(Float::isFinite)?.let { day to it } }.takeLast(90)
                val color = MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxWidth().height(130.dp)) {
                    val min = minOf(0f, points.minOfOrNull { it.second } ?: 0f); val max = maxOf(min + 1, points.maxOfOrNull { it.second } ?: 1f)
                    val span = points.first().first.daysUntil(points.last().first).coerceAtLeast(1)
                    val positions = points.map { (day, v) -> Offset(if (points.size == 1) size.width / 2 else points.first().first.daysUntil(day) * size.width / span, size.height - (v - min) / (max - min) * size.height) }
                    positions.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, 3.dp.toPx()) }; positions.forEach { drawCircle(color, 3.dp.toPx(), it) }
                }
                Text("${points.firstOrNull()?.first ?: ""} — ${points.lastOrNull()?.first ?: ""} · до 90 дней с записями · пропуски не считаются нулём", style = MaterialTheme.typography.labelSmall)
            }
            Text("История · ${entries.size}", style = MaterialTheme.typography.titleMedium)
            Text("Ручные записи имеют приоритет за день. Итоги разных API не складываются. Повторный импорт заменяет прежний итог.", style = MaterialTheme.typography.bodySmall)
            } }
            items(entries.asReversed(), key = { it.id }) { row ->
                var menu by remember { mutableStateOf(false) }
                ListItem(headlineContent = { Text(metricValueLabel(metric, row.value)) },
                    supportingContent = { Text("${formatDateTime(row.timestamp)} · ${healthSourceLabel(row.source)}") },
                    trailingContent = {
                        Box {
                            IconButton(onClick = { menu = true }, enabled = !busy, modifier = Modifier.semantics { contentDescription = "Действия с записью ${formatDateTime(row.timestamp)}" }) { LifetyIcon(LifetySymbol.More) }
                            DropdownMenu(menu, { menu = false }) {
                                if (row.source == "manual" && !metric.archived) DropdownMenuItem(text = { Text("Изменить") }, onClick = {
                                    menu = false; entry = row; value = row.value; date = localDate(row.timestamp).toString(); form = true
                                    scope.launch { list.animateScrollToItem(0) }
                                })
                                DropdownMenuItem(text = { Text("Удалить", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleting = row })
                            }
                        }
                    })
            }
        }
    deleting?.let { row -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Удалить запись?") }, text = { Text("Удалится только эта запись. Импортированная запись может вернуться при новом импорте.") },
        confirmButton = { TextButton(onClick = { deleting = null; if (entry?.id == row.id) { entry = null; form = false }; onDelete(row) }) { Text("Удалить") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } }) }
}

internal fun healthSourceLabel(source: String) = when (source) {
    "manual" -> "Вручную"; "healthkit" -> "Apple Health"; "health-connect" -> "Health Connect"; else -> "Импорт"
}
