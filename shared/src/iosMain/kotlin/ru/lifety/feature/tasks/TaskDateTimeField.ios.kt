@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package ru.lifety.feature.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.datetime.*
import platform.UIKit.UIDatePickerMode
import ru.lifety.core.planner.plannerTimestamp
import ru.lifety.core.ui.IosDatePicker
import kotlin.time.Instant

@Composable
internal actual fun TaskDateTimeField(label: String, description: String, value: Long?, enabled: Boolean,
    referenceDate: LocalDate, deadline: Boolean, onChange: (Long?) -> Unit) {
    val zone = TimeZone.currentSystemDefault()
    var error by remember { mutableStateOf<String?>(null) }
    fun choose(date: LocalDate) {
        val time = value?.let { Instant.fromEpochMilliseconds(it).toLocalDateTime(zone).time }
        try {
            onChange(plannerTimestamp(date, time?.hour ?: if (deadline) 23 else 9, time?.minute ?: if (deadline) 59 else 0, zone))
            error = null
        } catch (invalid: IllegalArgumentException) { error = invalid.message }
    }
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { if (value == null) choose(referenceDate) else { onChange(null); error = null } }, enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = "$label: ${if (value == null) "указать" else "убрать"}" }) {
                    Text(if (value == null) "Указать" else "Убрать")
                }
            }
            if (value != null) {
                IosDatePicker(value, UIDatePickerMode.UIDatePickerModeDateAndTime, label, Modifier.fillMaxWidth(), enabled) {
                    val selected = Instant.fromEpochMilliseconds(it).toLocalDateTime(zone)
                    try {
                        onChange(plannerTimestamp(selected.date, selected.hour, selected.minute, zone)); error = null
                    } catch (invalid: IllegalArgumentException) { error = invalid.message }
                }
            } else Text(if (deadline) "Без дедлайна" else "Не запланировано", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        }
    }
}
