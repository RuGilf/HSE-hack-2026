package ru.lifety.feature.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ru.lifety.feature.assistant.agent.*
import ru.lifety.core.ui.*
import ru.lifety.core.model.HealthMetricSnapshot
import kotlinx.coroutines.flow.catch

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AgentSettingsSheet(state: AgentChatState, controller: AgentChatController, openTransfer: () -> Unit, dismiss: () -> Unit) {
    var base by remember(state.credentials) { mutableStateOf(state.credentials.settings.baseUrl) }
    var model by remember(state.credentials) { mutableStateOf(state.credentials.settings.model) }
    var key by remember(state.credentials) { mutableStateOf(state.credentials.key) }
    var scopes by remember(state.view.document.scopes) { mutableStateOf(state.view.document.scopes) }
    var confirmScopes by remember { mutableStateOf(false) }
    var metrics by remember { mutableStateOf(HealthMetricSnapshot()) }
    var metricsError by remember { mutableStateOf(false) }
    val metricRepository = LocalRepositories.current?.healthMetrics
    LaunchedEffect(metricRepository) { metricRepository?.observe()?.catch { metricsError = true }?.collect { metrics = it; metricsError = false } }
    val entered = AgentCredentials(state.credentials.settings.copy(baseUrl = base.trim().trimEnd('/').removeSuffix("/chat/completions"), model = model.trim()), key.trim())
    val configured = state.connected && entered == state.credentials
    LifetyBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Встроенный чат · API", style = MaterialTheme.typography.titleLarge)
            FilledTonalButton(onClick = openTransfer, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Вернуться к обмену с ChatGPT") }
            Text("Обмен планом работает без API-ключа.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text("Подключение", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(base, { base = it }, Modifier.fillMaxWidth(), label = { Text("Адрес API, включая /v1") }, singleLine = true, enabled = !state.busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("Модель") }, singleLine = true, enabled = !state.busy)
            OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("API-ключ") }, singleLine = true, enabled = !state.busy,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Text(controller.credentialStorageDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { controller.connect(entered) }, enabled = !state.busy && !configured && base.isNotBlank() && model.isNotBlank() && key.isNotBlank(),
                modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) "Сохраняю…" else if (configured) "Сохранено" else "Сохранить и подключить") }
            if (configured) {
                Text("✓ Настройки сохранены. Модель готова к отправке сообщения.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                FilledTonalButton(onClick = dismiss, modifier = Modifier.fillMaxWidth()) { Text("К чату") }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Доступ к данным", style = MaterialTheme.typography.titleLarge)
            Text("Выбранные сведения и прочитанные материалы могут отправляться вашему LLM API после отправки сообщения. Изменения записей потребуют подтверждения.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            ScopeToggle("Расходы", scopes.finance, state.connected && !state.busy) { scopes = scopes.copy(finance = it) }
            ScopeToggle("Задачи, цели и план", scopes.tasks, state.connected && !state.busy) { scopes = scopes.copy(tasks = it) }
            ScopeToggle("Здоровье", scopes.health, state.connected && !state.busy) { scopes = scopes.copy(health = it) }
            if (scopes.health) {
                Text("Какие показатели здоровья доступны ИИ", style = MaterialTheme.typography.titleSmall)
                ScopeToggle("Сон", scopes.healthSleep, !state.busy) { scopes = scopes.copy(healthSleep = it) }
                ScopeToggle("Шаги", scopes.healthSteps, !state.busy) { scopes = scopes.copy(healthSteps = it) }
                if (metricsError) Text("Не удалось загрузить показатели. Сохранённый выбор не изменён.", color = MaterialTheme.colorScheme.error)
                metrics.metrics.filterNot { it.archived }.forEach { metric -> ScopeToggle(metric.name, metric.id in scopes.healthMetricIds, !state.busy) { selected ->
                    scopes = scopes.copy(healthMetricIds = if (selected) scopes.healthMetricIds + metric.id else scopes.healthMetricIds - metric.id)
                } }
                Text("Последние 7 дней и месячные медианы. Полная история не передаётся.", style = MaterialTheme.typography.bodySmall)
            }
            ScopeToggle("Личная память", scopes.memory, state.connected && !state.busy) { scopes = scopes.copy(memory = it) }
            FilledTonalButton(onClick = { confirmScopes = true },
                enabled = state.connected && !state.busy && scopes != state.view.document.scopes, modifier = Modifier.fillMaxWidth()) { Text("Применить доступ") }
            Text("Запрос начинается только после отправки сообщения.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
        }
    }
    if (confirmScopes) AlertDialog(onDismissRequest = { confirmScopes = false }, title = { Text("Изменить доступ?") },
        text = { Text("Очистятся переписка и рабочие материалы, включая сохранённые разборы. Подтверждённая память останется; отключённые категории будут недоступны агенту.") },
        confirmButton = { TextButton(onClick = { controller.changeScopes(scopes); confirmScopes = false }) { Text("Применить") } },
        dismissButton = { TextButton(onClick = { confirmScopes = false }) { Text("Отмена") } })
}

@Composable private fun ScopeToggle(label: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    ListItem(headlineContent = { Text(label) }, trailingContent = { ru.lifety.core.ui.LifetySwitch(checked, change, enabled = enabled) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
}
