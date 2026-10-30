package ru.lifety.feature.assistant.transfer

import ru.lifety.core.ui.LifetyTextField

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.lifety.core.planning.*
import ru.lifety.core.navigation.LocalPlannerNavigation
import ru.lifety.core.time.formatDateTime
import ru.lifety.core.ui.*
import kotlin.time.Clock
import kotlin.time.Instant

@Composable
internal fun PlanTransferContent(modifier: Modifier = Modifier, onBusyChanged: (Boolean) -> Unit = {}, onOpenApi: () -> Unit = {}) {
    val repositories = LocalRepositories.current
    if (repositories?.planning == null || repositories.chatGptExchange == null || repositories.goals == null) {
        PlaceholderScreen("Хранилище обмена не подключено", modifier); return
    }
    val scope = rememberCoroutineScope()
    val controller = remember(repositories, scope) { PlanTransferController(repositories, scope) }
    val state by controller.state.collectAsState()
    val workspace = state.workspace
    val draft = workspace.draft
    val sent = workspace.lastSent
    val clipboard = LocalClipboardManager.current
    val uri = LocalUriHandler.current
    val share = rememberPlanShare()
    var settings by remember { mutableStateOf(false) }
    var prepareExpanded by rememberSaveable { mutableStateOf(false) }
    var request by rememberSaveable { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var inputVisible by remember { mutableStateOf(false) }
    var promptVisible by remember { mutableStateOf(false) }
    var sleepVisible by remember { mutableStateOf(false) }
    var planExpanded by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(draft?.id) { if (draft != null) { if (request.isBlank()) request = draft.request; prepareExpanded = false } }
    LaunchedEffect(state.busy) { onBusyChanged(state.busy) }
    DisposableEffect(Unit) { onDispose { onBusyChanged(false) } }
    fun readAnswer(value: String) {
        if (value.length > 1_000_000) { localError = "Ответ слишком большой. Вставьте только итоговый ответ с планом."; return }
        text = value; inputVisible = true; localError = null; notice = null
        controller.discardPreview(); controller.preview(value)
    }
    val importFile = rememberPlanImportFile(onResult = ::readAnswer, onError = { localError = it })
    val planning = LocalPlanningState.current
    LaunchedEffect(state.preview, state.busy, state.error, localError) {
        if (!state.busy && (state.preview != null || state.error != null || localError != null)) {
            val importIndex = 2 + (if (draft != null) 1 else 0) + (if (notice != null) 1 else 0) + (if (draft == null && sent?.awaitingReply == true) 1 else 0)
            list.animateScrollToItem(importIndex + if (state.preview != null) 1 else 0)
        }
    }
    val pendingWithoutDraft = draft == null && sent?.awaitingReply == true
    val changes = state.snapshot?.let { contextChanges(compactBaseline(it), sent?.baseline) }
    LazyColumn(modifier.widthIn(max = 720.dp).fillMaxWidth(), state = list,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp + LocalBottomNavigationInset.current), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LifetyMascot(Modifier.size(40.dp), emotion = LifetyEmotion.Calm, animated = false)
                Column(Modifier.weight(1f)) {
                    Text("План с ChatGPT", style = MaterialTheme.typography.titleMedium)
                    Text("Один разговор · актуальные данные", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { settings = true }, enabled = !state.busy && !state.loading, modifier = Modifier.semantics { contentDescription = "Настройки обмена" }) { LifetyIcon(LifetySymbol.Settings) }
            }
        }
        if (state.loading || state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item(key = "prepare") {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val formOpen = prepareExpanded || (draft == null && sent == null) || state.snapshot?.tasks?.none { !it.isCompleted } == true
                    TextButton(onClick = { settings = true }, enabled = !state.busy) { Text("Что отправить в ИИ · редактор запроса") }
                    Text(if (!formOpen) "Ваш контекст" else if (sent == null) "Начнём ваш разговор" else "Обновим ваш разговор", style = MaterialTheme.typography.titleLarge)
                    if (formOpen) Text("Вставляйте запросы в один и тот же чат ChatGPT. Первый задаёт правила, следующие обновляют состояние.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.snapshot?.let { Text("Задачи: ${if (workspace.preferences.includeTasks) it.tasks.count { t -> !t.isCompleted }.toString() else "не включены"} · Цели: ${if (workspace.preferences.includeGoals) it.goals.count { g -> !g.isCompleted }.toString() else "не включены"}", style = MaterialTheme.typography.labelLarge) }
                    changes?.takeIf { workspace.preferences.includeTasks && workspace.preferences.includeGoals && (formOpen || it.added + it.changed + it.completedOrRemoved + it.goalsChanged > 0) }?.let { Text("С последней отправки: +${it.added} новых, ${it.changed} изменено, ${it.completedOrRemoved} выполнено / удалено. Изменений целей: ${it.goalsChanged}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (workspace.preferences.includeSleep) Text("Сон: 7 дней · месяцев в сводке: ${state.sleep?.months?.size ?: 0}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    else Text("Сон не включён · можно добавить в настройках обмена", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (formOpen) {
                        LifetyTextField(request, { request = it.take(2000) }, Modifier.fillMaxWidth(), label = { Text(if (workspace.preferences.includeTasks) "Пожелания к этому плану" else "Вопрос для ChatGPT") }, placeholder = { Text("Например: свободен после 18:00, оставь время на отдых") }, minLines = 2, maxLines = 4, enabled = !state.busy)
                        if (workspace.preferences.includeTasks && state.snapshot?.tasks?.none { !it.isCompleted } == true) {
                            val planner = LocalPlannerNavigation.current
                            FilledTonalButton(onClick = { planner?.createTask() }, enabled = planner != null) { Text("Добавить задачу") }
                        } else Button(onClick = { localError = null; notice = null; controller.prepare(initial = sent == null, request) }, enabled = !state.busy && !state.loading && state.snapshot != null, modifier = Modifier.fillMaxWidth()) {
                            Text(if (sent == null) "Подготовить первый запрос" else "Подготовить обновление")
                        }
                        if (sent != null) TextButton(onClick = { controller.prepare(initial = true, request) }, enabled = !state.busy && !state.loading) { Text("Запрос для нового разговора") }
                    } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { localError = null; notice = null; controller.prepare(initial = sent == null, request) }, enabled = !state.busy && !state.loading, modifier = Modifier.weight(1f)) { Text(if (sent == null) "Подготовить заново" else "Обновить контекст") }
                        TextButton(onClick = { prepareExpanded = true }, enabled = !state.busy) { Text("Пожелания") }
                    }
                }
            }
        }
        if (draft != null) item(key = "handoff") {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val waiting = sent?.requestId == draft.id && sent.awaitingReply
                    val draftStale = state.draftStale || request.trim() != draft.request.trim()
                    Text(if (draftStale) "Контекст нужно обновить" else if (waiting) "Ждём ответ ChatGPT" else "Запрос готов", style = MaterialTheme.typography.titleMedium)
                    Text("Подготовлен ${formatDateTime(draft.preparedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (draftStale) Text("Изменились данные, пожелания, настройки или день. Подготовьте обновление выше.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    else {
                        Button(onClick = { controller.handoff { prompt ->
                            clipboard.setText(AnnotatedString(prompt))
                            if (workspace.preferences.conversationUrl.isNotBlank()) {
                                try { uri.openUri(workspace.preferences.conversationUrl); notice = "Запрос скопирован. Вставьте его в свой разговор." }
                                catch (_: Exception) { notice = "Запрос скопирован. Откройте свой разговор ChatGPT вручную." }
                            } else notice = "Запрос скопирован. Вставьте его в свой постоянный разговор ChatGPT."
                        } }, enabled = !state.busy, colors = if (waiting) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors(), modifier = Modifier.fillMaxWidth()) { Text(if (workspace.preferences.conversationUrl.isBlank()) "Скопировать запрос" else "Скопировать и открыть разговор") }
                        Row {
                            TextButton(onClick = { controller.handoff { prompt -> try { share(prompt); localError = null } catch (_: Exception) { localError = "Не удалось открыть меню. Используйте копирование." } } }, enabled = !state.busy) { Text("Поделиться") }
                            TextButton(onClick = { promptVisible = !promptVisible }) { Text(if (promptVisible) "Скрыть текст" else "Посмотреть текст") }
                        }
                        if (promptVisible) SelectionContainer { Text(draft.prompt.take(12_000) + if (draft.prompt.length > 12_000) "\n… Полный текст доступен через копирование." else "", Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) }
                        if (!waiting) FilledTonalButton(onClick = controller::markSent, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Я отправил · жду ответ") }
                        if (!waiting) Text("Отправку в ChatGPT выполняете вы. Системное меню не гарантирует выбор существующего чата.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (pendingWithoutDraft) item(key = "waiting") {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Ждём ответ ChatGPT", style = MaterialTheme.typography.titleMedium)
                    Text("Отправка подтверждена ${formatDateTime(sent!!.sentAt)}. Вставьте ответ ниже или подготовьте новое обновление.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        notice?.let { item { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) } }
        item(key = "import") {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (workspace.preferences.includeTasks) "Вернуть план в Lifety" else "Ответ — в вашем разговоре ChatGPT", style = MaterialTheme.typography.titleMedium)
                    if (!workspace.preferences.includeTasks) Text("Обсуждайте ответ прямо в ChatGPT. Чтобы перенести расписание в Lifety, включите задачи в редакторе и подготовьте новый запрос.", style = MaterialTheme.typography.bodySmall)
                    else {
                    Text("Скопируйте итоговый ответ из ChatGPT. Сначала покажем изменения — расписание обновится только после подтверждения.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(colors = if (sent?.awaitingReply == true) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(), onClick = { val value = clipboard.getText()?.text; if (value.isNullOrBlank()) localError = "Буфер обмена пуст. Скопируйте итоговый ответ ChatGPT." else readAnswer(value) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Вставить ответ ChatGPT") }
                    Row {
                        TextButton(onClick = importFile, enabled = !state.busy) { Text("Из файла") }
                        TextButton(onClick = { inputVisible = !inputVisible }, enabled = !state.busy) { Text("Вставить вручную") }
                    }
                    if (inputVisible) {
                        LifetyTextField(text, { if (it.length <= 1_000_000) { text = it; localError = null; controller.discardPreview() } else localError = "Ответ слишком большой" }, Modifier.fillMaxWidth(), label = { Text("Итоговый ответ с планом") }, minLines = 3, maxLines = 7, enabled = !state.busy)
                        TextButton(onClick = { controller.preview(text) }, enabled = !state.busy && text.isNotBlank()) { Text("Проверить ответ") }
                    }
                    }
                    (localError ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    if (state.snapshot == null && !state.loading) TextButton(onClick = controller::load) { Text("Повторить загрузку") }
                }
            }
        }
        state.preview?.let { preview ->
            item(key = "preview") {
                Text("Изменения плана", style = MaterialTheme.typography.titleLarge)
                Text(preview.summary)
                Text("Оценки времени и советы — предположения ChatGPT. Дедлайны и выполненные задачи сохранятся.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(preview.tasks, key = { "slot-${it.taskId}" }) { planned ->
                val original = state.snapshot?.tasks?.find { it.id == planned.taskId }
                Card {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(original?.title.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        Text(original?.scheduledAt?.let { "Было: ${formatDateTime(it)}" } ?: "Раньше без времени", style = MaterialTheme.typography.labelSmall)
                        Text("Будет: ${formatDateTime(Instant.parse(planned.scheduledAt).toEpochMilliseconds())} · ${planned.durationMinutes} мин", color = MaterialTheme.colorScheme.primary)
                        original?.deadlineAt?.let { Text("Дедлайн: ${formatDateTime(it)}", style = MaterialTheme.typography.labelSmall, color = if (it < Clock.System.now().toEpochMilliseconds()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                        Text(planned.approach, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            items(preview.deferredTasks, key = { "defer-${it.taskId}" }) { deferred ->
                val task = state.snapshot?.tasks?.find { it.id == deferred.taskId }
                OutlinedCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("В очередь · ${task?.title.orEmpty()}", style = MaterialTheme.typography.titleSmall)
                        Text(deferred.reason, style = MaterialTheme.typography.bodySmall)
                        Text("Прежнее время выполнения будет снято. Задача, дедлайн и прежняя оценка сохранятся.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(key = "apply") { Button(onClick = controller::apply, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) { Text("Применить план") } }
        }
        if (workspace.preferences.includeSleep && state.sleep != null) item(key = "sleep") {
            OutlinedCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { sleepVisible = !sleepVisible }) { Text(if (sleepVisible) "Скрыть сводку сна" else "Какие данные сна отправятся") }
                    if (sleepVisible) SleepSummaryPreview(state.sleep!!)
                }
            }
        }
        if (state.applied) item { Text("План сохранён. Расписание, оценки и очередь обновлены.", color = MaterialTheme.colorScheme.primary) }
        planning.report?.let { report -> item(key = "current-plan") {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Текущий план", style = MaterialTheme.typography.titleLarge)
                    Text("Последний разбор · ${formatDateTime(report.importedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(report.summary, maxLines = if (planExpanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()))
                    TextButton(onClick = { planExpanded = !planExpanded }) { Text(if (planExpanded) "Свернуть" else "Подробнее") }
                    state.snapshot?.tasks?.filter { !it.isCompleted && it.scheduledAt != null }?.sortedBy { it.scheduledAt }?.take(4)?.forEach { task ->
                        Text(task.title, style = MaterialTheme.typography.titleSmall)
                        Text("${formatDateTime(task.scheduledAt!!)}${planning.plans[task.id]?.let { " · ${it.durationMinutes} мин" }.orEmpty()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    workspace.receipt?.deferredTasks?.filter { d -> state.snapshot?.tasks?.any { it.id == d.taskId && !it.isCompleted && it.scheduledAt == null } == true }?.let { queue ->
                        if (queue.isNotEmpty()) {
                            Text("В очереди: ${queue.size}", style = MaterialTheme.typography.titleSmall)
                            queue.take(if (planExpanded) queue.size else 3).forEach { d ->
                                Text("${state.snapshot?.tasks?.find { it.id == d.taskId }?.title}: ${d.reason}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        } }
    }
    if (settings) ConversationSettingsSheet(workspace.preferences, state.busy, state.error,
        onSave = { controller.savePreferences(it) { settings = false; prepareExpanded = true; notice = "Настройки сохранены. Подготовьте новый запрос." } },
        onDismiss = { settings = false }, onOpenApi = { settings = false; onOpenApi() })
}

@Composable
private fun SleepSummaryPreview(sleep: SleepContext) {
    Text("Последние 7 дней · ${sleep.timeZone}", style = MaterialTheme.typography.titleSmall)
    sleep.last7Days.forEach { day -> Text("${day.date}: ${day.minutes?.let { "$it мин" } ?: "нет данных"}", style = MaterialTheme.typography.bodySmall) }
    Text("Медианы по месяцам", style = MaterialTheme.typography.titleSmall)
    Column(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (sleep.months.isEmpty()) Text("Нет записей сна", style = MaterialTheme.typography.bodySmall)
        sleep.months.forEach { month -> Text("${month.month}: ${if (month.medianMinutes % 1.0 == 0.0) month.medianMinutes.toInt().toString() else month.medianMinutes.toString()} мин · ${month.recordedDays}/${month.daysInMonth} дней${if (month.isPartial) " · месяц не завершён" else ""}", style = MaterialTheme.typography.bodySmall) }
    }
    Text("Медиана считается на устройстве по известным дням. Пропуски не считаются нулём.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
