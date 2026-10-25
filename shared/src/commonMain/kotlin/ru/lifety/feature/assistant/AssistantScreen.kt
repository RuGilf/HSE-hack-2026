package ru.lifety.feature.assistant

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.lifety.core.ui.*
import ru.lifety.feature.assistant.ai.*

@Composable
fun AssistantScreen(modifier: Modifier = Modifier) {
    val repositories = LocalRepositories.current
    key(repositories) {
        val scope = rememberCoroutineScope()
        val controller = remember(repositories, scope) { AssistantController(repositories, scope) }
        val state by controller.state.collectAsState()
        Column(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LifetyMascot(Modifier.size(40.dp), animated = false)
                Column {
                    Text("План дня и разбор расходов", style = MaterialTheme.typography.titleMedium)
                    Text("Выберите данные перед отправкой", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (repositories == null) Text("Хранилище не подключено. Настройки доступны, анализ появится после подключения локальных данных.", color = MaterialTheme.colorScheme.error)
            AiSettings(state.settings, !state.busy, controller::updateSettings)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnalysisGoal.entries.forEach { goal ->
                    FilterChip(selected = state.goal == goal, onClick = { controller.chooseGoal(goal) }, enabled = !state.busy, label = { Text(goal.label) })
                }
            }
            FilledTonalButton(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), onClick = controller::preparePreview, enabled = !state.busy && repositories != null && state.settings.permissions.any) {
                Text(if (state.preview == null) "Подготовить предпросмотр" else "Обновить предпросмотр")
            }
            state.preview?.let { preview ->
                val json = remember(preview) { preview.previewJson() }
                Card(modifier = Modifier.animateContentSize(tween(LifetyMotion.Standard)), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Данные перед отправкой", style = MaterialTheme.typography.titleLarge)
                        Text("Адрес: ${state.settings.endpoint}\nМодель: ${state.settings.model.ifBlank { "не выбрана" }}")
                        Text("Это зафиксированный снимок. Именно этот JSON будет отправлен с инструкцией «${state.goal.label}». Ключ передаётся отдельно в заголовке авторизации.")
                        SelectionContainer {
                            Text(json, modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
                                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Расходы: последние 30 дней. Названия задач сокращены до 100 символов, максимум 40 задач. Магазины, источники и внутренние ID не отправляются.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = controller::analyze, enabled = !state.busy && settingsAreValid(state.settings)) { Text("Проанализировать") }
                        if (!settingsAreValid(state.settings)) Text("Для отправки укажите ключ, HTTPS endpoint и модель в настройках.")
                    }
                }
            }
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.outlineVariant)
                TextButton(onClick = controller::cancel) { Text("Отменить") }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.result?.let { result ->
                Text("Ваш следующий шаг", style = MaterialTheme.typography.headlineSmall)
                Text(result.summary, style = MaterialTheme.typography.bodyLarge)
                result.recommendations.forEach { recommendation ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(recommendation.title, style = MaterialTheme.typography.titleMedium)
                            recommendation.taskRef?.let { ref ->
                                val title = state.preview?.tasks?.items?.firstOrNull { it.ref == ref }?.title.orEmpty()
                                Text("Задача: $title", style = MaterialTheme.typography.labelLarge)
                            }
                            Text(recommendation.explanation)
                            recommendation.proposedAt?.let { Text("Предложенное время: ${assistantLocalTime(it)}") }
                        }
                    }
                }
                Text("Рекомендации могут быть неточными. Переносы можно применить самостоятельно в разделе задач. Данные не изменены. Медицинские диагнозы ассистент не ставит.", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AiSettings(settings: AiSettings, enabled: Boolean, onChange: (AiSettings) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Card(Modifier.fillMaxWidth()) {
        ListItem(
            modifier = Modifier.clickable(enabled = enabled) { focus.clearFocus(); expanded = !expanded },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            headlineContent = { Text("Подключение к ИИ") },
            supportingContent = { Text(if (settingsAreValid(settings)) settings.model else "Нужны API-ключ и модель") },
            trailingContent = { Text(if (expanded) "Скрыть" else "Настроить", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) },
        )
        AnimatedVisibility(expanded, enter = LifetyMotion.RevealEnter, exit = LifetyMotion.RevealExit) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Ключ хранится только в памяти. Запрос отправляется после вашего подтверждения.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(settings.apiKey.value, { onChange(settings.copy(apiKey = ApiKey(it))) }, modifier = Modifier.fillMaxWidth(),
                    enabled = enabled, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), label = { Text("API-ключ") })
                if (settings.apiKey.value.isNotEmpty()) TextButton(onClick = { onChange(settings.copy(apiKey = ApiKey())) }, enabled = enabled) { Text("Удалить ключ из сессии") }
                OutlinedTextField(settings.endpoint, { onChange(settings.copy(endpoint = it.trim())) }, modifier = Modifier.fillMaxWidth(),
                    enabled = enabled, singleLine = true, label = { Text("HTTPS endpoint") }, supportingText = { Text("OpenAI-совместимый /v1/chat/completions") })
                OutlinedTextField(settings.model, { onChange(settings.copy(model = it)) }, modifier = Modifier.fillMaxWidth(),
                    enabled = enabled, singleLine = true, label = { Text("Модель") })
            }
        }
    }
    Text("Данные для анализа", style = MaterialTheme.typography.titleMedium)
    Column {
        PermissionRow("Задачи и сроки", settings.permissions.tasks, enabled) { onChange(settings.copy(permissions = settings.permissions.copy(tasks = it))) }
        PermissionRow("Сводка расходов за 30 дней", settings.permissions.finances, enabled) { onChange(settings.copy(permissions = settings.permissions.copy(finances = it))) }
        PermissionRow("Последние сон и шаги", settings.permissions.health, enabled) { onChange(settings.copy(permissions = settings.permissions.copy(health = it))) }
    }
    Text("Отправка — только после предпросмотра и подтверждения.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PermissionRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled,
                modifier = Modifier.semantics { contentDescription = label })
        },
    )
}
