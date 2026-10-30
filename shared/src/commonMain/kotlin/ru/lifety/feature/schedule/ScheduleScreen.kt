package ru.lifety.feature.schedule

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import ru.lifety.core.model.ScheduleBlock
import ru.lifety.core.planner.scheduleError
import ru.lifety.core.repository.ScheduleRepository
import ru.lifety.core.ui.*
import ru.lifety.core.time.today
import ru.lifety.core.time.formatDate
import kotlin.uuid.Uuid

private val weekdays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
internal fun scheduleTime(minute: Int) = "${(minute / 60).toString().padStart(2, '0')}:${(minute % 60).toString().padStart(2, '0')}"
internal fun scheduleRepeat(block: ScheduleBlock): String = block.date?.let { formatDate(LocalDate.parse(it)) }
    ?: if (block.weekdays.size == 7) "Каждый день" else block.weekdays.sorted().joinToString(" · ") { weekdays[it - 1] }

@Composable
fun ScheduleScreen(modifier: Modifier = Modifier) {
    val repository = LocalRepositories.current?.schedule
    if (repository == null) { PlaceholderScreen("Расписание недоступно", modifier); return }
    val state = LocalPlanningState.current
    val retry = LocalPlanningRetry.current
    val bottomInset = LocalBottomNavigationInset.current
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    Scaffold(modifier.fillMaxSize(), contentWindowInsets = WindowInsets(0, 0, 0, 0), floatingActionButton = {
        FloatingActionButton(onClick = { creating = true; editorId = Uuid.random().toString() },
            modifier = Modifier.padding(bottom = bottomInset).semantics { contentDescription = "Добавить занятие" }) { LifetyIcon(LifetySymbol.Plus) }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp + bottomInset), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Постоянное расписание", style = MaterialTheme.typography.titleLarge)
                Text("Занятия, работа и встречи. В плане дня они займут свои часы, а задачи можно распределить между ними.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp))
            }
            if (state.loading) item { CircularProgressIndicator() }
            state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error); TextButton(onClick = retry) { Text("Повторить") } } }
            if (!state.loading && state.error == null && state.schedule.isEmpty()) item {
                LifetyEmptyState("Сначала — привычные часы", "Добавьте пары, рабочие часы или разовую встречу. Их не придётся создавать как задачи.",
                    emotion = LifetyEmotion.Calm, actionLabel = "Добавить в расписание",
                    onAction = { creating = true; editorId = Uuid.random().toString() })
            }
            items(state.schedule.sortedWith(compareBy<ScheduleBlock> { it.date != null }.thenBy { it.date }.thenBy { it.startMinute }), key = { it.id }) { block ->
                Card(onClick = { creating = false; editorId = block.id }, modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(scheduleTime(block.startMinute), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
                            Text(scheduleTime(block.endMinute), style = MaterialTheme.typography.bodySmall)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(block.title, style = MaterialTheme.typography.titleMedium)
                            Text(scheduleRepeat(block), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (block.endMinute <= block.startMinute) Text("Окончание на следующий день", style = MaterialTheme.typography.labelSmall)
                        }
                        Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    editorId?.let { id ->
        val initial = if (creating) ScheduleBlock(id, "", 9 * 60, 10 * 60, setOf(1, 2, 3, 4, 5)) else state.schedule.find { it.id == id }
        if (initial != null) key(id) { ScheduleEditorSheet(initial, creating, repository) { editorId = null } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleEditorSheet(initial: ScheduleBlock, isNew: Boolean, repository: ScheduleRepository, onDismiss: () -> Unit) {
    var title by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initial.title, TextRange(initial.title.length))) }
    var start by rememberSaveable { mutableIntStateOf(initial.startMinute) }
    var end by rememberSaveable { mutableIntStateOf(initial.endMinute) }
    var weekly by rememberSaveable { mutableStateOf(initial.date == null) }
    var days by rememberSaveable { mutableStateOf(initial.weekdays.toList()) }
    var date by rememberSaveable { mutableStateOf(initial.date ?: today().toString()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !busy })
    val draft = ScheduleBlock(initial.id, title.text.trim(), start, end, if (weekly) days.toSet() else emptySet(), if (weekly) null else date)
    fun perform(delete: Boolean) {
        if (busy) return
        if (!delete) scheduleError(draft)?.let { error = it; return }
        busy = true; error = null
        scope.launch {
            try {
                if (delete) repository.delete(initial.id) else repository.upsert(draft)
                busy = false
                sheet.hide()
                onDismiss()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = "Не удалось сохранить расписание. Попробуйте ещё раз." }
            finally { busy = false }
        }
    }
    LifetyBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (isNew) "Новое занятие" else "Изменить занятие", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { scope.launch { sheet.hide(); onDismiss() } }, enabled = !busy) { Text("Отмена") }
            }
            LifetyTextField(title, { title = it }, label = { Text("Название") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            LifetySegmentedControl(listOf("По дням недели", "Одна дата"), if (weekly) 0 else 1, { if (!busy) weekly = it == 0 }, Modifier.fillMaxWidth())
            if (weekly) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                weekdays.forEachIndexed { index, label ->
                    val day = index + 1
                    Surface(onClick = { days = if (day in days) days - day else days + day }, enabled = !busy,
                        modifier = Modifier.weight(1f).height(44.dp).semantics { selected = day in days }, shape = MaterialTheme.shapes.small,
                        color = if (day in days) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                        contentColor = if (day in days) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
                        Box(contentAlignment = Alignment.Center) { Text(label, style = MaterialTheme.typography.labelMedium) }
                    }
                }
            } else LifetyDateField("Дата", date, { date = it }, Modifier.fillMaxWidth(), enabled = !busy)
            LifetyTimeField("Начало", start / 60, start % 60, { h, m -> start = h * 60 + m }, Modifier.fillMaxWidth(), enabled = !busy)
            LifetyTimeField("Окончание", end / 60, end % 60, { h, m -> end = h * 60 + m }, Modifier.fillMaxWidth(), enabled = !busy)
            Text(if (end <= start) "Окончание на следующий день. Совпадающее время — полные сутки." else "Занятое время исключается из свободных окон плана дня.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!isNew && weekly) Text("Изменения применятся ко всем повторениям этого занятия.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { perform(false) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Сохранить")
            }
            if (!isNew) TextButton(onClick = { confirmDelete = true }, enabled = !busy, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Удалить занятие", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Удалить занятие?") },
        text = { Text(if (initial.date == null) "Все повторения «${initial.title}» исчезнут из расписания. Задачи сохранятся." else "«${initial.title}» исчезнет из расписания. Задачи сохранятся.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; perform(true) }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } })
}
