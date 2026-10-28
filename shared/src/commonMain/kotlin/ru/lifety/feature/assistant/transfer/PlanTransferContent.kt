package ru.lifety.feature.assistant.transfer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import ru.lifety.core.time.formatDateTime
import ru.lifety.core.ui.LocalRepositories
import ru.lifety.core.ui.PlaceholderScreen
import kotlin.time.Instant

@Composable
internal fun PlanTransferContent(modifier: Modifier = Modifier, onBusyChanged: (Boolean) -> Unit = {}) {
    val repository = LocalRepositories.current?.planning
    if (repository == null) { PlaceholderScreen("Хранилище планов не подключено", modifier); return }
    val scope = rememberCoroutineScope()
    val controller = remember(repository, scope) { PlanTransferController(repository, scope) }
    val state by controller.state.collectAsState()
    var text by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var shareError by remember { mutableStateOf<String?>(null) }
    val share = rememberPlanShare()
    val clipboard = LocalClipboardManager.current
    val importFile = rememberPlanImportFile(onResult = { imported -> text = imported; shareError = null; controller.discardPreview(); notice = "Файл прочитан. Проверьте план перед применением." }, onError = { shareError = it })
    LaunchedEffect(state.busy) { onBusyChanged(state.busy) }
    DisposableEffect(Unit) { onDispose { onBusyChanged(false) } }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text("Обмен с ChatGPT", style = MaterialTheme.typography.titleLarge)
            Text("Сначала отправьте задачи и цели в ChatGPT. Затем вставьте его JSON-ответ и проверьте расписание перед применением.", style = MaterialTheme.typography.bodyMedium)
            Text("В экспорт входят названия задач, даты, дедлайны, цели и текущие оценки времени. Отправку выполняете вы через меню «Поделиться».",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        shareError?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
        if (state.snapshot == null && !state.busy) item { TextButton(onClick = controller::load) { Text("Повторить загрузку") } }
        item {
            Text("1. Экспорт", style = MaterialTheme.typography.titleMedium)
            state.snapshot?.let { Text("Задач: ${it.tasks.count { t -> !t.isCompleted }} · Целей: ${it.goals.count { g -> !g.isCompleted }}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            FilledTonalButton(onClick = controller::export, enabled = !state.busy && state.snapshot != null, modifier = Modifier.fillMaxWidth()) { Text("Подготовить для ChatGPT") }
        }
        state.prompt?.let { prompt -> item {
            Button(onClick = { try { share(prompt); shareError = null } catch (_: Exception) { shareError = "Не удалось открыть меню. Скопируйте текст и вставьте его в ChatGPT." } },
                enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Поделиться") }
            Row {
                TextButton(onClick = { clipboard.setText(AnnotatedString(prompt)); notice = "Текст скопирован. Вставьте его в ChatGPT." }) { Text("Скопировать") }
                TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Скрыть текст" else "Посмотреть текст") }
            }
            if (expanded) SelectionContainer { Text(prompt, style = MaterialTheme.typography.bodySmall) }
        } }
        notice?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
        item {
            HorizontalDivider()
            Text("2. Импорт ответа", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(text, { if (it.length <= 1_000_000) { text = it; shareError = null; controller.discardPreview() } else shareError = "Ответ больше 1 МБ. Вставьте только JSON плана." }, modifier = Modifier.fillMaxWidth(),
                label = { Text("JSON-ответ ChatGPT") }, placeholder = { Text("Вставьте полный ответ после составления плана") }, minLines = 4, maxLines = 8, enabled = !state.busy)
            Row {
                TextButton(onClick = { clipboard.getText()?.text?.let { if (it.length <= 1_000_000) { text = it; shareError = null; controller.discardPreview() } else shareError = "Ответ больше 1 МБ" } }, enabled = !state.busy) { Text("Вставить") }
                TextButton(onClick = importFile, enabled = !state.busy) { Text("Выбрать JSON-файл") }
            }
            FilledTonalButton(onClick = { controller.preview(text) }, enabled = !state.busy && text.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Проверить и посмотреть") }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        }
        val preview = state.preview
        if (preview != null) {
            item {
                Text("Предпросмотр плана", style = MaterialTheme.typography.titleLarge)
                Text(preview.summary)
                Text("Длительности и советы — оценки ChatGPT. Дедлайны, названия и выполненные задачи сохранятся.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(preview.tasks, key = { it.taskId }) { planned ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val original = state.snapshot?.tasks?.find { it.id == planned.taskId }
                        Text(original?.title.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text(original?.scheduledAt?.let { "Было: ${formatDateTime(it)}" } ?: "Раньше без времени", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        original?.deadlineAt?.let { deadline -> Text("Дедлайн: ${formatDateTime(deadline)}", style = MaterialTheme.typography.labelSmall,
                            color = if (deadline < kotlin.time.Clock.System.now().toEpochMilliseconds()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text("${formatDateTime(Instant.parse(planned.scheduledAt).toEpochMilliseconds())} · ${planned.durationMinutes} мин", color = MaterialTheme.colorScheme.primary)
                        Text(planned.approach, style = MaterialTheme.typography.bodySmall)
                        state.snapshot?.goals?.find { it.id == planned.goalId }?.let { Text("Цель: ${it.title}", style = MaterialTheme.typography.labelMedium) }
                    }
                }
            }
            item { Button(onClick = controller::apply, enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Применить план") } }
        }
        if (state.applied) item { Text("План применён. Расписание, оценки времени и советы сохранены. Агент увидит их при следующем запросе, если включён доступ к задачам.", color = MaterialTheme.colorScheme.primary) }
    }
}
