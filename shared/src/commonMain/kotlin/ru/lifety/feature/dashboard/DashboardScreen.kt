package ru.lifety.feature.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.lifety.core.ui.LocalRepositories

@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    val repositories = LocalRepositories.current
    var retry by remember { mutableIntStateOf(0) }
    // Replacing this composition also cancels the old subscriptions on retry.
    key(repositories, retry) {
        val scope = rememberCoroutineScope()
        val controller = remember { DashboardController(repositories, scope) }
        val state by controller.state.collectAsState()
        val summary = state.summary
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(Modifier.widthIn(max = 840.dp).fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Text("Ваш день, спокойно", style = MaterialTheme.typography.headlineLarge)
                    Text(state.date.ifBlank { "Всё важное в одном месте" }, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (repositories == null) item { OverviewCard("Подключим ваши данные", "Локальное хранилище ещё не подключено. После подключения здесь появятся ваши задачи, расходы и показатели здоровья.") }
                if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                state.error?.let { error -> item {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { retry++ }) { Text("Повторить") }
                } }
                if (repositories != null && !state.loading && state.error == null) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Сегодня", style = MaterialTheme.typography.titleLarge)
                                Text(if (summary.today.isEmpty()) "Есть место для нового плана" else "${summary.completed} из ${summary.today.size} завершено", style = MaterialTheme.typography.headlineSmall)
                                if (summary.today.isNotEmpty()) LinearProgressIndicator(progress = { summary.completed.toFloat() / summary.today.size }, modifier = Modifier.fillMaxWidth())
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                    Column { Text("${summary.completed}", style = MaterialTheme.typography.headlineMedium); Text("выполнено") }
                                    Column { Text("${summary.remaining}", style = MaterialTheme.typography.headlineMedium); Text("осталось") }
                                }
                                if (summary.today.isEmpty()) Text("Добавьте задачу на сегодня в разделе «Задачи». Начните с одного небольшого шага.")
                            }
                        }
                    }
                    items(summary.today.take(6), key = { "today-${it.id}" }) { task ->
                        ListItem(headlineContent = { Text(task.title) }, supportingContent = {
                            Text(if (task.isCompleted) "Готово" else task.scheduledAt?.let { "Запланировано: ${dashboardDate(it)}" } ?: "Срок сегодня")
                        }, leadingContent = { Text(if (task.isCompleted) "✓" else "○", style = MaterialTheme.typography.titleLarge) })
                    }
                    if (summary.today.size > 6) item { Text("Ещё ${summary.today.size - 6} — в разделе «Задачи»", style = MaterialTheme.typography.bodySmall) }
                    item { Text("Ближайшие дедлайны", style = MaterialTheme.typography.titleLarge) }
                    if (summary.overdue > 0) item { OverviewCard("Требуют внимания: ${summary.overdue}", "Есть задачи с прошедшим сроком. Выберите посильный следующий шаг или пересмотрите дату.") }
                    if (summary.deadlines.isEmpty()) item { OverviewCard("Без спешки", "Ближайших незавершённых задач со сроком пока нет.") }
                    items(summary.deadlines, key = { "deadline-${it.id}" }) { task ->
                        ListItem(headlineContent = { Text(task.title) }, supportingContent = { Text(dashboardDate(requireNotNull(task.deadlineAt))) })
                    }
                    item {
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Расходы · текущий месяц", style = MaterialTheme.typography.titleLarge)
                                if (summary.expenseCount == 0) Text("Пока без расходов. Добавьте первую запись в разделе «Финансы».")
                                summary.spending.forEach { total ->
                                    Text(total.amountMinor?.let { formatMinor(it, total.currency) } ?: "${total.currency}: сумма вне диапазона", style = MaterialTheme.typography.headlineSmall)
                                }
                                Text("Остаток бюджета пока недоступен: лимит не задан в хранилище.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item {
                        val health = summary.health
                        OverviewCard("Самочувствие и активность", if (health == null) "Пока нет показателей. В разделе «Здоровье» можно вручную добавить сон и шаги." else buildString {
                            append("Сон: "); append(health.sleepMinutes?.let { "${it / 60} ч ${it % 60} мин" } ?: "нет данных")
                            append("\nШаги: "); append(health.steps?.toString() ?: "нет данных")
                            append("\nЗапись: ${dashboardDate(health.timestamp)}")
                        })
                    }
                }
                item { OverviewCard("Ассистент — когда нужен", "Откройте раздел «Ассистент», выберите данные и посмотрите контекст перед анализом. Без вашего действия ничего не отправляется.") }
            }
        }
    }
}

@Composable
private fun OverviewCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
