package ru.lifety.feature.assistant.transfer

import androidx.compose.foundation.layout.*
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
import ru.lifety.core.planning.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConversationSettingsSheet(preferences: ChatGptPreferences, busy: Boolean, error: String?, onSave: (ChatGptPreferences) -> Unit,
    onDismiss: () -> Unit, onOpenApi: () -> Unit) {
    var instructions by rememberSaveable(preferences.instructions) { mutableStateOf(preferences.instructions) }
    var sleep by rememberSaveable(preferences.includeSleep) { mutableStateOf(preferences.includeSleep) }
    var url by rememberSaveable(preferences.conversationUrl) { mutableStateOf(preferences.conversationUrl) }
    var urlError by remember { mutableStateOf(false) }
    LifetyBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), contentColor = MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Ваш разговор ChatGPT", style = MaterialTheme.typography.titleLarge)
            Text("Создайте один разговор или проект в ChatGPT и возвращайтесь в него с обновлениями Lifety.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(instructions, { instructions = it.take(4000) }, Modifier.fillMaxWidth(), label = { Text("Постоянные предпочтения") },
                placeholder = { Text("Рабочие часы, доступное время, перерывы и ограничения") }, minLines = 3, maxLines = 6, enabled = !busy)
            Text("В первый запрос войдут все предпочтения. В обновлениях они повторяются только после изменений.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(url, { url = it.take(2048); urlError = false }, Modifier.fillMaxWidth(), label = { Text("Ссылка на ваш разговор · необязательно") },
                placeholder = { Text("https://chatgpt.com/c/…") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), singleLine = true, enabled = !busy,
                isError = urlError, supportingText = { Text(if (urlError) "Нужна личная ссылка chatgpt.com/c/…, не публичная ссылка «Поделиться»." else "Откроем ссылку; запрос вы вставляете и отправляете сами.") })
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Добавлять сводку сна", style = MaterialTheme.typography.titleMedium)
                    Text("Последние 7 дней и месячные медианы с количеством записей. Полная история и шаги не отправляются.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(sleep, { sleep = it }, enabled = !busy)
            }
            Text("Подготовленный текст хранится локально до импорта или изменения настроек. Внешняя отправка происходит только по вашему действию.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { if (validConversationUrl(url.trim())) onSave(ChatGptPreferences(instructions, sleep, url.trim())) else urlError = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Сохраняем…" else "Сохранить настройки") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Отмена") }
            HorizontalDivider()
            Text("Дополнительно", style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = onOpenApi, enabled = !busy) { Text("Встроенный чат · подключение API") }
            Spacer(Modifier.height(24.dp))
        }
    }
}
