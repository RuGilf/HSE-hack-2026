package ru.lifety.feature.health

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import ru.lifety.core.model.HealthSnapshot
import ru.lifety.core.platform.IosServices
import ru.lifety.core.repository.HealthRepository
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** HealthKit read authorization cannot be inspected; an empty response never means zero. */
@Composable
internal actual fun HealthConnectSection(repository: HealthRepository?, enabled: Boolean, onBusyChanged: (Boolean) -> Unit) {
    val source = IosServices.health
    val scope = rememberCoroutineScope()
    var sleep by remember { mutableStateOf(true) }
    var steps by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<List<HealthSnapshot>?>(null) }
    var baseline by remember { mutableStateOf<List<HealthSnapshot>>(emptyList()) }
    val currentBusyCallback by rememberUpdatedState(onBusyChanged)
    LaunchedEffect(busy) { currentBusyCallback(busy) }
    DisposableEffect(source) { onDispose { source?.cancelRead(); currentBusyCallback(false) } }
    val active = enabled && !busy && repository != null && source?.available == true
    fun operation(block: suspend () -> Unit) {
        if (busy) return
        busy = true; error = null; message = null
        scope.launch {
            try { block() }
            catch (_: TimeoutCancellationException) { error = "Apple Health не ответил вовремя. Повторите чтение." }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Не удалось прочитать или сохранить показатели. Проверьте доступ в приложении «Здоровье» и повторите." }
            finally { busy = false }
        }
    }
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Сон и шаги из Apple Health", style = MaterialTheme.typography.titleMedium)
        Text("Чтение — только по кнопке. Полученные дни сначала появятся здесь для проверки; сохранение в Lifety подтверждается отдельно.", style = MaterialTheme.typography.bodySmall)
        if (source?.available != true) Text("Apple Health недоступен на этом устройстве. Ручной ввод и локальная история работают.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            FilterChip(selected = sleep, enabled = active, onClick = { sleep = !sleep; preview = null }, label = { Text("Сон") })
            FilterChip(selected = steps, enabled = active, onClick = { steps = !steps; preview = null }, label = { Text("Шаги") })
        }
        OutlinedButton(enabled = active && (sleep || steps), onClick = { operation {
            preview = null
            // A permission dialog has no time limit: the user may need time to read it.
            suspendCancellableCoroutine { continuation ->
                source!!.requestAccess(sleep, steps) { failure ->
                    if (continuation.isActive) {
                        if (failure == null) continuation.resume(Unit) else continuation.resumeWithException(IllegalStateException(failure))
                    }
                }
            }
            message = "Настройки доступа закрыты. Apple не сообщает приложению, разрешено ли чтение. Нажмите «Прочитать за 30 дней», чтобы проверить доступные записи."
        } }) { Text("Настроить доступ Apple Health") }
        Button(enabled = active && (sleep || steps), onClick = { operation {
            preview = null
            baseline = repository!!.observeAll().first().sortedBy { it.timestamp }
            val rows = withTimeout(30_000) {
                suspendCancellableCoroutine<List<HealthSnapshot>> { continuation ->
                    continuation.invokeOnCancellation { source!!.cancelRead() }
                    source!!.readDays(sleep, steps, 30) { values, failure ->
                        if (continuation.isActive) {
                            if (failure != null) continuation.resumeWithException(IllegalStateException(failure))
                            else continuation.resume(values.orEmpty())
                        }
                    }
                }
            }.map { it.copy(sleepMinutes = it.sleepMinutes.takeIf { sleep }, steps = it.steps.takeIf { steps }) }
                .filter { it.sleepMinutes != null || it.steps != null }.sortedBy { it.timestamp }
            require(rows.map { it.timestamp }.distinct().size == rows.size)
            require(rows.all { (it.sleepMinutes == null || it.sleepMinutes in 0..1560) && (it.steps == null || it.steps >= 0) })
            if (rows.isEmpty()) message = "Доступных записей нет. Это может означать отсутствие данных или разрешения чтения. Локальная история сохранена."
            else preview = rows
        } }) { Text("Прочитать за 30 дней") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        preview?.let { rows ->
            HorizontalDivider()
            Text("Проверить: ${rows.size} дней", style = MaterialTheme.typography.titleMedium)
            Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.asReversed().forEach { row -> Text("${healthRecordedAt(row.timestamp)}\nСон: ${sleepLabel(row.sleepMinutes)} · Шаги: ${row.steps?.toString() ?: "Нет данных"}", style = MaterialTheme.typography.bodySmall) }
            }
            Button(enabled = active, onClick = { operation {
                val repo = requireNotNull(repository)
                if (repo.observeAll().first().sortedBy { it.timestamp } != baseline) {
                    preview = null; error = "Локальная история изменилась. Прочитайте показатели заново."
                } else {
                    val existing = baseline.associateBy { it.timestamp }
                    repo.updateAll(rows.map { row -> row.copy(sleepMinutes = row.sleepMinutes ?: existing[row.timestamp]?.sleepMinutes,
                        steps = row.steps ?: existing[row.timestamp]?.steps) })
                    preview = null; message = "Сохранено дней: ${rows.size}"
                }
            } }) { Text("Сохранить историю") }
            TextButton(enabled = !busy, onClick = { preview = null }) { Text("Отменить импорт") }
        }
        Text("Дневные итоги: сон может распределяться между соседними датами. Пустые показатели не заменяются нулями. Lifety не записывает ничего в Apple Health. Разрешения ИИ настраиваются отдельно.", style = MaterialTheme.typography.bodySmall)
    }
}
