package ru.lifety.feature.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.datetime.*
import ru.lifety.core.planner.formatPlannerTime
import ru.lifety.core.planner.plannerTimestamp
import ru.lifety.core.time.formatDate
import ru.lifety.core.time.localDate
import ru.lifety.core.time.today
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskDateTimeField(label: String, description: String, value: Long?, enabled: Boolean,
    referenceDate: LocalDate, deadline: Boolean, onChange: (Long?) -> Unit) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val zone = TimeZone.currentSystemDefault()
    val date = value?.let { localDate(it, zone) }
    val time = value?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(zone).time }
    val hour = time?.hour ?: if (deadline) 23 else 9
    val minute = time?.minute ?: if (deadline) 59 else 0
    fun choose(chosen: LocalDate, h: Int = hour, m: Int = minute) {
        try { onChange(plannerTimestamp(chosen, h, m, zone)); error = null }
        catch (invalid: IllegalArgumentException) { error = invalid.message }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (value != null) TextButton(onClick = { onChange(null); error = null }, enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = "Убрать: $label" }) { Text("Убрать") }
            }
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = date == today(zone), enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = "Сегодня: $label" }, onClick = { choose(today(zone)) }, label = { Text("Сегодня") })
                FilterChip(selected = date == today(zone).plus(DatePeriod(days = 1)), enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = "Завтра: $label" }, onClick = { choose(today(zone).plus(DatePeriod(days = 1))) }, label = { Text("Завтра") })
                OutlinedButton(onClick = { showDate = true }, enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = "Дата: $label" }) {
                    Text(date?.let(::formatDate) ?: "Выбрать дату")
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (value == null) if (deadline) "Без дедлайна" else "Без точного времени"
                    else if (deadline) "Готово к ${formatDate(requireNotNull(date))}" else "Заняться ${formatDate(requireNotNull(date))}",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { showTime = true }, enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = "Время: $label" }) {
                    Text(value?.let { formatPlannerTime(it, zone) } ?: "Указать время")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (showDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = (date ?: referenceDate).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds())
        DatePickerDialog(onDismissRequest = { showDate = false },
            confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { choose(localDate(it, TimeZone.UTC)) }; showDate = false
            }) { Text("Готово") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Отмена") } }) { DatePicker(picker) }
    }
    if (showTime) {
        val picker = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(onDismissRequest = { showTime = false }, title = { Text(if (deadline) "Дедлайн — время" else "Когда заняться") },
            text = { TimeInput(picker) }, confirmButton = { TextButton(onClick = {
                choose(date ?: referenceDate, picker.hour, picker.minute); showTime = false
            }) { Text("Готово") } }, dismissButton = { TextButton(onClick = { showTime = false }) { Text("Отмена") } })
    }
}
