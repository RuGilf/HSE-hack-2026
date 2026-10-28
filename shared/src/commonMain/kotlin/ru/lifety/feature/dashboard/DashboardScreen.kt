package ru.lifety.feature.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.lifety.app.navigation.AppDestination
import ru.lifety.app.navigation.LocalRootNavigator
import ru.lifety.core.navigation.LocalPlannerNavigation
import ru.lifety.core.planner.formatPlannerTime
import ru.lifety.core.time.formatDate
import ru.lifety.core.time.localDate
import ru.lifety.core.ui.*
import ru.lifety.feature.goals.GoalManagerSheet

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    val repositories = LocalRepositories.current
    val navigator = LocalRootNavigator.current
    val planner = LocalPlannerNavigation.current
    val planning = LocalPlanningState.current
    val retryPlanning = LocalPlanningRetry.current
    var retry by remember { mutableIntStateOf(0) }
    var goalsOpen by rememberSaveable { mutableStateOf(false) }
    var goalId by rememberSaveable { mutableStateOf<String?>(null) }
    key(repositories, retry) {
        val scope = rememberCoroutineScope()
        val controller = remember { DashboardController(repositories, scope) }
        val state by controller.state.collectAsState()
        val summary = state.summary
        val progress by animateFloatAsState(if (summary.today.isEmpty()) 0f else summary.completed.toFloat() / summary.today.size,
            tween(LifetyMotion.Emphasis), label = "dayProgress")
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp + LocalBottomNavigationInset.current),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                item {
                    Text(state.date.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Сегодня", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 6.dp))
                }
                if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (repositories == null) item { Text("Хранилище не подключено", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error); TextButton(onClick = { retry++ }) { Text("Повторить") } } }
                if (repositories != null && !state.loading && state.error == null) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CompactMetric("Дела", summary.remaining.toString(), "${summary.completed} выполнено", LifetySymbol.Plan,
                                Modifier.weight(1f), onClick = { navigator?.navigate(AppDestination.TASKS) }) {
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), trackColor = MaterialTheme.colorScheme.outlineVariant)
                            }
                            val next = summary.deadlines.firstOrNull()
                            CompactMetric(if (summary.overdue > 0) "Просрочено" else "Ближайший срок",
                                if (summary.overdue > 0) summary.overdue.toString() else next?.deadlineAt?.let { formatDate(localDate(it)) } ?: "—",
                                if (summary.overdue > 0) "Нужно перепланировать" else next?.title ?: "Дедлайнов пока нет", LifetySymbol.Calendar,
                                Modifier.weight(1f), onClick = { if (next != null && summary.overdue == 0) planner?.openTask(next.id) else navigator?.navigate(AppDestination.TASKS) })
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Глобальные цели", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { goalId = null; goalsOpen = true }, enabled = repositories?.goals != null) { Text("Все") }
                    }
                }
                planning.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error); TextButton(onClick = retryPlanning) { Text("Повторить") } } }
                if (planning.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                else if (planning.error == null && repositories?.goals != null) {
                    val goals = planning.goals.filterNot { it.isCompleted }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (goals.isEmpty()) item {
                                OutlinedCard(onClick = { goalId = null; goalsOpen = true }, modifier = Modifier.width(252.dp)) {
                                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        LifetyIcon(LifetySymbol.Plus, color = MaterialTheme.colorScheme.primary)
                                        Text(if (planning.goals.isEmpty()) "К чему ты идёшь?" else "Следующая большая цель", style = MaterialTheme.typography.titleMedium)
                                        Text("Добавь цель и преврати её в план", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            items(goals, key = { it.id }) { goal ->
                                Card(onClick = { goalId = goal.id; goalsOpen = true }, modifier = Modifier.width(252.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Text(goal.targetAt?.let { "К ${formatDate(localDate(it))}" } ?: "Большая цель", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        Text(goal.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        if (goal.description.isNotBlank()) Text(goal.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        val linkedIds = planning.plans.values.filter { it.goalId == goal.id }.map { it.taskId }.toSet()
                                        if (linkedIds.isNotEmpty()) {
                                            val linked = state.allTasks.filter { it.id in linkedIds }
                                            Text("${linked.count { it.isCompleted }} из ${linked.size} шагов", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (linked.isNotEmpty()) LinearProgressIndicator(progress = { linked.count { it.isCompleted }.toFloat() / linked.size }, modifier = Modifier.fillMaxWidth())
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (repositories != null && !state.loading && state.error == null) {
                    item {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("План на день", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            IconButton(onClick = { planner?.createTask() }, modifier = Modifier.semantics { contentDescription = "Добавить задачу" }) { LifetyIcon(LifetySymbol.Plus, color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                    if (summary.today.isEmpty()) item { Text("Пока ничего не назначено. Оставь пространство для важного.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(summary.today.take(4), key = { "today-${it.id}" }) { task ->
                        val details = planning.plans[task.id]
                        Row(Modifier.fillMaxWidth().clickable { planner?.openTask(task.id) }.padding(vertical = 4.dp).animateItem(placementSpec = tween(LifetyMotion.Standard)),
                            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                            LifetyIcon(if (task.isCompleted) LifetySymbol.Check else LifetySymbol.Plan, Modifier.padding(top = 2.dp),
                                color = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(if (task.isCompleted) "Готово" else listOfNotNull(task.scheduledAt?.let { formatPlannerTime(it) }, details?.let { "${it.durationMinutes} мин" },
                                    task.deadlineAt?.let { "срок ${formatPlannerTime(it)}" }).joinToString(" · ").ifEmpty { "Без времени" },
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                details?.let { Text(it.approach, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            LifetyIcon(LifetySymbol.Chevron, Modifier.size(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (summary.today.size > 4) item { TextButton(onClick = { navigator?.navigate(AppDestination.TASKS) }) { Text("Ещё ${summary.today.size - 4} задач в плане") } }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val spending = summary.spending.firstOrNull()
                            CompactMetric("Финансы", spending?.amountMinor?.let { formatMinor(it, spending.currency) } ?: if (spending == null) "Нет трат" else "Открыть",
                                if (summary.spending.size > 1) "Ещё ${summary.spending.size - 1} валют · за месяц" else "${summary.expenseCount} операций за месяц", LifetySymbol.Wallet,
                                Modifier.weight(1f), onClick = { navigator?.navigate(AppDestination.FINANCE) })
                            val health = summary.health
                            CompactMetric("Здоровье", health?.sleepMinutes?.let { "${it / 60} ч ${it % 60} мин" } ?: "Нет сна",
                                health?.steps?.let { "$it шагов" } ?: "Шаги не записаны", LifetySymbol.Heart,
                                Modifier.weight(1f), onClick = { navigator?.navigate(AppDestination.HEALTH) }) {
                                health?.let { Text("Замер ${formatDate(localDate(it.timestamp))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
        }
    }
    if (goalsOpen) GoalManagerSheet(goalId, onDismiss = { goalsOpen = false })
}

@Composable
private fun CompactMetric(title: String, value: String, subtitle: String, symbol: LifetySymbol, modifier: Modifier,
    onClick: () -> Unit, extra: @Composable ColumnScope.() -> Unit = {}) {
    Card(onClick = onClick, modifier = modifier.heightIn(min = 152.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LifetyIcon(symbol, Modifier.size(18.dp), color = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            extra()
        }
    }
}
