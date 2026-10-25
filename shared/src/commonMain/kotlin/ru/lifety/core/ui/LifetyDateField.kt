package ru.lifety.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import ru.lifety.core.time.formatDate
import ru.lifety.core.time.localDate

/** Civil dates use ISO strings so Finance's existing date model stays unchanged. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifetyDateField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var showPicker by remember { mutableStateOf(false) }
    val selected = remember(value) { runCatching { LocalDate.parse(value) }.getOrNull() }
    Surface(onClick = { showPicker = true }, enabled = enabled, shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.semantics { contentDescription = "$label: ${selected?.let(::formatDate) ?: value}" }) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(selected?.let(::formatDate) ?: value, style = MaterialTheme.typography.bodyMedium)
            }
            LifetyIcon(LifetySymbol.Calendar, color = MaterialTheme.colorScheme.primary)
        }
    }
    if (showPicker) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = selected?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds(), yearRange = 1900..9999)
        DatePickerDialog(onDismissRequest = { showPicker = false },
            confirmButton = { TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { onChange(localDate(it, TimeZone.UTC).toString()) }
                showPicker = false
            }) { Text("Готово") } },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Отмена") } }) { DatePicker(picker) }
    }
}
