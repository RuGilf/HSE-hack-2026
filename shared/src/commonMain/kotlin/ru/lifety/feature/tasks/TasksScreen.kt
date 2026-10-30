package ru.lifety.feature.tasks

import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import ru.lifety.core.model.Task
import ru.lifety.core.navigation.LocalPlannerNavigation
import ru.lifety.core.planner.*
import ru.lifety.core.time.formatDate
import ru.lifety.core.time.localDate
import ru.lifety.core.ui.*
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(modifier: Modifier = Modifier) {
    val repository = LocalRepositories.current?.tasks
    val bottomInset = LocalBottomNavigationInset.current
    if (repository == null) { PlaceholderScreen("Задачи · хранилище не подключено", modifier); return }
    val planner = LocalPlannerNavigation.current
    val model = viewModel { TaskListViewModel(repository) }
    val state by model.state.collectAsState()
    var filterName by rememberSaveable { mutableStateOf(TaskFilter.ACTIVE.name) }
    val filter = TaskFilter.valueOf(filterName)
    var allDates by rememberSaveable { mutableStateOf(false) }
    var timelineMode by rememberSaveable { mutableStateOf(true) }
    var fromHour by rememberSaveable { mutableIntStateOf(8) }
    var untilHour by rememberSaveable { mutableIntStateOf(22) }
    var hoursDialog by remember { mutableStateOf(false) }
    // null follows today across midnight; an explicitly selected date stays fixed.
    var selectedIso by rememberSaveable { mutableStateOf<String?>(null) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var now by remember { mutableStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = Clock.System.now().toEpochMilliseconds() } }
    val zone = TimeZone.currentSystemDefault()
    val today = localDate(now, zone)
    val selected = selectedIso?.let { LocalDate.parse(it) } ?: today
    val plan = remember(state.tasks, selected, now, zone) { dayPlan(state.tasks, selected, now, zone) }
    val planningState = LocalPlanningState.current
    val retryPlanning = LocalPlanningRetry.current
    val details = planningState.plans
    val timeline = remember(state.tasks, details, selected, zone, filter, fromHour, untilHour) {
        dayTimeline(visibleTasks(state.tasks, filter), details, selected, zone, fromHour, untilHour)
    }
    var pendingDelete by remember { mutableStateOf<Task?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.actionError) { state.actionError?.let { snackbar.showSnackbar(it); model.dismissError() } }
    val scope = rememberCoroutineScope()
    fun create() {
        val scheduledAt = try { if (allDates) null else plannerTimestamp(selected, 9, 0, zone) }
        catch (_: IllegalArgumentException) { scope.launch { snackbar.showSnackbar("Этот день недоступен в текущем часовом поясе. Выберите другой.") }; return }
        planner?.createTask(scheduledAt)
    }
    Scaffold(modifier = modifier.fillMaxSize(), contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = bottomInset)) }, floatingActionButton = {
            if (state.tasks.isNotEmpty()) FloatingActionButton(onClick = ::create,
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.large, modifier = Modifier.padding(bottom = bottomInset).semantics { contentDescription = "Добавить дело" }) { LifetyIcon(LifetySymbol.Plus) }
        }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (allDates) "Все дела" else "План на день", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { allDates = !allDates }) { Text(if (allDates) "К плану дня" else "Все даты") }
            }
            if (!allDates) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selectedIso = selected.plus(DatePeriod(days = -1)).toString() },
                    modifier = Modifier.semantics { contentDescription = "Предыдущий день" }) { Text("‹", style = MaterialTheme.typography.headlineSmall) }
                if (isIos) LifetyDateField("День плана", selected.toString(), { selectedIso = it.takeUnless { it == today.toString() } }, Modifier.weight(1f))
                else FilledTonalButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)
                    .semantics { contentDescription = "Выбрать день плана: ${formatDate(selected)}" }) {
                    Text(when (selected) { today -> "Сегодня · ${formatDate(selected)}"; today.plus(DatePeriod(days = 1)) -> "Завтра · ${formatDate(selected)}"; else -> formatDate(selected) })
                }
                IconButton(onClick = { selectedIso = selected.plus(DatePeriod(days = 1)).toString() },
                    modifier = Modifier.semantics { contentDescription = "Следующий день" }) { Text("›", style = MaterialTheme.typography.headlineSmall) }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (allDates) "Всего дел: ${state.tasks.size}" else "${plan.activeCount + plan.completed.size} в плане · ${plan.undated.size} без даты",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (!allDates && selected != today) TextButton(onClick = { selectedIso = null }) { Text("Сегодня") }
            }
            if (isIos) LifetySegmentedControl(TaskFilter.entries.map { it.title }, filter.ordinal, { filterName = TaskFilter.entries[it].name }, Modifier.fillMaxWidth())
            else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskFilter.entries.forEach { item -> FilterChip(selected = filter == item, onClick = { filterName = item.name }, label = { Text(item.title) }) }
            }
            if (!allDates) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = timelineMode, onClick = { timelineMode = !timelineMode }, label = { Text(if (timelineMode) "По времени" else "Списком") })
                Spacer(Modifier.weight(1f))
                if (timelineMode) TextButton(onClick = { hoursDialog = true }) { Text("Окна $fromHour:00–$untilHour:00") }
            }
            when {
                state.loading -> CircularProgressIndicator(Modifier.padding(24.dp))
                state.readError != null -> Column {
                    Text(requireNotNull(state.readError), color = MaterialTheme.colorScheme.error)
                    Button(onClick = model::retry) { Text("Повторить") }
                }
                !allDates && timelineMode && planningState.loading -> CircularProgressIndicator(Modifier.padding(24.dp))
                !allDates && timelineMode && planningState.error != null -> Column {
                    Text(requireNotNull(planningState.error), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = retryPlanning) { Text("Повторить") }
                }
                else -> {
                    val timedIds = if (!allDates && timelineMode) timeline.tasks.mapTo(mutableSetOf()) { it.task.id } else emptySet()
                    val sections = if (allDates) listOf("" to visibleTasks(state.tasks, filter)) else buildList {
                        if (filter != TaskFilter.COMPLETED) {
                            add("Просроченные" to plan.overdue)
                            add("Дедлайны на ${formatDate(selected)}" to plan.due)
                            add("Запланировано" to plan.scheduled)
                            add("Без даты" to plan.undated)
                        }
                        if (filter != TaskFilter.ACTIVE) add("Завершённые" to plan.completed)
                    }.map { (title, rows) -> title to rows.filter { it.id !in timedIds } }.filter { it.second.isNotEmpty() }
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 100.dp + bottomInset)) {
                        if (!allDates && timelineMode) dayTimelineItems(timeline, selected, zone, now, filter != TaskFilter.COMPLETED, state.busyIds,
                            { planner?.openTask(it.id) }, model::toggle, { planner?.createTask(it) })
                        if (sections.all { it.second.isEmpty() } && (allDates || !timelineMode || timeline.tasks.isEmpty() && filter == TaskFilter.COMPLETED)) item {
                            LifetyEmptyState(title = if (filter == TaskFilter.COMPLETED) "Пока нет завершённых дел" else "День свободен",
                                message = if (filter == TaskFilter.COMPLETED) "Здесь будут завершённые дела выбранного периода. Все записи доступны через «Все даты»."
                                    else "Добавь дело и укажи, когда заняться им или к какому сроку закончить.", emotion = LifetyEmotion.Calm,
                                actionLabel = if (filter == TaskFilter.COMPLETED) null else "Добавить дело",
                                onAction = if (filter == TaskFilter.COMPLETED) null else ::create)
                        }
                        sections.forEach { (title, rows) ->
                            taskSection(title, rows, state.busyIds, now, { planner?.openTask(it.id) }, model::toggle, { pendingDelete = it })
                        }
                    }
                }
            }
        }
    }
    if (hoursDialog) TimelineHoursDialog(fromHour, untilHour, onSave = { from, until -> fromHour = from; untilHour = until; hoursDialog = false }, onDismiss = { hoursDialog = false })
    if (showDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = selected.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds())
        DatePickerDialog(onDismissRequest = { showDate = false },
            confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { selectedIso = localDate(it, TimeZone.UTC).takeUnless { it == today }?.toString() }; showDate = false
            }) { Text("Выбрать") } }, dismissButton = { TextButton(onClick = { showDate = false }) { Text("Отмена") } }) { DatePicker(picker) }
    }
    pendingDelete?.let { task -> AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("Удалить задачу?") },
        text = { Text("«${task.title}» будет удалена с этого устройства.") },
        confirmButton = { TextButton(onClick = { model.delete(task.id); pendingDelete = null }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Отмена") } }) }
}

private fun LazyListScope.taskSection(title: String, rows: List<Task>, busyIds: Set<String>, now: Long,
    open: (Task) -> Unit, toggle: (Task) -> Unit, delete: (Task) -> Unit) {
    if (rows.isEmpty()) return
    if (title.isNotEmpty()) item(key = "header:$title", contentType = "section") {
        Text("$title · ${rows.size}", modifier = Modifier.padding(top = 12.dp), style = MaterialTheme.typography.titleSmall,
            color = if (title == "Просроченные") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
    items(rows, key = { "task:${it.id}" }, contentType = { "task" }) { task ->
        Box(Modifier.animateItem(fadeInSpec = tween(LifetyMotion.Quick), placementSpec = tween(LifetyMotion.Standard), fadeOutSpec = tween(LifetyMotion.Quick))) {
            TaskCard(task, task.id in busyIds, onOpen = { open(task) }, onToggle = { toggle(task) }, onDelete = { delete(task) }, now = now)
        }
    }
}
