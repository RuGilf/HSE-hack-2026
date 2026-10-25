package ru.lifety.feature.health

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.lifety.core.ui.LocalRepositories

@Composable
fun HealthScreen(modifier: Modifier = Modifier) {
    val repository = LocalRepositories.current?.health
    key(repository) {
        val scope = rememberCoroutineScope()
        val controller = remember(repository, scope) { HealthController(repository, scope) }
        val state by controller.state.collectAsState()
        var hours by remember { mutableStateOf("") }
        var minutes by remember { mutableStateOf("") }
        var steps by remember { mutableStateOf("") }
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Забота о себе", style = MaterialTheme.typography.headlineLarge)
            Text("Сон и движение помогают заметить свой ритм. Записи сохраняются на устройстве, автоматической отправки нет.", style = MaterialTheme.typography.bodyLarge)
            if (repository == null) Text("Локальное хранилище ещё не подключено. Сохранение пока недоступно.", color = MaterialTheme.colorScheme.error)
            if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            val snapshot = state.snapshot
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Последние показатели", style = MaterialTheme.typography.titleLarge)
                    Text("Сон", style = MaterialTheme.typography.labelLarge)
                    Text(snapshot?.sleepMinutes?.let { "${it / 60} ч ${it % 60} мин" } ?: "Пока нет данных", style = MaterialTheme.typography.headlineMedium)
                    Text("Шаги", style = MaterialTheme.typography.labelLarge)
                    Text(snapshot?.steps?.toString() ?: "Пока нет данных", style = MaterialTheme.typography.headlineMedium)
                    snapshot?.let { Text("Записано: ${healthRecordedAt(it.timestamp)}", style = MaterialTheme.typography.bodySmall) }
                    if (snapshot == null) Text("Добавьте первую запись ниже. Неизвестные значения останутся пустыми, а не нулевыми.")
                }
            }
            Text("Добавить вручную", style = MaterialTheme.typography.titleLarge)
            Text("Введите сон за последнюю ночь и шаги за сегодня. После сохранения будут показаны новые значения. Пустое поле означает «неизвестно».")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(hours, { hours = it }, Modifier.weight(1f), enabled = !state.saving, singleLine = true,
                    label = { Text("Сон, часы") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(minutes, { minutes = it }, Modifier.weight(1f), enabled = !state.saving, singleLine = true,
                    label = { Text("Минуты") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            OutlinedTextField(steps, { steps = it }, Modifier.fillMaxWidth(), enabled = !state.saving, singleLine = true,
                label = { Text("Шаги за сегодня") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Button(onClick = { controller.save(hours, minutes, steps) }, enabled = repository != null && !state.saving) {
                Text(if (state.saving) "Сохраняем…" else "Сохранить на устройстве")
            }
            if (state.saved) Text("Показатели сохранены локально.", color = MaterialTheme.colorScheme.primary)
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = controller::reload, enabled = !state.saving) { Text("Обновить показатели") }
            }
            Text("Импорт из Health Connect пока недоступен. Сейчас можно добавить сон и шаги вручную.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
