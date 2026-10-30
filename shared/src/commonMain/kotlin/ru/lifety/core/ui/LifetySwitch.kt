package ru.lifety.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun LifetySwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true)
