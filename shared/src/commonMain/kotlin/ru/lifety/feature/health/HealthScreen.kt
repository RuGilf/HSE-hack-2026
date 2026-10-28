package ru.lifety.feature.health

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import ru.lifety.core.time.formatDate
import ru.lifety.core.time.localDate
import ru.lifety.core.ui.*
import kotlin.time.Clock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(modifier: Modifier = Modifier) {
    val repository = LocalRepositories.current?.health
    key(repository) {
        val scope = rememberCoroutineScope()
        val controller = remember(repository, scope) { HealthController(repository, scope) }
        val state by controller.state.collectAsState()
        var periodName by rememberSaveable { mutableStateOf(HealthPeriod.WEEK.name) }
        val period = HealthPeriod.valueOf(periodName)
        var selectedDate by rememberSaveable { mutableStateOf("") }
        var today by remember { mutableStateOf(localDate(Clock.System.now().toEpochMilliseconds())) }
        LaunchedEffect(Unit) { while (true) { delay(60_000); today = localDate(Clock.System.now().toEpochMilliseconds()) } }
        val timeline = remember(state.history, period, today) { healthTimeline(state.history, period, today) }
        val selected = timeline.days.indexOfFirst { it.date.toString() == selectedDate }.takeIf { it >= 0 } ?: timeline.days.lastIndex
        val day = timeline.days.getOrNull(selected)
        var showImport by rememberSaveable { mutableStateOf(false) }
        var showManual by rememberSaveable { mutableStateOf(false) }
        var hours by rememberSaveable { mutableStateOf("") }
        var minutes by rememberSaveable { mutableStateOf("") }
        var steps by rememberSaveable { mutableStateOf("") }
        var manualDate by rememberSaveable { mutableStateOf(today.toString()) }
        var importBusy by remember { mutableStateOf(false) }
        val formEnabled = !state.saving && !importBusy
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val snackbar = remember { SnackbarHostState() }
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !state.saving })
        LaunchedEffect(state.saved) {
            if (state.saved) {
                sheet.hide(); showManual = false; hours = ""; minutes = ""; steps = ""
                snackbar.showSnackbar("Запись сохранена в истории")
            }
        }
        val bottomInset = ru.lifety.core.ui.LocalBottomNavigationInset.current
        Scaffold(modifier = modifier, contentWindowInsets = WindowInsets(0, 0, 0, 0), snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = bottomInset)) }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp,
                bottom = 16.dp + bottomInset), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        HealthPeriod.entries.forEachIndexed { index, option ->
                            SegmentedButton(selected = option == period, onClick = { periodName = option.name; selectedDate = "" },
                                shape = SegmentedButtonDefaults.itemShape(index, HealthPeriod.entries.size)) { Text(option.label) }
                        }
                    }
                }
                if (repository == null) item { Text("Хранилище недоступно", color = MaterialTheme.colorScheme.error) }
                if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                item {
                    FilledTonalButton(onClick = {
                        controller.clearSaveStatus(); manualDate = (day?.date ?: today).toString(); showManual = true
                    }, enabled = repository != null && formEnabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Добавить запись") }
                }
                item { Text("История сна и движения", style = MaterialTheme.typography.titleMedium) }
                item { HealthChart("Сон", timeline, selected, true) { selectedDate = timeline.days[it].date.toString() } }
                item { HealthChart("Шаги", timeline, selected, false) { selectedDate = timeline.days[it].date.toString() } }
                if (day != null) item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                TextButton(enabled = selected > 0, onClick = { selectedDate = timeline.days[selected - 1].date.toString() }) { Text("Назад") }
                                Text(formatDate(day.date), style = MaterialTheme.typography.titleSmall)
                                TextButton(enabled = selected < timeline.days.lastIndex, onClick = { selectedDate = timeline.days[selected + 1].date.toString() }) { Text("Вперёд") }
                            }
                            Text("Сон: ${sleepLabel(day.sleepMinutes)}")
                            Text("Шаги: ${day.steps?.toString() ?: "Нет данных"}")
                        }
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth()) {
                        ListItem(modifier = Modifier.clickable(enabled = !importBusy) { showImport = !showImport },
                            headlineContent = { Text(if (isIos) "Импорт из Apple Health" else "Импорт Health Connect") }, supportingContent = { Text("Сон и шаги за последние 30 дней") },
                            trailingContent = { Text(if (showImport) "Скрыть" else "Открыть", color = MaterialTheme.colorScheme.primary) })
                        AnimatedVisibility(showImport, enter = LifetyMotion.RevealEnter, exit = LifetyMotion.RevealExit) {
                            HealthConnectSection(repository, enabled = repository != null && !state.saving, onBusyChanged = { importBusy = it })
                        }
                    }
                }
                state.error?.let { error -> item {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = controller::reload, enabled = formEnabled) { Text("Обновить историю") }
                } }
                item {
                    Text("По дням", style = MaterialTheme.typography.titleMedium)
                    Text("${timeline.records} записей за период. За день показано последнее известное значение каждого показателя. Пропуски не считаются нулём.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val recorded = timeline.days.filter { it.records > 0 }.asReversed()
                if (recorded.isEmpty()) item { Text("Здесь появятся сохранённые записи и импортированные дни.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(recorded, key = { it.date.toString() }, contentType = { "health-day" }) { entry ->
                    ListItem(modifier = Modifier.animateItem().clickable { selectedDate = entry.date.toString() },
                        headlineContent = { Text(formatDate(entry.date)) },
                        supportingContent = { Text("Сон: ${sleepLabel(entry.sleepMinutes)} · Шаги: ${entry.steps?.toString() ?: "Нет данных"}") })
                }
            }
        }
        if (showManual) LifetyBottomSheet(onDismissRequest = { if (!state.saving) showManual = false }, sheetState = sheet) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Запись здоровья", style = MaterialTheme.typography.titleLarge)
                LifetyDateField("Дата показателей", manualDate, { manualDate = it }, enabled = formEnabled)
                Text("Сон и шаги за выбранный день. Пустое поле — нет нового значения.", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(hours, { hours = it.take(4) }, Modifier.weight(1f), enabled = formEnabled, singleLine = true,
                        label = { Text("Сон, часы") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }))
                    OutlinedTextField(minutes, { minutes = it.take(4) }, Modifier.weight(1f), enabled = formEnabled, singleLine = true,
                        label = { Text("Минуты") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }))
                }
                OutlinedTextField(steps, { steps = it.take(8) }, Modifier.fillMaxWidth(), enabled = formEnabled, singleLine = true,
                    label = { Text("Шаги за день") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus() }))
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = {
                    keyboard?.hide(); focus.clearFocus(); controller.save(hours, minutes, steps, LocalDate.parse(manualDate))
                }, enabled = repository != null && formEnabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (state.saving) "Сохранение…" else "Сохранить в историю")
                }
                TextButton(onClick = { scope.launch { sheet.hide(); showManual = false } }, enabled = !state.saving, modifier = Modifier.fillMaxWidth()) { Text("Отмена") }
            }
        }
    }
}
