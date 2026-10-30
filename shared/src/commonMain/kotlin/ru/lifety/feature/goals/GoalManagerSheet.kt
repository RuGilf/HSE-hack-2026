package ru.lifety.feature.goals

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.json.Json
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import ru.lifety.core.model.Goal
import ru.lifety.core.time.*
import ru.lifety.core.ui.*
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GoalManagerSheet(initialId: String? = null, onDismiss: () -> Unit) {
    val repository = LocalRepositories.current?.goals ?: return
    val state = LocalPlanningState.current
    val retry = LocalPlanningRetry.current
    val goalSaver = Saver<Goal?, String>(save = { it?.let { goal -> Json.encodeToString(goal) } ?: "" }, restore = { if (it.isEmpty()) null else Json.decodeFromString<Goal>(it) })
    var editing by rememberSaveable(initialId, stateSaver = goalSaver) { mutableStateOf(initialId?.let { id -> state.goals.find { it.id == id } }) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val blocked by rememberUpdatedState(busy)
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { it != SheetValue.Hidden || !blocked })
    fun write(goal: Goal, remove: Boolean = false) {
        if (busy) return
        busy = true; error = null
        scope.launch {
            try { if (remove) repository.delete(goal.id) else repository.upsert(goal); editing = null }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Не удалось сохранить цель. Попробуйте ещё раз." }
            finally { busy = false }
        }
    }
    LifetyBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f).imePadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (editing != null) IconButton(onClick = { editing = null }, enabled = !busy) { LifetyIcon(LifetySymbol.Back) }
                Text(if (editing == null) "Глобальные цели" else "Моя цель", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { if (!busy) onDismiss() }, enabled = !busy, modifier = Modifier.semantics { contentDescription = "Закрыть цели" }) { LifetyIcon(LifetySymbol.Close) }
            }
            (error ?: state.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.error != null) TextButton(onClick = retry) { Text("Повторить загрузку") }
            if (busy || state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            val goal = editing
            if (goal != null) key(goal.id) {
                GoalEditor(goal, !busy, onSave = { write(it) }, onDelete = if (state.goals.any { it.id == goal.id }) ({ deleting = true }) else null)
            } else {
                Text("То, к чему ты движешься. Цели попадут в контекст планирования для ChatGPT.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { editing = Goal(Uuid.random().toString(), "") }, enabled = !state.loading && state.error == null, modifier = Modifier.fillMaxWidth()) { Text("Добавить цель") }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (state.goals.isEmpty() && !state.loading && state.error == null) item { LifetyEmptyState("Большое начинается с малого", "Сформулируй результат и первый шаг.", mascotSize = 72.dp) }
                    items(state.goals, key = { it.id }) { item ->
                        Card(onClick = { editing = item }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium)
                                Text(if (item.isCompleted) "Достигнута" else item.targetAt?.let { "К ${formatDate(localDate(it))}" } ?: "Без срока", color = MaterialTheme.colorScheme.primary)
                                if (item.description.isNotBlank()) Text(item.description, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
    if (deleting && editing != null) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Удалить цель?") },
        text = { Text("Задачи останутся, связь с этой целью будет убрана.") },
        confirmButton = { TextButton(onClick = { deleting = false; editing?.let { write(it, true) } }) { Text("Удалить") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Отмена") } })
}

@Composable
private fun ColumnScope.GoalEditor(initial: Goal, enabled: Boolean, onSave: (Goal) -> Unit, onDelete: (() -> Unit)?) {
    var title by rememberSaveable(initial.id) { mutableStateOf(initial.title) }
    var description by rememberSaveable(initial.id) { mutableStateOf(initial.description) }
    var date by rememberSaveable(initial.id) { mutableStateOf(initial.targetAt?.let { localDate(it).toString() }) }
    var completed by rememberSaveable(initial.id) { mutableStateOf(initial.isCompleted) }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(title, { title = it.take(200) }, label = { Text("Чего хочу достичь") }, modifier = Modifier.fillMaxWidth(), enabled = enabled)
        OutlinedTextField(description, { description = it.take(6000) }, label = { Text("Зачем это важно и что нужно сделать") }, modifier = Modifier.fillMaxWidth(), minLines = 3, enabled = enabled)
        if (date == null) OutlinedButton(onClick = { date = today().toString() }, enabled = enabled) { Text("Добавить срок") }
        else {
            LifetyDateField("Достичь к", date!!, { date = it }, enabled = enabled)
            TextButton(onClick = { date = null }, enabled = enabled) { Text("Убрать срок") }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Цель достигнута", modifier = Modifier.weight(1f)); ru.lifety.core.ui.LifetySwitch(completed, { completed = it }, enabled = enabled)
        }
        onDelete?.let { TextButton(onClick = it, enabled = enabled) { Text("Удалить цель", color = MaterialTheme.colorScheme.error) } }
    }
    Button(onClick = { onSave(Goal(initial.id, title.trim(), description.trim(), date?.let { LocalDate.parse(it).atTime(23, 59).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds() }, completed)) },
        enabled = enabled && title.isNotBlank(), modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp).heightIn(min = 48.dp)) { Text("Сохранить цель") }
}
