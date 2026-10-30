package ru.lifety.feature.assistant.transfer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.lifety.core.ui.LifetyBottomSheet
import kotlinx.coroutines.flow.catch
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import ru.lifety.core.planning.*
import ru.lifety.core.ui.LocalRepositories
import ru.lifety.core.model.HealthMetricSnapshot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversationSettingsSheet(preferences: ChatGptPreferences, busy: Boolean, error: String?, onSave: (ChatGptPreferences) -> Unit,
    onDismiss: () -> Unit, onOpenApi: () -> Unit) {
    var instructions by rememberSaveable(preferences.instructions) { mutableStateOf(preferences.instructions) }
    var sleep by rememberSaveable(preferences.includeSleep) { mutableStateOf(preferences.includeSleep) }
    var url by rememberSaveable(preferences.conversationUrl) { mutableStateOf(preferences.conversationUrl) }
    var urlError by remember { mutableStateOf(false) }
    var tasks by rememberSaveable { mutableStateOf(preferences.includeTasks) }
    var goals by rememberSaveable { mutableStateOf(preferences.includeGoals) }
    var steps by rememberSaveable { mutableStateOf(preferences.includeSteps) }
    var finance by rememberSaveable { mutableStateOf(preferences.includeFinance) }
    var people by rememberSaveable { mutableStateOf(preferences.includePeople) }
    var metricIds by remember { mutableStateOf(preferences.healthMetricIds) }
    var metrics by remember { mutableStateOf(HealthMetricSnapshot()) }
    var metricsError by remember { mutableStateOf(false) }
    val repository = LocalRepositories.current?.healthMetrics
    LaunchedEffect(repository) { repository?.observe()?.catch { metricsError = true }?.collect { metrics = it; metricsError = false } }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LifetyBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).imePadding().padding(horizontal = 20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Контекст для ChatGPT", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss, enabled = !busy) { Text("Отмена") }
            }
            ru.lifety.core.ui.LifetySegmentedControl(listOf("Данные", "Разговор"), tab, { tab = it },
                Modifier.fillMaxWidth().padding(vertical = 12.dp), enabled = !busy)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (tab == 0) {
                    Text("Выберите, что включить в следующий запрос.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Планирование", style = MaterialTheme.typography.titleSmall)
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column { ContextToggle("Задачи и дедлайны", tasks, !busy) { tasks = it }; ContextToggle("Глобальные цели", goals, !busy) { goals = it } }
                    }
                    Text("Здоровье", style = MaterialTheme.typography.titleSmall)
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column {
                            ContextToggle("Сон", sleep, !busy) { sleep = it }
                            ContextToggle("Шаги", steps, !busy) { steps = it }
                            metrics.metrics.filterNot { it.archived }.forEach { metric -> ContextToggle(metric.name, metric.id in metricIds, !busy) { selected -> metricIds = if (selected) metricIds + metric.id else metricIds - metric.id } }
                        }
                    }
                    if (metricsError) Text("Не удалось загрузить показатели. Сохранённый выбор остаётся прежним.", color = MaterialTheme.colorScheme.error)
                    Text("7 дней и месячные медианы чисел. Заметки и да/нет — только 7 дней.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Другие разделы", style = MaterialTheme.typography.titleSmall)
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column { ContextToggle("Финансы · итоги за 30 дней", finance, !busy) { finance = it }; ContextToggle("Люди · дни рождения и заметки", people, !busy) { people = it } }
                    }
                    if (!tasks) Text("Без задач — обычное обсуждение. Импорт расписания будет выключен.", style = MaterialTheme.typography.bodySmall)
                    Text("Выключенные категории не войдут в новый запрос. Ранее отправленные сведения останутся в вашем разговоре ChatGPT.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    OutlinedTextField(instructions, { instructions = it.take(4000) }, Modifier.fillMaxWidth(), label = { Text("Постоянные предпочтения") },
                        placeholder = { Text("Рабочие часы, доступное время, перерывы") }, minLines = 3, maxLines = 6, enabled = !busy)
                    OutlinedTextField(url, { url = it.take(2048); urlError = false }, Modifier.fillMaxWidth(), label = { Text("Ссылка на разговор · необязательно") },
                        placeholder = { Text("https://chatgpt.com/c/…") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true, enabled = !busy,
                        isError = urlError, supportingText = { Text(if (urlError) "Нужна личная ссылка chatgpt.com/c/…, не публичная ссылка." else "Запрос можно скопировать и вставить в один постоянный чат.") })
                    Text("Предпочтения повторяются в запросе после изменений. Сохранённая ссылка открывается по вашему нажатию; отправляете сообщение вы сами.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                    TextButton(onClick = onOpenApi, enabled = !busy) { Text("Подключить встроенный чат") }
                    Text("Для обмена с ChatGPT ключ не нужен.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Button(onClick = { if (validConversationUrl(url.trim())) onSave(ChatGptPreferences(instructions, sleep, url.trim(), tasks, goals, steps, metricIds, finance, people)) else { urlError = true; tab = 1 } }, enabled = !busy, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)) { Text(if (busy) "Сохраняем…" else "Сохранить") }
        }
    }
}

@Composable private fun ContextToggle(title: String, selected: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = onChange).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Checkbox(selected, onCheckedChange = null, enabled = enabled)
    }
}
