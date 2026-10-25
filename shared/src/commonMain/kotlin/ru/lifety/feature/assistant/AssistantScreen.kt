package ru.lifety.feature.assistant

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.lifety.core.ui.LocalRepositories
import ru.lifety.feature.assistant.ai.*

@Composable
fun AssistantScreen(modifier: Modifier = Modifier) {
    val repositories = LocalRepositories.current
    key(repositories) {
        val scope = rememberCoroutineScope()
        val controller = remember(repositories, scope) { AssistantController(repositories, scope) }
        val state by controller.state.collectAsState()
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Помощь в вашем ритме", style = MaterialTheme.typography.headlineLarge)
            Text("ИИ опционален. Задачи, расходы и здоровье доступны локально без ключа и интернета.", style = MaterialTheme.typography.bodyLarge)
            if (repositories == null) Text("Хранилище не подключено. Настройки доступны, анализ появится после подключения локальных данных.", color = MaterialTheme.colorScheme.error)
            AiSettings(state.settings, !state.busy, controller::updateSettings)
            Text("Чем помочь?", style = MaterialTheme.typography.titleLarge)
            AnalysisGoal.entries.forEach { goal ->
                FilterChip(selected = state.goal == goal, onClick = { controller.chooseGoal(goal) }, enabled = !state.busy, label = { Text(goal.label) })
            }
            OutlinedButton(onClick = controller::preparePreview, enabled = !state.busy && repositories != null && state.settings.permissions.any) {
                Text(if (state.preview == null) "Подготовить предпросмотр" else "Обновить предпросмотр")
            }
            state.preview?.let { preview ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Данные перед отправкой", style = MaterialTheme.typography.titleLarge)
                        Text("Адрес: ${state.settings.endpoint}\nМодель: ${state.settings.model.ifBlank { "не выбрана" }}")
                        Text("Это зафиксированный снимок. Именно этот JSON будет отправлен с инструкцией «${state.goal.label}». Ключ передаётся отдельно в заголовке авторизации.")
                        SelectionContainer {
                            Text(preview.previewJson(), modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
                                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Расходы: последние 30 дней. Названия задач сокращены до 100 символов, максимум 40 задач. Магазины, источники и внутренние ID не отправляются.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = controller::analyze, enabled = !state.busy && settingsAreValid(state.settings)) { Text("Проанализировать") }
                        if (!settingsAreValid(state.settings)) Text("Для отправки укажите ключ, HTTPS endpoint и модель в настройках.")
                    }
                }
            }
            if (state.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
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
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, contentColor = MaterialTheme.colorScheme.onSurface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Настройки ИИ", style = MaterialTheme.typography.titleLarge)
            Text("Ключ и настройки хранятся только в памяти процесса. Данные не отправляются при запуске, выборе разрешений или изменении записей.")
            OutlinedTextField(settings.apiKey.value, { onChange(settings.copy(apiKey = ApiKey(it))) }, modifier = Modifier.fillMaxWidth(),
                enabled = enabled, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), label = { Text("API-ключ") })
            TextButton(onClick = { onChange(settings.copy(apiKey = ApiKey())) }, enabled = enabled && settings.apiKey.value.isNotEmpty()) { Text("Удалить ключ из сессии") }
            OutlinedTextField(settings.endpoint, { onChange(settings.copy(endpoint = it.trim())) }, modifier = Modifier.fillMaxWidth(),
                enabled = enabled, singleLine = true, label = { Text("Полный HTTPS endpoint") }, supportingText = { Text("OpenAI-совместимый /v1/chat/completions") })
            OutlinedTextField(settings.model, { onChange(settings.copy(model = it)) }, modifier = Modifier.fillMaxWidth(),
                enabled = enabled, singleLine = true, label = { Text("Имя модели у вашего провайдера") })
            Text("Какими данными поделиться", style = MaterialTheme.typography.titleMedium)
            PermissionRow("Задачи и сроки", settings.permissions.tasks, enabled) { onChange(settings.copy(permissions = settings.permissions.copy(tasks = it))) }
            PermissionRow("Сводка расходов за 30 дней", settings.permissions.finances, enabled) { onChange(settings.copy(permissions = settings.permissions.copy(finances = it))) }
            PermissionRow("Последние сон и шаги", settings.permissions.health, enabled) { onChange(settings.copy(permissions = settings.permissions.copy(health = it))) }
            Text("По умолчанию все категории выключены. Смена настроек сбрасывает предпросмотр.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PermissionRow(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, modifier = Modifier.semantics { contentDescription = label })
    }
}
