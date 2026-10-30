package ru.lifety.feature.tasks

import ru.lifety.core.ui.LifetyTextField

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import ru.lifety.core.ui.isIos
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.time.Clock
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.datetime.TimeZone
import ru.lifety.core.ui.LifetyBottomSheet
import ru.lifety.core.planner.taskIntervalError
import ru.lifety.core.time.localDate
import ru.lifety.core.time.today
import ru.lifety.core.ui.LifetyIcon
import ru.lifety.core.ui.LifetySymbol
import ru.lifety.core.ui.LocalPlanningState
import ru.lifety.core.ui.LocalRepositories
import ru.lifety.core.ui.PlaceholderScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(taskId: String?, initialScheduledAt: Long?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val repository = LocalRepositories.current?.tasks
    if (repository == null) { PlaceholderScreen("Хранилище не подключено", modifier); return }
    val planning = LocalRepositories.current?.planning
    val model = viewModel { TaskEditorViewModel(repository, taskId, initialScheduledAt, planning) }
    val state by model.state.collectAsState()
    var now by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = Clock.System.now().toEpochMilliseconds() } }
    val durationRequired = state.hadDetails || state.approach.isNotBlank() || state.goalId != null || state.duration.isNotBlank()
    val timingError = if (planning != null && durationRequired && state.duration.toIntOrNull()?.let { it in 1..1440 } != true)
        "Укажите длительность от 1 до 1440 минут" else state.task?.let { taskIntervalError(it, state.duration.toIntOrNull(), now) }
    var detailsExpanded by rememberSaveable(taskId) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val saving by rememberUpdatedState(state.saving)
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || !saving || closing })
    LaunchedEffect(model) { model.finished.collect { closing = true; sheet.hide(); onClose() } }
    val close: () -> Unit = { if (!closing && !state.saving) scope.launch { closing = true; sheet.hide(); onClose() } }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val sheetHeight = maxHeight * 0.92f
        LifetyBottomSheet(onDismissRequest = { if (!state.saving) onClose() }, sheetState = sheet,
            containerColor = MaterialTheme.colorScheme.background) {
            Scaffold(modifier = Modifier.fillMaxWidth().height(sheetHeight), contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    if (isIos) Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = close, enabled = !state.saving && !closing) { Text("Отмена") }
                        BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(if (taskId == null) "Новая задача" else "Задача", style = MaterialTheme.typography.titleMedium)
                        }
                        if (taskId != null && state.task != null) TextButton(onClick = { confirmDelete = true }, enabled = !state.saving) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
                        else Spacer(Modifier.size(80.dp, 48.dp))
                    } else TopAppBar(title = { Text(if (taskId == null) "Новая задача" else "Редактирование", style = MaterialTheme.typography.titleLarge) },
                        navigationIcon = { IconButton(onClick = close, enabled = !state.saving && !closing, modifier = Modifier.semantics { contentDescription = "Закрыть редактор" }) { LifetyIcon(LifetySymbol.Close) } },
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        actions = { if (taskId != null && state.task != null) TextButton(onClick = { confirmDelete = true }, enabled = !state.saving) { Text("Удалить", color = MaterialTheme.colorScheme.error) } })
                },
                bottomBar = {
                    if (state.task != null) Surface(color = MaterialTheme.colorScheme.background) {
                        Column {
                            timingError?.let { Text(it, color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                            Button(onClick = model::save, enabled = !state.saving && timingError == null, modifier = Modifier.fillMaxWidth().imePadding().padding(16.dp).height(48.dp)) {
                                if (state.saving) CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp).size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                                Text(if (state.saving) "Сохраняем…" else if (taskId == null) "Создать задачу" else "Сохранить изменения")
                            }
                        }
                    }
            }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    when {
                        state.loading -> CircularProgressIndicator()
                        state.task == null -> {
                            Text(state.error ?: "Задача недоступна", color = MaterialTheme.colorScheme.error)
                            Button(onClick = model::reload) { Text("Повторить") }
                        }
                        else -> {
                            val task = requireNotNull(state.task)
                            // IME selection/composition must update synchronously, without waiting
                            // for the StateFlow echo of each key (which can reset the iOS caret).
                            var titleEdit by rememberSaveable(task.id, stateSaver = TextFieldValue.Saver) {
                                mutableStateOf(TextFieldValue(task.title, TextRange(task.title.length)))
                            }
                            LifetyTextField(value = titleEdit, onValueChange = { titleEdit = it; model.setTitle(it.text) }, enabled = !state.saving,
                                label = { Text("Название задачи") }, modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 4,
                                isError = task.title.isBlank() && state.error != null,
                                supportingText = if (task.title.isBlank() && state.error != null) ({ Text("Добавьте название задачи") }) else null)
                            state.error?.takeUnless { it == timingError || task.title.isBlank() }?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            }
                            TaskDateTimeField("Дедлайн", "Когда дело должно быть готово. Дата без уточнения времени — до 23:59.",
                                task.deadlineAt, !state.saving, task.scheduledAt?.let { localDate(it) } ?: today(), true, model::setDeadlineAt)
                            TaskDateTimeField("Когда заняться", "Необязательное время в расписании. Можно оставить только дедлайн.",
                                task.scheduledAt, !state.saving, task.deadlineAt?.let { localDate(it) } ?: today(), false, model::setScheduledAt)
                            if (planning != null) {
                                TextButton(onClick = { detailsExpanded = !detailsExpanded }, enabled = !state.saving) {
                                    Text(if (detailsExpanded) "Скрыть детали" else state.duration.takeIf { it.isNotBlank() }?.let { "$it мин · детали задачи" } ?: "Добавить длительность, шаги или цель")
                                }
                                AnimatedVisibility(detailsExpanded, enter = ru.lifety.core.ui.LifetyMotion.RevealEnter, exit = ru.lifety.core.ui.LifetyMotion.RevealExit) { TaskDurationFields(state, model) }
                            }
                            if (taskId != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Задача выполнена", style = MaterialTheme.typography.titleMedium)
                                    Text("Отметьте, когда всё готово", style = MaterialTheme.typography.bodySmall)
                                }
                                ru.lifety.core.ui.LifetySwitch(checked = task.isCompleted, onCheckedChange = model::setCompleted, enabled = !state.saving,
                                    modifier = Modifier.semantics { contentDescription = "Задача выполнена" })
                            }
                            Text("Часовой пояс: ${TimeZone.currentSystemDefault().id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Удалить задачу?") },
        text = { Text("Это действие нельзя отменить.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; model.delete() }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } })
}
