@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ru.lifety.feature.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

@Composable internal fun HealthMetricsSection() {
    val repo = LocalRepositories.current?.healthMetrics ?: return
    var snapshot by remember { mutableStateOf(HealthMetricSnapshot()) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(repo, retry) { repo.observe().catch { error = "Не удалось загрузить показатели" }.collect { snapshot = it; error = null } }
    var editing by remember { mutableStateOf<CustomMetric?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var api by remember { mutableStateOf(false) }
    var archive by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    fun write(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null
        scope.launch { try { action() } catch (c: CancellationException) { throw c } catch (e: Exception) { error = e.message ?: "Не удалось сохранить" } finally { busy = false } }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Мои показатели", style = MaterialTheme.typography.titleLarge)
        Text("Добавляйте свои измерения, привычки и заметки. В ИИ попадут только выбранные категории.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { editing = CustomMetric(Uuid.random().toString(), "") }) { Text("+ Показатель") }
            OutlinedButton(onClick = { api = true }) { Text("Из API") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { retry++ }) { Text("Повторить") } }
        snapshot.metrics.filter { it.archived == archive }.forEach { metric ->
            val last = snapshot.entries.lastOrNull { it.metricId == metric.id }
            OutlinedCard(onClick = { selected = metric.id }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(metric.name, style = MaterialTheme.typography.titleMedium)
                    Text(last?.let { "${metricValueLabel(metric, it.value)} · ${formatDate(localDate(it.timestamp))}" } ?: "Пока нет записей", style = MaterialTheme.typography.bodyMedium)
                    if (metric.sourceKey != null) Text("Ручной ввод / импорт устройства", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (snapshot.metrics.any { it.archived }) TextButton(onClick = { archive = !archive }) { Text(if (archive) "Активные показатели" else "Архив") }
    }
    editing?.let { metric -> MetricEditorSheet(metric, snapshot.entries.any { it.metricId == metric.id }, busy, error, onDismiss = { if (!busy) editing = null }, onSave = { value -> write { repo.saveMetric(value); editing = null } }) }
    selected?.let { id -> snapshot.metrics.find { it.id == id }?.let { metric ->
        MetricDetailSheet(metric, snapshot.entries.filter { it.metricId == id }, busy, error, onDismiss = { if (!busy) selected = null },
            onEdit = { selected = null; editing = metric }, onSave = { entry -> write { repo.saveEntry(entry); selected = null } }, onDelete = { entry -> write { repo.deleteEntry(entry.id) } })
    } }
    if (api) DeviceMetricsSheet(repo, onDismiss = { api = false })
}

internal fun metricValueLabel(metric: CustomMetric, value: String): String = when (metric.kind) {
    MetricKind.BOOLEAN -> if (value == "true") "Да" else "Нет"
    MetricKind.TEXT -> value
    MetricKind.NUMBER -> "${value.toDoubleOrNull()?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: value} ${metric.unit}".trim()
}

@Composable private fun MetricEditorSheet(initial: CustomMetric, hasEntries: Boolean, busy: Boolean, error: String?, onDismiss: () -> Unit, onSave: (CustomMetric) -> Unit) {
    var name by remember { mutableStateOf(initial.name) }; var unit by remember { mutableStateOf(initial.unit) }
    var kind by remember { mutableStateOf(initial.kind) }; var aggregation by remember { mutableStateOf(initial.aggregation) }
    LifetyBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Показатель здоровья", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(name, { name = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Название") }, enabled = !busy)
            if (initial.sourceKey == null && !hasEntries) MetricKind.entries.forEach { option ->
                FilterChip(kind == option, { kind = option; if (kind != MetricKind.NUMBER) aggregation = MetricAggregation.LATEST }, enabled = !busy,
                    label = { Text(when (option) { MetricKind.NUMBER -> "Число"; MetricKind.TEXT -> "Заметка"; MetricKind.BOOLEAN -> "Да / нет" }) })
            }
            if (kind == MetricKind.NUMBER) {
                OutlinedTextField(unit, { unit = it.take(24) }, label = { Text("Единица · необязательно") }, enabled = !busy && initial.sourceKey == null)
                Text("Итог за день", style = MaterialTheme.typography.titleSmall)
                MetricAggregation.entries.forEach { option -> FilterChip(aggregation == option, { aggregation = option }, enabled = !busy && initial.sourceKey == null,
                    label = { Text(when (option) { MetricAggregation.LATEST -> "Последнее значение"; MetricAggregation.SUM -> "Сумма"; MetricAggregation.AVERAGE -> "Среднее" }) }) }
            }
            if (initial.sourceKey != null) Text("Единица и способ подсчёта заданы источником API.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { onSave(initial.copy(name = name.trim(), unit = if (kind == MetricKind.NUMBER) unit.trim() else "", kind = kind, aggregation = aggregation)) }, enabled = name.isNotBlank() && !busy) { Text("Сохранить") }
            if (hasEntries || initial.name.isNotBlank()) TextButton(onClick = { onSave(initial.copy(archived = !initial.archived)) }, enabled = !busy) { Text(if (initial.archived) "Вернуть из архива" else "В архив · записи сохранятся") }
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Отмена") }
        }
    }
}

@Composable private fun MetricDetailSheet(metric: CustomMetric, entries: List<HealthEntry>, busy: Boolean, error: String?, onDismiss: () -> Unit,
    onEdit: () -> Unit, onSave: (HealthEntry) -> Unit, onDelete: (HealthEntry) -> Unit) {
    var entry by remember { mutableStateOf<HealthEntry?>(null) }
    var deleting by remember { mutableStateOf<HealthEntry?>(null) }
    var date by remember { mutableStateOf(today().toString()) }
    var value by remember { mutableStateOf("") }; var form by remember { mutableStateOf(false) }
    val daily = remember(metric, entries) { metricDailyValues(metric, entries, Clock.System.now().toEpochMilliseconds(), TimeZone.currentSystemDefault()).entries.sortedBy { it.key }.associate { it.key to it.value } }
    LifetyBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(metric.name, style = MaterialTheme.typography.headlineSmall)
            Row { TextButton(onClick = onEdit, enabled = !busy) { Text("Настроить") }; TextButton(onClick = onDismiss, enabled = !busy) { Text("Закрыть") } }
            if (!metric.archived) Button(onClick = { entry = null; value = if (metric.kind == MetricKind.BOOLEAN) "true" else ""; date = today().toString(); form = true }) { Text("Добавить запись") }
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
            entries.asReversed().forEach { row ->
                OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text(metricValueLabel(metric, row.value)); Text("${formatDateTime(row.timestamp)} · ${row.source}", style = MaterialTheme.typography.labelSmall)
                    Row {
                        if (row.source == "manual" && !metric.archived) TextButton(onClick = { entry = row; value = row.value; date = localDate(row.timestamp).toString(); form = true }, enabled = !busy) { Text("Изменить") }
                        TextButton(onClick = { deleting = row }, enabled = !busy) { Text("Удалить") }
                    }
                } }
            }
        }
    }
    deleting?.let { row -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Удалить запись?") }, text = { Text("Удалится только эта запись. Импортированная запись может вернуться при новом импорте.") },
        confirmButton = { TextButton(onClick = { deleting = null; if (entry?.id == row.id) { entry = null; form = false }; onDelete(row) }) { Text("Удалить") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена") } }) }
}
