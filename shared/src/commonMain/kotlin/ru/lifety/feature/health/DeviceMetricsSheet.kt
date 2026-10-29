@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ru.lifety.feature.health

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import ru.lifety.core.model.*
import ru.lifety.core.repository.HealthMetricsRepository
import ru.lifety.core.time.formatDate
import ru.lifety.core.time.localDate
import ru.lifety.core.ui.*

internal interface MetricDeviceSource {
    val available: Boolean
    val source: String
    suspend fun authorize(keys: Set<String>)
    suspend fun read(keys: Set<String>): List<HealthApiSample>
    fun cancel()
}
@Composable internal expect fun rememberMetricDeviceSource(): MetricDeviceSource

@Composable internal fun DeviceMetricsSheet(repository: HealthMetricsRepository, onDismiss: () -> Unit) {
    val source = rememberMetricDeviceSource()
    val scope = rememberCoroutineScope()
    var keys by remember { mutableStateOf(emptySet<String>()) }
    var preview by remember { mutableStateOf<List<HealthApiSample>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(source) { onDispose { source.cancel() } }
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null; message = null
        scope.launch { try { action() } catch (_: TimeoutCancellationException) { error = "Чтение заняло слишком много времени. Попробуйте ещё раз." } catch (c: CancellationException) { throw c } catch (_: Exception) { error = "Не удалось прочитать данные. Проверьте доступ к выбранным типам в настройках здоровья." } finally { busy = false } }
    }
    LifetyBottomSheet(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (isIos) "Данные Apple Health" else "Данные Health Connect", style = MaterialTheme.typography.headlineSmall)
            Text("Выберите, какие типы прочитать. Наличие API не означает, что на устройстве есть записи. Разрешение и сохранение в Lifety — отдельные действия.")
            healthApiMetrics.forEach { metric ->
                FilterChip(metric.sourceKey in keys, { keys = if (metric.sourceKey in keys) keys - metric.sourceKey!! else keys + metric.sourceKey!!; preview = null },
                    enabled = source.available && !busy, label = { Text("${metric.name} · ${metric.unit}") })
            }
            Text("Сон и шаги уже доступны через прежний блок импорта на экране здоровья.", style = MaterialTheme.typography.bodySmall)
            if (!source.available) Text("Импорт недоступен на этом устройстве. Все показатели можно вести вручную.")
            OutlinedButton(onClick = { run { preview = null; source.authorize(keys); message = "Доступ запрошен. Теперь прочитайте выбранные типы." } }, enabled = source.available && keys.isNotEmpty() && !busy) { Text("Настроить доступ") }
            Button(onClick = { run { preview = withTimeout(30_000) { source.read(keys) }; if (preview.isNullOrEmpty()) message = "Доступных записей нет. Отсутствие разрешения чтения также может давать пустой результат." } },
                enabled = source.available && keys.isNotEmpty() && !busy) { Text("Прочитать последние 30 дней") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }; message?.let { Text(it) }
            preview?.takeIf { it.isNotEmpty() }?.let { samples ->
                Text("Предпросмотр · ${samples.size} дневных значений", style = MaterialTheme.typography.titleMedium)
                samples.sortedWith(compareByDescending<HealthApiSample> { it.timestamp }.thenBy { it.key }).forEach { sample ->
                    val metric = healthApiMetrics.first { it.sourceKey == sample.key }
                    Text("${formatDate(localDate(sample.timestamp))} · ${metric.name}: ${metricValueLabel(metric, sample.value.toString())}", style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { run { repository.importSamples(samples, source.source); onDismiss() } }, enabled = !busy) { Text("Сохранить выбранные показатели") }
            }
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Закрыть") }
        }
    }
}
