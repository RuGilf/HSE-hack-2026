package ru.lifety.feature.health

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import ru.lifety.core.ui.*

@Composable
fun HealthScreen(modifier: Modifier = Modifier) {
    val repository = LocalRepositories.current?.health
    key(repository) {
        val scope = rememberCoroutineScope()
        val controller = remember(repository, scope) { HealthController(repository, scope) }
        val state by controller.state.collectAsState()
        var hours by rememberSaveable { mutableStateOf("") }
        var minutes by rememberSaveable { mutableStateOf("") }
        var steps by rememberSaveable { mutableStateOf("") }
        var importBusy by remember { mutableStateOf(false) }
        val formEnabled = !state.saving && !importBusy
        val focus = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(state.saved) { if (state.saved) snackbar.showSnackbar("Показатели сохранены") }
        Scaffold(modifier = modifier, contentWindowInsets = WindowInsets(0, 0, 0, 0), snackbarHost = { SnackbarHost(snackbar) }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (repository == null) Text("Хранилище недоступно", color = MaterialTheme.colorScheme.error)
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.outlineVariant)
                val snapshot = state.snapshot
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        HealthMetric("Сон", snapshot?.sleepMinutes?.let { "${it / 60} ч ${it % 60} мин" } ?: "Нет данных", LifetySymbol.Sleep)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        HealthMetric("Шаги", snapshot?.steps?.toString() ?: "Нет данных", LifetySymbol.Steps)
                        snapshot?.let { Text("Обновлено: ${healthRecordedAt(it.timestamp)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                HealthConnectSection(repository = repository, enabled = repository != null && !state.saving, onBusyChanged = { importBusy = it })
                Text("Новая запись", style = MaterialTheme.typography.titleMedium)
                Text("Сон за последнюю ночь и шаги за сегодня. Пустые поля останутся без данных.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(hours, { hours = it.take(4) }, Modifier.weight(1f), enabled = formEnabled, singleLine = true,
                        label = { Text("Сон, часы") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }))
                    OutlinedTextField(minutes, { minutes = it.take(4) }, Modifier.weight(1f), enabled = formEnabled, singleLine = true,
                        label = { Text("Минуты") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }))
                }
                OutlinedTextField(steps, { steps = it.take(8) }, Modifier.fillMaxWidth(), enabled = formEnabled, singleLine = true,
                    label = { Text("Шаги за сегодня") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); focus.clearFocus() }))
                AnimatedVisibility(state.error != null, enter = LifetyMotion.RevealEnter, exit = LifetyMotion.RevealExit) {
                    Column {
                        Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = controller::reload, enabled = formEnabled) { Text("Обновить показатели") }
                    }
                }
                Button(onClick = { keyboard?.hide(); focus.clearFocus(); controller.save(hours, minutes, steps) }, enabled = repository != null && formEnabled,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (state.saving) "Сохранение…" else "Сохранить на устройстве")
                }
                Text("Ручной ввод · данные хранятся на устройстве", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HealthMetric(label: String, value: String, symbol: LifetySymbol) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        LifetyIcon(symbol, color = MaterialTheme.colorScheme.primary)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge)
        }
    }
}
