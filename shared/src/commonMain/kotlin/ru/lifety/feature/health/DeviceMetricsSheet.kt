package ru.lifety.feature.health

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import ru.lifety.core.model.*
import ru.lifety.core.model.HealthMetric as CustomMetric
import ru.lifety.core.repository.HealthMetricsRepository
import ru.lifety.core.repository.HealthRepository
import ru.lifety.core.time.formatDate
import ru.lifety.core.time.localDate

internal interface MetricDeviceSource {
    val available: Boolean
    val source: String
    val setupRequired: Boolean get() = false
    fun openSetup() {}
    suspend fun authorize(keys: Set<String>)
    suspend fun read(keys: Set<String>): List<HealthApiSample>
    fun cancel()
}
@Composable internal expect fun rememberMetricDeviceSource(): MetricDeviceSource
internal val deviceHealthTypes = listOf(CustomMetric("sleep", "Сон", "мин", sourceKey = "sleep"), CustomMetric("steps", "Шаги", "шагов", sourceKey = "steps")) + healthApiMetrics
internal fun deviceValueLabel(key: String, value: Double): String = if (key == "sleep") sleepLabel(value.toInt())
    else metricValueLabel(deviceHealthTypes.first { it.sourceKey == key }, value.toString())

@Composable internal fun DeviceMetricsContent(health: HealthRepository?, repository: HealthMetricsRepository?, onBusyChanged: (Boolean) -> Unit, onSaved: () -> Unit) {
    val source = rememberMetricDeviceSource()
    val scope = rememberCoroutineScope()
    var keys by rememberSaveable(stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })) { mutableStateOf(setOf("sleep", "steps")) }
    var preview by remember { mutableStateOf<List<HealthApiSample>?>(null) }
    var baseline by remember { mutableStateOf<List<HealthSnapshot>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var help by remember { mutableStateOf(false) }
    val currentBusy by rememberUpdatedState(onBusyChanged)
    DisposableEffect(source) { onDispose { source.cancel(); currentBusy(false) } }
    fun operation(action: suspend () -> Unit) {
        if (busy) return
        busy = true; currentBusy(true); error = null; message = null
        scope.launch {
            try { action() }
            catch (_: TimeoutCancellationException) { error = "Источник не ответил вовремя. Попробуйте ещё раз." }
            catch (c: CancellationException) { throw c }
            catch (e: IllegalArgumentException) { error = e.message ?: "Не удалось проверить записи" }
            catch (_: Exception) { error = "Не удалось получить записи. Проверьте разрешения в настройках здоровья и повторите." }
            finally { busy = false; currentBusy(false) }
        }
    }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Выберите показатели для Lifety", style = MaterialTheme.typography.titleMedium) }
        items(deviceHealthTypes, key = { it.id }) { metric ->
            Row(Modifier.fillMaxWidth().toggleable(metric.sourceKey in keys, enabled = source.available && !busy, role = Role.Checkbox) {
                keys = if (it) keys + metric.sourceKey!! else keys - metric.sourceKey!!; preview = null; error = null; message = null
            }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) { Text(metric.name); Text(metric.unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Checkbox(metric.sourceKey in keys, onCheckedChange = null, enabled = source.available && !busy)
            }
        }
        item {
            HorizontalDivider()
            Text("За последние 30 дней. При необходимости система запросит доступ. Затем покажем записи перед сохранением.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
        }
        if (!source.available) item {
            Text(if (source.setupRequired) "Для подключения установите или обновите Health Connect. Затем откройте этот раздел заново." else "Источник недоступен на этом устройстве. Показатели можно вести вручную.")
            if (source.setupRequired) FilledTonalButton(onClick = { try { source.openSetup() } catch (_: Exception) { error = "Не удалось открыть магазин. Найдите Health Connect в Google Play." } }) { Text("Установить Health Connect") }
        }
        item {
            Button(onClick = { operation {
                preview = null
                source.authorize(keys)
                baseline = requireNotNull(health).observeAll().first()
                preview = withTimeout(30_000) { source.read(keys) }.filter { it.key in keys }
                if (preview.isNullOrEmpty()) message = "Доступных записей нет. Проверьте, есть ли они в приложении здоровья и разрешено ли чтение."
            } }, enabled = source.available && keys.isNotEmpty() && !busy && health != null && repository != null, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Обрабатываем…" else "Получить записи") }
        }
        if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
        message?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium) } }
        preview?.takeIf { it.isNotEmpty() }?.let { rows ->
            item {
                Text("Проверить перед сохранением", style = MaterialTheme.typography.titleMedium)
                val missing = keys - rows.map { it.key }.toSet()
                if (missing.isNotEmpty()) Text("Нет доступных записей: " + deviceHealthTypes.filter { it.sourceKey in missing }.joinToString { it.name } + ". Проверьте данные и разрешения источника.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(rows.groupBy { it.key }.entries.toList(), key = { "preview-${it.key}" }) { (key, values) ->
                val last = values.maxBy { it.timestamp }
                ListItem(headlineContent = { Text(deviceHealthTypes.first { it.sourceKey == key }.name) }, supportingContent = { Text("${deviceValueLabel(key, last.value)} · ${formatDate(localDate(last.timestamp))}\n${values.size} дней с данными") })
            }
            item {
                Button(onClick = { operation {
                    requireNotNull(repository).importDeviceSamples(rows, source.source, baseline)
                    busy = false; currentBusy(false); onSaved()
                } }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Сохранить в Lifety") }
                TextButton(onClick = { expanded = !expanded }, enabled = !busy) { Text(if (expanded) "Свернуть записи" else "Все записи · ${rows.size}") }
            }
            if (expanded) items(rows.sortedWith(compareByDescending<HealthApiSample> { it.timestamp }.thenBy { it.key }), key = { "${it.key}:${it.timestamp}" }) { row ->
                Text("${formatDate(localDate(row.timestamp))} · ${deviceHealthTypes.first { it.sourceKey == row.key }.name}: ${deviceValueLabel(row.key, row.value)}", style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            TextButton(onClick = { help = !help }) { Text(if (help) "Скрыть пояснения" else "Как используются данные") }
            if (help) Text("Lifety только читает выбранные показатели. Обновление вручную, без фоновой синхронизации. Пустой результат не означает ноль. Apple Health также не раскрывает статус разрешения чтения. Повторное сохранение обновляет дневные итоги. Доступ ИИ настраивается отдельно в ассистенте.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
