package ru.lifety.feature.people

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.number
import ru.lifety.core.time.localDate
import ru.lifety.core.time.today

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal actual fun BirthdayPickerDialog(initial: BirthdaySelection?, onConfirm: (BirthdaySelection) -> Unit, onDismiss: () -> Unit) {
    val currentDay = today()
    var knownYear by rememberSaveable { mutableStateOf(initial?.year != null) }
    val initialDate = initial?.let { LocalDate(it.year ?: 2000, it.month, it.day) }
    val picker = rememberDatePickerState(initialSelectedDateMillis = initialDate?.atStartOfDayIn(TimeZone.UTC)?.toEpochMilliseconds(), yearRange = 1..currentDay.year)
    val date = picker.selectedDateMillis?.let { localDate(it, TimeZone.UTC) }
    val selection = date?.let { BirthdaySelection(it.day, it.month.number, if (knownYear) it.year else null) }
    val error = selection?.let { birthdaySelectionError(it, currentDay) }
    DatePickerDialog(onDismissRequest = onDismiss,
        confirmButton = { TextButton(enabled = selection != null && error == null, onClick = { selection?.let(onConfirm) }) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }) {
        Column {
            DatePicker(picker)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(knownYear, { knownYear = it })
                Text("Год рождения известен")
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) }
        }
    }
}
