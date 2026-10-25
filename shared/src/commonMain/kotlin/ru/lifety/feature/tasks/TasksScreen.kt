package ru.lifety.feature.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.lifety.core.model.Task
import ru.lifety.core.navigation.LocalPlannerNavigation
import ru.lifety.core.planner.TaskFilter
import ru.lifety.core.planner.TaskListViewModel
import ru.lifety.core.planner.visibleTasks
import ru.lifety.core.ui.LifetyEmptyState
import ru.lifety.core.ui.LifetyEmotion
import ru.lifety.core.ui.LifetyIcon
import ru.lifety.core.ui.LifetySymbol
import ru.lifety.core.ui.LocalRepositories
import ru.lifety.core.ui.PlaceholderScreen
import ru.lifety.core.ui.TaskCard

@Composable
fun TasksScreen(modifier: Modifier = Modifier) {
    val repository = LocalRepositories.current?.tasks
    if (repository == null) {
        PlaceholderScreen("Задачи · хранилище не подключено", modifier)
        return
    }
    val planner = LocalPlannerNavigation.current
    val model = viewModel { TaskListViewModel(repository) }
    val state by model.state.collectAsState()
    var filterName by rememberSaveable { mutableStateOf(TaskFilter.ACTIVE.name) }
    val filter = TaskFilter.valueOf(filterName)
    var pendingDelete by remember { mutableStateOf<Task?>(null) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.actionError) {
        state.actionError?.let { snackbar.showSnackbar(it); model.dismissError() }
    }
    Scaffold(modifier = modifier.fillMaxSize(), contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            if (state.tasks.isNotEmpty()) FloatingActionButton(onClick = { planner?.createTask() },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier.semantics { contentDescription = "Добавить задачу" }) { LifetyIcon(LifetySymbol.Plus) }
        }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Твои задачи", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${state.tasks.count { !it.isCompleted }} активных · ${state.tasks.count { it.isCompleted }} готово",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskFilter.entries.forEach { item -> FilterChip(selected = filter == item, onClick = { filterName = item.name }, label = { Text(item.title) }) }
            }
            when {
                state.loading -> CircularProgressIndicator(Modifier.padding(24.dp))
                state.readError != null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(requireNotNull(state.readError), color = MaterialTheme.colorScheme.error)
                    Button(onClick = model::retry) { Text("Повторить") }
                }
                else -> {
                    val tasks = visibleTasks(state.tasks, filter)
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 100.dp)) {
                        if (tasks.isEmpty()) item {
                            LifetyEmptyState(
                                title = if (filter == TaskFilter.COMPLETED) "Прогресс начинается с малого" else "Пока здесь пусто",
                                message = if (filter == TaskFilter.COMPLETED) "Завершённые задачи появятся здесь. Lifety рядом, шаг за шагом." else "Добавь первую задачу, и Lifety поможет держать день в одном контексте.",
                                emotion = if (filter == TaskFilter.COMPLETED) LifetyEmotion.Calm else LifetyEmotion.Happy,
                                actionLabel = if (filter == TaskFilter.COMPLETED) null else "Добавить задачу",
                                onAction = if (filter == TaskFilter.COMPLETED) null else ({ planner?.createTask() }),
                            )
                        }
                        items(tasks, key = { it.id }) { task ->
                            TaskCard(task, task.id in state.busyIds, onOpen = { planner?.openTask(task.id) },
                                onToggle = { model.toggle(task) }, onDelete = { pendingDelete = task })
                        }
                    }
                }
            }
        }
    }
    pendingDelete?.let { task ->
        AlertDialog(onDismissRequest = { pendingDelete = null }, title = { Text("Удалить задачу?") },
            text = { Text("«${task.title}» будет удалена с этого устройства.") },
            confirmButton = { TextButton(onClick = { model.delete(task.id); pendingDelete = null }) { Text("Удалить", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Отмена") } })
    }
}
