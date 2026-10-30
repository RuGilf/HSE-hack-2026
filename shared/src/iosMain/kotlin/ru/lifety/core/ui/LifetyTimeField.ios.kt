@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package ru.lifety.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.*
import platform.UIKit.UIDatePickerMode
import ru.lifety.core.time.today
import kotlin.time.Instant

@Composable
internal actual fun LifetyTimeField(label: String, hour: Int, minute: Int,
    onChange: (Int, Int) -> Unit, modifier: Modifier, enabled: Boolean) {
    val date = today()
    // Time-only values deliberately use UTC: the date and DST must not change a duration.
    val initial = LocalDateTime(date, LocalTime(hour, minute)).toInstant(TimeZone.UTC).toEpochMilliseconds()
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        IosDatePicker(initial, UIDatePickerMode.UIDatePickerModeTime, label, Modifier.width(110.dp), enabled = enabled, useUtc = true, backgroundColor = LocalNativeSurfaceColor.current) {
            val time = Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC)
            onChange(time.hour, time.minute)
        }
    }
}
