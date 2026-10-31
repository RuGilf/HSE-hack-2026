@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package ru.lifety.feature.health

import ru.lifety.core.ui.LifetyTextField

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.datetime.LocalDate
import ru.lifety.core.model.*
import ru.lifety.core.model.HealthMetric as CustomMetric
import ru.lifety.core.time.*
import ru.lifety.core.ui.*
import kotlin.time.Clock
import kotlin.uuid.Uuid

@Serializable
private sealed interface HealthPanel {
    @Serializable data object Add : HealthPanel
    @Serializable data object Sources : HealthPanel
    @Serializable data object Archive : HealthPanel
    @Serializable data class Builtin(val sleep: Boolean) : HealthPanel
    @Serializable data class Manual(val sleep: Boolean, val date: String? = null, val fromDetail: Boolean = false) : HealthPanel
    @Serializable data class Metric(val metric: CustomMetric, val adding: Boolean = false) : HealthPanel
    @Serializable data class Editor(val metric: CustomMetric) : HealthPanel
}

private val healthPanelSaver = Saver<HealthPanel?, String>(
    save = { it?.let { panel -> Json.encodeToString<HealthPanel>(panel) } ?: "" },
    restore = { value -> if (value.isEmpty()) null else runCatching { Json.decodeFromString<HealthPanel>(value) }.getOrNull() },
)

@Composable fun HealthScreen(modifier: Modifier = Modifier) {
    val repositories = LocalRepositories.current
    val repository = repositories?.health
    if (repository == null) { PlaceholderScreen("Хранилище здоровья недоступно", modifier); return }
    val metricRepo = repositories.healthMetrics
    val scope = rememberCoroutineScope()
    val controller = remember(repository, scope) { HealthController(repository, scope) }
    val state by controller.state.collectAsState()
    var metrics by remember { mutableStateOf(HealthMetricSnapshot()) }
    var metricError by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(metricRepo, retry) { metricRepo?.observe()?.catch { metricError = "Не удалось загрузить показатели" }?.collect { metrics = it; metricError = null } }
    var panel by rememberSaveable(stateSaver = healthPanelSaver) { mutableStateOf<HealthPanel?>(null) }
    var writing by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    val busy = writing || state.saving || importing
    var now by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = Clock.System.now().toEpochMilliseconds() } }
    val today = localDate(now)
    val preparedHistory by produceState<List<HealthSnapshot>?>(null, state.history, now) {
        value = withContext(Dispatchers.Default) { state.history.filter { it.timestamp <= now }.sortedBy { it.timestamp } }
    }
    val history = preparedHistory.orEmpty()
    val sleep = history.lastOrNull { it.sleepMinutes != null }
    val steps = history.lastOrNull { it.steps != null }
    val inset = LocalBottomNavigationInset.current
    val snackbar = remember { SnackbarHostState() }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !writing && !controller.state.value.saving && !importing })
    fun close() { if (!writing && !controller.state.value.saving && !importing) scope.launch { sheet.hide(); if (!sheet.isVisible) panel = null } }
    fun write(action: suspend () -> Unit, finished: () -> Unit) {
        if (busy) return
        writing = true; metricError = null
        scope.launch {
            try { action(); metrics = metricRepo?.snapshot() ?: metrics; writing = false; finished() }
            catch (c: CancellationException) { throw c }
            catch (e: Exception) { metricError = e.message ?: "Не удалось сохранить запись" }
            finally { writing = false }
        }
    }
    LaunchedEffect(state.saved) {
        if (state.saved) { sheet.hide(); panel = null; snackbar.showSnackbar("Запись сохранена") }
    }
    Scaffold(modifier.fillMaxSize(), contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = inset)) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { controller.clearSaveStatus(); metricError = null; panel = HealthPanel.Add },
                modifier = Modifier.padding(bottom = inset).semantics { contentDescription = "Добавить запись здоровья" }, icon = { LifetyIcon(LifetySymbol.Plus) }, text = { Text("Запись") })
        }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 104.dp + inset),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Обзор", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    Text(formatDate(today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.loading || preparedHistory == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    SummaryMetricCard("Сон", sleep?.sleepMinutes?.let(::sleepLabel) ?: "Нет записей", sleep?.timestamp, LifetySymbol.Sleep, Modifier.weight(1f)) { panel = HealthPanel.Builtin(true) }
                    SummaryMetricCard("Шаги", steps?.steps?.toString() ?: "Нет записей", steps?.timestamp, LifetySymbol.Steps, Modifier.weight(1f)) { panel = HealthPanel.Builtin(false) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Мои показатели", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { metricError = null; panel = HealthPanel.Editor(CustomMetric(Uuid.random().toString(), "")) }, enabled = metricRepo != null) { Text("Добавить") }
                }
                val active = metrics.metrics.filterNot { it.archived }
                if (active.isEmpty()) Text("Что ещё хотите отслеживать? Например, настроение, воду или самочувствие.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column {
                        active.forEachIndexed { index, metric ->
                            val last = metrics.entries.lastOrNull { it.metricId == metric.id && it.timestamp <= now }
                            HealthNavigationRow(metric.name, last?.let { "${metricValueLabel(metric, it.value)} · ${formatDate(localDate(it.timestamp))}" } ?: "Добавьте первую запись") { panel = HealthPanel.Metric(metric) }
                            if (index < active.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
            item {
                Text("Источники данных", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    HealthNavigationRow(if (isIos) "Apple Health" else "Health Connect", "Сон, шаги и ещё 4 показателя") { panel = HealthPanel.Sources }
                }
                Text("Обновление по вашему действию", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
            }
            if (metrics.metrics.any { it.archived }) item { TextButton(onClick = { panel = HealthPanel.Archive }) { Text("Архив показателей") } }
            (metricError ?: state.error)?.let { message -> item {
                Text(message, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { retry++; controller.reload() }) { Text("Повторить загрузку") }
            } }
        }
    }
    panel?.let { current ->
        val title = when (current) {
            HealthPanel.Add -> "Новая запись"; HealthPanel.Archive -> "Архив"; HealthPanel.Sources -> if (isIos) "Apple Health" else "Health Connect"
            is HealthPanel.Builtin -> if (current.sleep) "Сон" else "Шаги"
            is HealthPanel.Manual -> if (current.sleep) "Записать сон" else "Записать шаги"
            is HealthPanel.Metric -> current.metric.name
            is HealthPanel.Editor -> if (current.metric.name.isBlank()) "Новый показатель" else "Изменить показатель"
        }
        LifetyBottomSheet(onDismissRequest = { if (!busy) panel = null }, sheetState = sheet) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().heightIn(max = maxHeight * .92f).imePadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val back: HealthPanel? = when (current) {
                        is HealthPanel.Manual -> if (current.fromDetail) HealthPanel.Builtin(current.sleep) else HealthPanel.Add
                        is HealthPanel.Editor -> if (current.metric.name.isBlank()) HealthPanel.Add else HealthPanel.Metric(current.metric)
                        is HealthPanel.Metric -> if (current.adding) HealthPanel.Add else null
                        else -> null
                    }
                    if (back != null) IconButton(onClick = { metricError = null; panel = back }, enabled = !busy, modifier = Modifier.semantics { contentDescription = "Назад" }) { LifetyIcon(LifetySymbol.Back) }
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = ::close, enabled = !busy, modifier = Modifier.semantics { contentDescription = "Закрыть здоровье" }) { LifetyIcon(LifetySymbol.Close) }
                }
                HorizontalDivider()
                key(current) {
                    when (current) {
                        HealthPanel.Add -> LazyColumn(contentPadding = PaddingValues(20.dp)) {
                            item { Text("Что записать?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            item { HealthNavigationRow("Сон", "Продолжительность за день") { controller.clearSaveStatus(); panel = HealthPanel.Manual(true) } }
                            item { HealthNavigationRow("Шаги", "Количество за день") { controller.clearSaveStatus(); panel = HealthPanel.Manual(false) } }
                            items(metrics.metrics.filterNot { it.archived }, key = { it.id }) { metric -> HealthNavigationRow(metric.name, metric.unit.ifBlank { when (metric.kind) { MetricKind.TEXT -> "Заметка"; MetricKind.BOOLEAN -> "Да или нет"; else -> "Число" } }) { panel = HealthPanel.Metric(metric, true) } }
                            item { HorizontalDivider(); TextButton(onClick = { panel = HealthPanel.Editor(CustomMetric(Uuid.random().toString(), "")) }, enabled = metricRepo != null) { Text("Создать свой показатель") } }
                        }
                        HealthPanel.Archive -> LazyColumn(contentPadding = PaddingValues(20.dp)) {
                            items(metrics.metrics.filter { it.archived }, key = { it.id }) { metric -> HealthNavigationRow(metric.name, "Записи сохранены") { panel = HealthPanel.Metric(metric) } }
                        }
                        HealthPanel.Sources -> DeviceMetricsContent(repository, metricRepo, onBusyChanged = { importing = it }, onSaved = { close() })
                        is HealthPanel.Builtin -> BuiltinHealthContent(current.sleep, history, today, onAdd = { selected -> controller.clearSaveStatus(); panel = HealthPanel.Manual(current.sleep, selected.toString(), true) })
                        is HealthPanel.Manual -> ManualHealthContent(current.sleep, current.date ?: today.toString(), state, controller)
                        is HealthPanel.Editor -> MetricEditorContent(current.metric, metrics.entries.any { it.metricId == current.metric.id }, writing, metricError, ::close) { value ->
                            write({ requireNotNull(metricRepo).saveMetric(value) }) { if (value.archived) close() else panel = HealthPanel.Metric(value, current.metric.name.isBlank()) }
                        }
                        is HealthPanel.Metric -> {
                            val metric = metrics.metrics.find { it.id == current.metric.id } ?: current.metric
                            MetricDetailContent(metric, metrics.entries.filter { it.metricId == metric.id }, writing, metricError, ::close, current.adding,
                                onEdit = { metricError = null; panel = HealthPanel.Editor(metric) },
                                onSave = { entry -> write({ requireNotNull(metricRepo).saveEntry(entry) }, ::close) },
                                onDelete = { entry -> write({ requireNotNull(metricRepo).deleteEntry(entry.id) }) {} })
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable private fun SummaryMetricCard(title: String, value: String, timestamp: Long?, symbol: LifetySymbol, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) { LifetyIcon(symbol, Modifier.size(20.dp), MaterialTheme.colorScheme.primary); Text(title, style = MaterialTheme.typography.titleSmall) }
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 2)
            Text(timestamp?.let { formatDate(localDate(it)) } ?: "Начните отслеживать", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable internal fun HealthNavigationRow(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(modifier = Modifier.clickable(onClick = onClick), colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        headlineContent = { Text(title) }, supportingContent = { Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = { LifetyIcon(LifetySymbol.Chevron, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant) })
}

@Composable private fun BuiltinHealthContent(sleep: Boolean, history: List<HealthSnapshot>, today: LocalDate, onAdd: (LocalDate) -> Unit) {
    var periodName by rememberSaveable { mutableStateOf(HealthPeriod.WEEK.name) }
    val period = HealthPeriod.valueOf(periodName)
    val calculationKey = remember(history, period, today) { Any() }
    val timelineState = key(calculationKey) {
        produceState<HealthTimeline?>(null) { value = withContext(Dispatchers.Default) { healthTimeline(history, period, today) } }
    }
    val timeline = timelineState.value
    if (timeline == null) { LinearProgressIndicator(Modifier.fillMaxWidth().padding(20.dp)); return }
    val recordedDays = remember(timeline, sleep) { timeline.days.filter { if (sleep) it.sleepMinutes != null else it.steps != null }.asReversed() }
    var selectedDate by remember { mutableStateOf("") }
    val selected = timeline.days.indexOfFirst { it.date.toString() == selectedDate }.takeIf { it >= 0 } ?: timeline.days.lastIndex
    val day = timeline.days.getOrNull(selected)
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { LifetySegmentedControl(HealthPeriod.entries.map { it.label }, HealthPeriod.entries.indexOf(period), { periodName = HealthPeriod.entries[it].name; selectedDate = "" }, Modifier.fillMaxWidth()) }
        item { HealthChart(if (sleep) "Сон" else "Шаги", timeline, selected, sleep) { selectedDate = timeline.days[it].date.toString() } }
        if (day != null) item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { selectedDate = timeline.days[selected - 1].date.toString() }, enabled = selected > 0) { Text("Назад") }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(formatDate(day.date), style = MaterialTheme.typography.labelMedium); Text(if (sleep) sleepLabel(day.sleepMinutes) else day.steps?.toString() ?: "Нет данных") }
                TextButton(onClick = { selectedDate = timeline.days[selected + 1].date.toString() }, enabled = selected < timeline.days.lastIndex) { Text("Вперёд") }
            }
        }
        item { Button(onClick = { onAdd(day?.date ?: today) }, modifier = Modifier.fillMaxWidth()) { Text("Добавить запись") } }
        item { Text("История", style = MaterialTheme.typography.titleMedium) }
        items(recordedDays, key = { it.date.toString() }) { row ->
            ListItem(headlineContent = { Text(if (sleep) sleepLabel(row.sleepMinutes) else "${row.steps} шагов") }, supportingContent = { Text(formatDate(row.date)) }, modifier = Modifier.clickable { selectedDate = row.date.toString() })
        }
    }
}

@Composable private fun ManualHealthContent(sleep: Boolean, initialDate: String, state: HealthUiState, controller: HealthController) {
    var date by rememberSaveable { mutableStateOf(initialDate) }
    var hours by rememberSaveable { mutableStateOf("") }; var minutes by rememberSaveable { mutableStateOf("") }; var steps by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (sleep) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LifetyTextField(hours, { hours = it.take(2) }, Modifier.weight(1f), label = { Text("Часы") }, singleLine = true, enabled = !state.saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            LifetyTextField(minutes, { minutes = it.take(2) }, Modifier.weight(1f), label = { Text("Минуты") }, singleLine = true, enabled = !state.saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        } else LifetyTextField(steps, { steps = it.take(8) }, Modifier.fillMaxWidth(), label = { Text("Шаги за день") }, singleLine = true, enabled = !state.saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        LifetyDateField("Дата", date, { date = it }, enabled = !state.saving)
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(onClick = { controller.save(hours, minutes, steps, LocalDate.parse(date)) }, enabled = !state.saving && (if (sleep) hours.isNotBlank() || minutes.isNotBlank() else steps.isNotBlank()), modifier = Modifier.fillMaxWidth()) { Text(if (state.saving) "Сохраняем…" else "Сохранить запись") }
    }
}
