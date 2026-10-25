package ru.lifety.feature.tasks

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import ru.lifety.core.ui.LifetyMotion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import kotlinx.datetime.toLocalDateTime
import ru.lifety.core.time.changeDate
import ru.lifety.core.time.changeTime
import ru.lifety.core.time.datePickerMillis
import ru.lifety.core.time.formatDateTime
import ru.lifety.core.time.isLocalTimePreserved
import ru.lifety.core.ui.LifetyIcon
import ru.lifety.core.ui.LifetySymbol
import ru.lifety.core.ui.LocalRepositories
import ru.lifety.core.ui.PlaceholderScreen
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorScreen(taskId: String?, initialScheduledAt: Long?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val repository = LocalRepositories.current?.tasks
    if (repository == null) { PlaceholderScreen("Хранилище не подключено", modifier); return }
    val model = viewModel { TaskEditorViewModel(repository, taskId, initialScheduledAt) }
    val state by model.state.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    val saving by rememberUpdatedState(state.saving)
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || !saving || closing })
    LaunchedEffect(model) { model.finished.collect { closing = true; sheet.hide(); onClose() } }
    val close: () -> Unit = { if (!closing && !state.saving) scope.launch { closing = true; sheet.hide(); onClose() } }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val sheetHeight = maxHeight * 0.92f
        ModalBottomSheet(onDismissRequest = { if (!state.saving) onClose() }, sheetState = sheet,
            containerColor = MaterialTheme.colorScheme.background) {
    Scaffold(modifier = Modifier.fillMaxWidth().height(sheetHeight), contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(title = { Text(if (taskId == null) "Новая задача" else "Редактирование", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = close, enabled = !state.saving && !closing, modifier = Modifier.semantics { contentDescription = "Закрыть редактор" }) { LifetyIcon(LifetySymbol.Close) } },
                windowInsets = WindowInsets(0, 0, 0, 0),
                actions = { if (taskId != null && state.task != null) TextButton(onClick = { confirmDelete = true }, enabled = !state.saving) { Text("Удалить", color = MaterialTheme.colorScheme.error) } })
        },
        bottomBar = {
            if (state.task != null) Surface(color = MaterialTheme.colorScheme.background) {
                Button(onClick = model::save, enabled = !state.saving, modifier = Modifier.fillMaxWidth().imePadding().padding(16.dp).height(48.dp)) {
                    if (state.saving) CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp).size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Text(if (state.saving) "Сохраняем…" else if (taskId == null) "Создать задачу" else "Сохранить изменения")
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
                    OutlinedTextField(value = task.title, onValueChange = model::setTitle, enabled = !state.saving,
                        label = { Text("Название задачи") }, modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 4,
                        isError = task.title.isBlank() && state.error != null)
                    DateTimeField("Дата и время выполнения", task.scheduledAt, !state.saving, model::setScheduledAt)
                    DateTimeField("Дедлайн", task.deadlineAt, !state.saving, model::setDeadlineAt)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Задача выполнена", style = MaterialTheme.typography.titleMedium)
                            Text("Отметьте, когда всё готово", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = task.isCompleted, onCheckedChange = model::setCompleted, enabled = !state.saving,
                            modifier = Modifier.semantics { contentDescription = "Задача выполнена" })
                    }
                    Text("Часовой пояс: ${TimeZone.currentSystemDefault().id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeField(label: String, value: Long?, enabled: Boolean, onChange: (Long?) -> Unit) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val zone = TimeZone.currentSystemDefault()
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.animateContentSize(tween(LifetyMotion.Standard)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Switch(checked = value != null, enabled = enabled, modifier = Modifier.semantics { contentDescription = label },
                    onCheckedChange = { checked -> error = null; onChange(if (checked) Clock.System.now().toEpochMilliseconds() else null) })
            }
            if (value != null) {
                Text(formatDateTime(value), style = MaterialTheme.typography.bodyLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showDate = true }, enabled = enabled) { Text("Выбрать дату") }
                    TextButton(onClick = { showTime = true }, enabled = enabled) { Text("Время") }
                }
            } else Text("Не задано", color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (showDate && value != null) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = datePickerMillis(value, zone))
        DatePickerDialog(onDismissRequest = { showDate = false },
            confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let {
                    val original = Instant.fromEpochMilliseconds(value).toLocalDateTime(zone)
                    val changed = changeDate(value, it, zone)
                    if (isLocalTimePreserved(changed, original.hour, original.minute, zone)) { onChange(changed); error = null }
                    else error = "Такого времени нет в этом часовом поясе. Выберите другое время."
                }
                showDate = false
            }) { Text("Готово") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Отмена") } }) { DatePicker(picker) }
    }
    if (showTime && value != null) {
        val time = Instant.fromEpochMilliseconds(value).toLocalDateTime(zone)
        val picker = rememberTimePickerState(initialHour = time.hour, initialMinute = time.minute, is24Hour = true)
        AlertDialog(onDismissRequest = { showTime = false }, title = { Text("Выберите время") }, text = { TimeInput(picker) },
            confirmButton = { TextButton(onClick = {
                val changed = changeTime(value, picker.hour, picker.minute, zone)
                if (isLocalTimePreserved(changed, picker.hour, picker.minute, zone)) { onChange(changed); error = null }
                else error = "Такого времени нет в этом часовом поясе. Выберите другое время."
                showTime = false
            }) { Text("Готово") } }, dismissButton = { TextButton(onClick = { showTime = false }) { Text("Отмена") } })
    }
}
