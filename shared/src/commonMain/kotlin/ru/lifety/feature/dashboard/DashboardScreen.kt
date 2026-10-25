package ru.lifety.feature.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.lifety.app.navigation.AppDestination
import ru.lifety.app.navigation.LocalRootNavigator
import ru.lifety.core.navigation.LocalPlannerNavigation
import ru.lifety.core.ui.*

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    val repositories = LocalRepositories.current
    val navigator = LocalRootNavigator.current
    val planner = LocalPlannerNavigation.current
    var retry by remember { mutableIntStateOf(0) }
    key(repositories, retry) {
        val scope = rememberCoroutineScope()
        val controller = remember { DashboardController(repositories, scope) }
        val state by controller.state.collectAsState()
        val summary = state.summary
        val progress by animateFloatAsState(
            if (summary.today.isEmpty()) 0f else summary.completed.toFloat() / summary.today.size,
            tween(LifetyMotion.Emphasis), label = "dayProgress",
        )
        LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(state.date.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (repositories == null) item { OverviewCard("Нет доступа к хранилищу", "Попробуйте открыть приложение ещё раз.") }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.outlineVariant) }
            state.error?.let { error -> item {
                Text(error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { retry++ }) { Text("Повторить") }
            } }
            if (repositories != null && !state.loading && state.error == null) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Сегодня", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(if (summary.today.isEmpty()) "Нет задач на сегодня" else "${summary.completed} из ${summary.today.size} выполнено", style = MaterialTheme.typography.titleLarge)
                                }
                                TextButton(onClick = { navigator?.navigate(AppDestination.TASKS) }) { Text("План") }
                            }
                            if (summary.today.isNotEmpty()) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.outlineVariant)
                            else TextButton(onClick = { planner?.createTask() }) { Text("Добавить задачу") }
                        }
                    }
                }
                items(summary.today.take(6), key = { "today-${it.id}" }, contentType = { "task" }) { task ->
                    ListItem(
                        modifier = Modifier.clickable { planner?.openTask(task.id) }.animateItem(placementSpec = tween(LifetyMotion.Standard)),
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        headlineContent = { Text(task.title, maxLines = 2) },
                        supportingContent = { Text(if (task.isCompleted) "Выполнено" else task.scheduledAt?.let { dashboardDate(it) } ?: "Срок сегодня") },
                        leadingContent = { LifetyIcon(if (task.isCompleted) LifetySymbol.Check else LifetySymbol.Plan, color = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingContent = { LifetyIcon(LifetySymbol.Chevron) },
                    )
                }
                if (summary.today.size > 6) item {
                    TextButton(onClick = { navigator?.navigate(AppDestination.TASKS) }) { Text("Все задачи · ${summary.today.size}") }
                }
                if (summary.overdue > 0) item { OverviewCard("Просрочено: ${summary.overdue}", "Откройте план, чтобы перенести или завершить задачи.") }
                if (summary.deadlines.isNotEmpty()) {
                    item { Text("Ближайшие дедлайны", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                    items(summary.deadlines, key = { "deadline-${it.id}" }, contentType = { "deadline" }) { task ->
                        ListItem(
                            modifier = Modifier.clickable { planner?.openTask(task.id) },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                            headlineContent = { Text(task.title) }, supportingContent = { Text(dashboardDate(requireNotNull(task.deadlineAt))) },
                            trailingContent = { LifetyIcon(LifetySymbol.Chevron) },
                        )
                    }
                }
                item {
                    Card(onClick = { navigator?.navigate(AppDestination.FINANCE) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SectionLabel("Расходы за месяц", LifetySymbol.Wallet)
                            if (summary.spending.isEmpty()) Text("Пока нет расходов", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            summary.spending.forEach { total -> Text(total.amountMinor?.let { formatMinor(it, total.currency) } ?: "${total.currency}: сумма недоступна", style = MaterialTheme.typography.headlineSmall) }
                            Text("${summary.expenseCount} операций", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    val health = summary.health
                    Card(onClick = { navigator?.navigate(AppDestination.HEALTH) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            SectionLabel("Здоровье", LifetySymbol.Heart)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text("Сон", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(health?.sleepMinutes?.let { "${it / 60} ч ${it % 60} мин" } ?: "Нет данных", style = MaterialTheme.typography.titleMedium)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("Шаги", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(health?.steps?.toString() ?: "Нет данных", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            health?.let { Text(dashboardDate(it.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
            item {
                ListItem(
                    modifier = Modifier.clickable { navigator?.navigate(AppDestination.ASSISTANT) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                    leadingContent = { LifetyMascot(Modifier.size(40.dp), animated = false) },
                    headlineContent = { Text("Спросить Lifety") }, supportingContent = { Text("План дня и разбор расходов") },
                    trailingContent = { LifetyIcon(LifetySymbol.Chevron) },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String, symbol: LifetySymbol) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LifetyIcon(symbol, color = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        LifetyIcon(LifetySymbol.Chevron, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OverviewCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
