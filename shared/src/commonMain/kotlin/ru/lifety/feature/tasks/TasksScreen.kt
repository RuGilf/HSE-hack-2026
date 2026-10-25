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
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.lifety.core.model.Task
import ru.lifety.core.navigation.LocalPlannerNavigation
import ru.lifety.core.planner.TaskFilter
import ru.lifety.core.planner.TaskListViewModel
import ru.lifety.core.planner.visibleTasks
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
            ExtendedFloatingActionButton(onClick = { planner?.createTask() }, expanded = true,
                icon = { Text("+", style = MaterialTheme.typography.headlineSmall) }, text = { Text("Новая задача") })
        }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("ВАШ ПЛАН", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp))
            Text("Задачи", style = MaterialTheme.typography.headlineLarge)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Освободите голову для важного", style = MaterialTheme.typography.titleMedium)
                    Text("${state.tasks.count { !it.isCompleted }} активных · ${state.tasks.count { it.isCompleted }} завершено", style = MaterialTheme.typography.bodyMedium)
                }
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
                            Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (filter == TaskFilter.COMPLETED) "Завершённые задачи появятся здесь" else "Здесь пока свободно", style = MaterialTheme.typography.titleLarge)
                                Text(if (filter == TaskFilter.COMPLETED) "Отмечайте сделанное — и наблюдайте за прогрессом." else "Добавьте первую задачу и выберите удобное время.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
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
