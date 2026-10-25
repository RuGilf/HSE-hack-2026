package ru.lifety.feature.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.number
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import ru.lifety.core.navigation.LocalPlannerNavigation
import ru.lifety.core.planner.TaskListViewModel
import ru.lifety.core.time.formatDate
import ru.lifety.core.time.localDate
import ru.lifety.core.time.monthGrid
import ru.lifety.core.time.russianMonths
import ru.lifety.core.time.tasksOnDate
import ru.lifety.core.time.today
import ru.lifety.core.ui.LocalRepositories
import ru.lifety.core.ui.PlaceholderScreen
import ru.lifety.core.ui.TaskCard

@Composable
fun CalendarScreen(modifier: Modifier = Modifier) {
    val repository = LocalRepositories.current?.tasks
    if (repository == null) { PlaceholderScreen("Календарь · хранилище не подключено", modifier); return }
    val planner = LocalPlannerNavigation.current
    val model = viewModel { TaskListViewModel(repository) }
    val state by model.state.collectAsState()
    val zone = TimeZone.currentSystemDefault()
    val currentDay = today(zone)
    var selectedIso by rememberSaveable { mutableStateOf(currentDay.toString()) }
    var monthIso by rememberSaveable { mutableStateOf(LocalDate(currentDay.year, currentDay.month.number, 1).toString()) }
    val selected = LocalDate.parse(selectedIso)
    val month = LocalDate.parse(monthIso)
    val dates = remember(month) { monthGrid(month) }
    val taskCounts = remember(state.tasks, zone) {
        state.tasks.flatMap { task -> listOfNotNull(task.scheduledAt, task.deadlineAt).map { localDate(it, zone) }.distinct() }.groupingBy { it }.eachCount()
    }
    val selectedTasks = remember(state.tasks, selected, zone) { tasksOnDate(state.tasks, selected, zone) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.actionError) { state.actionError?.let { snackbar.showSnackbar(it); model.dismissError() } }
    Scaffold(modifier = modifier.fillMaxSize(), contentWindowInsets = WindowInsets(0, 0, 0, 0), snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { planner?.createTask(selected.atTime(9, 0).toInstant(zone).toEpochMilliseconds()) }) { Text("+  Задача на этот день") } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 16.dp, bottom = 100.dp)) {
            item {
                Text("ВРЕМЯ ДЛЯ ВАЖНОГО", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("Календарь", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 8.dp))
            }
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { monthIso = month.plus(DatePeriod(months = -1)).toString() }, modifier = Modifier.semantics { contentDescription = "Предыдущий месяц" }) { Text("‹") }
                            Text("${russianMonths[month.month.number - 1]} ${month.year}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = { monthIso = month.plus(DatePeriod(months = 1)).toString() }, modifier = Modifier.semantics { contentDescription = "Следующий месяц" }) { Text("›") }
                        }
                        Row(Modifier.fillMaxWidth()) {
                            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach { Text(it, modifier = Modifier.weight(1f).padding(vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.labelSmall) }
                        }
                        dates.chunked(7).forEach { week ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                week.forEach { day ->
                                    val chosen = day == selected
                                    Surface(onClick = { selectedIso = day.toString(); monthIso = LocalDate(day.year, day.month.number, 1).toString() },
                                        modifier = Modifier.weight(1f).height(48.dp).semantics { contentDescription = "${formatDate(day)}, задач: ${taskCounts[day] ?: 0}" },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                                        border = if (day == currentDay && !chosen) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                            Text(day.day.toString(), style = MaterialTheme.typography.bodyMedium,
                                                color = if (chosen) MaterialTheme.colorScheme.onPrimary else if (day.month.number == month.month.number) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline)
                                            Text(if ((taskCounts[day] ?: 0) > 0) "•" else " ", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                        TextButton(onClick = { selectedIso = currentDay.toString(); monthIso = LocalDate(currentDay.year, currentDay.month.number, 1).toString() }, modifier = Modifier.align(Alignment.End)) { Text("Сегодня") }
                    }
                }
            }
            item {
                Text(formatDate(selected), style = MaterialTheme.typography.titleLarge)
                Text("План и дедлайны · ${selectedTasks.size} задач", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                state.loading -> item { androidx.compose.material3.CircularProgressIndicator() }
                state.readError != null -> item {
                    Text(requireNotNull(state.readError), color = MaterialTheme.colorScheme.error)
                    Button(onClick = model::retry) { Text("Повторить") }
                }
                selectedTasks.isEmpty() -> item { Text("На этот день задач нет. Оставьте время для себя или добавьте новый план.", modifier = Modifier.padding(vertical = 16.dp)) }
                else -> items(selectedTasks, key = { it.id }) { task ->
                    TaskCard(task, task.id in state.busyIds, onOpen = { planner?.openTask(task.id) }, onToggle = { model.toggle(task) })
                }
            }
        }
    }
}
