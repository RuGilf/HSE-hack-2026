package ru.lifety.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** ISO civil date, independent of the device time zone. Platform UI only; no persistence. */
@Composable
expect fun LifetyDateField(label: String, value: String, onChange: (String) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true)
