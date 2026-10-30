package ru.lifety.feature.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.datetime.*
import ru.lifety.core.planner.*
import ru.lifety.core.time.formatDateTime
import ru.lifety.core.ui.LocalPlanningState
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskDurationFields(state: TaskEditorState, model: TaskEditorViewModel) {
    val task = state.task ?: return
    var goalPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val duration = state.duration.toIntOrNull()?.takeIf { it in 1..1440 }
    val durationRequired = state.hadDetails || state.approach.isNotBlank() || state.goalId != null
    val invalidDuration = (state.duration.isNotBlank() || durationRequired) && duration == null
    val end = task.scheduledAt?.let { start -> duration?.let { start + it * 60_000L } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Время на задачу", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(state.duration, model::setDuration, Modifier.fillMaxWidth(), label = { Text("Длительность, мин") },
            placeholder = { Text("Например, 45") }, enabled = !state.saving, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = invalidDuration,
            supportingText = { Text(if (invalidDuration) "Укажите 1..1440 минут. Для шагов и связи с целью нужна оценка времени."
                else "От 1 минуты до 24 часов. Без оценки длительность не подставляется.") })
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 60, 90, 120).forEach { minutes -> FilterChip(selected = duration == minutes, onClick = { model.setDuration(minutes.toString()) },
                enabled = !state.saving, label = { Text("$minutes мин") }) }
        }
        if (task.scheduledAt != null) {
            Text(end?.let { "${formatDateTime(task.scheduledAt)} → ${formatDateTime(it)}" } ?: "Укажите длительность, чтобы увидеть окончание", style = MaterialTheme.typography.bodyMedium)
            val initial = Instant.fromEpochMilliseconds(end ?: (task.scheduledAt + 30 * 60_000)).toLocalDateTime(TimeZone.currentSystemDefault())
            ru.lifety.core.ui.LifetyTimeField("Время окончания", initial.hour, initial.minute, { hour, minute ->
                try { model.setDuration(durationUntil(task.scheduledAt, hour, minute).toString()); error = null }
                catch (invalid: IllegalArgumentException) { error = invalid.message }
            }, enabled = !state.saving)
            Text("Время раньше начала означает следующий день.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        OutlinedTextField(state.approach, model::setApproach, Modifier.fillMaxWidth(), label = { Text("Шаги выполнения · необязательно") },
            placeholder = { Text("Как будешь выполнять задачу") }, minLines = 2, maxLines = 6, enabled = !state.saving)
        val goals = LocalPlanningState.current.goals
        Box {
            TextButton(onClick = { goalPicker = true }, enabled = !state.saving) { Text(goals.firstOrNull { it.id == state.goalId }?.let { "Цель: ${it.title}" } ?: "Связать с глобальной целью") }
            DropdownMenu(expanded = goalPicker, onDismissRequest = { goalPicker = false }) {
                DropdownMenuItem(text = { Text("Без цели") }, onClick = { model.setGoal(null); goalPicker = false })
                goals.filterNot { it.isCompleted }.forEach { goal -> DropdownMenuItem(text = { Text(goal.title) }, onClick = { model.setGoal(goal.id); goalPicker = false }) }
            }
        }
    }
}
