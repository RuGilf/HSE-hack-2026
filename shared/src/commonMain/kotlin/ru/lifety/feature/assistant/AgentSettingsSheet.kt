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

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AgentSettingsSheet(state: AgentChatState, controller: AgentChatController, dismiss: () -> Unit) {
    var base by remember(state.credentials) { mutableStateOf(state.credentials.settings.baseUrl) }
    var model by remember(state.credentials) { mutableStateOf(state.credentials.settings.model) }
    var key by remember(state.credentials) { mutableStateOf(state.credentials.key) }
    var scopes by remember(state.view.document.scopes) { mutableStateOf(state.view.document.scopes) }
    var confirmScopes by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Подключение", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(base, { base = it }, Modifier.fillMaxWidth(), label = { Text("Адрес API, включая /v1") }, singleLine = true, enabled = !state.busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("Модель") }, singleLine = true, enabled = !state.busy)
            OutlinedTextField(key, { key = it }, Modifier.fillMaxWidth(), label = { Text("API-ключ") }, singleLine = true, enabled = !state.busy,
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            Text(controller.credentialStorageDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { controller.connect(AgentCredentials(state.credentials.settings.copy(baseUrl = base.trim().trimEnd('/').removeSuffix("/chat/completions"), model = model.trim()), key.trim())) },
                enabled = !state.busy && base.isNotBlank() && model.isNotBlank() && key.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (state.busy) "Подключаю…" else "Сохранить и подключить") }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Доступ к данным", style = MaterialTheme.typography.titleLarge)
            Text("Выбранные сведения и прочитанные материалы могут отправляться вашему LLM API после отправки сообщения. Изменения записей потребуют подтверждения.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            ScopeToggle("Расходы", scopes.finance, state.connected && !state.busy) { scopes = scopes.copy(finance = it) }
            ScopeToggle("Задачи", scopes.tasks, state.connected && !state.busy) { scopes = scopes.copy(tasks = it) }
            ScopeToggle("Здоровье", scopes.health, state.connected && !state.busy) { scopes = scopes.copy(health = it) }
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
    ListItem(headlineContent = { Text(label) }, trailingContent = { Switch(checked, change, enabled = enabled) }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
}
