package ru.lifety.core.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal actual fun LifetyTimeField(label: String, hour: Int, minute: Int,
    onChange: (Int, Int) -> Unit, modifier: Modifier, enabled: Boolean) {
    var show by remember { mutableStateOf(false) }
    TextButton({ show = true }, modifier, enabled = enabled) { Text(label) }
    if (show) {
        val picker = rememberTimePickerState(hour, minute, is24Hour = true)
        AlertDialog(onDismissRequest = { show = false }, title = { Text(label) },
            text = { TimeInput(picker) },
            confirmButton = { TextButton({ onChange(picker.hour, picker.minute); show = false }) { Text("Готово") } },
            dismissButton = { TextButton({ show = false }) { Text("Отмена") } })
    }
}
