@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package ru.lifety.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.*
import platform.UIKit.UIDatePickerMode
import ru.lifety.core.time.localDate
import ru.lifety.core.time.localDatePickerMillis
import ru.lifety.core.time.today

@Composable
actual fun LifetyDateField(label: String, value: String, onChange: (String) -> Unit,
    modifier: Modifier, enabled: Boolean) {
    val date = remember(value) { runCatching { LocalDate.parse(value) }.getOrNull() }
    val zone = TimeZone.currentSystemDefault()
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IosDatePicker(localDatePickerMillis(date ?: today(zone), zone),
                UIDatePickerMode.UIDatePickerModeDate, label, Modifier.fillMaxWidth(), enabled,
                onChange = { onChange(localDate(it, zone).toString()) })
        }
    }
}
