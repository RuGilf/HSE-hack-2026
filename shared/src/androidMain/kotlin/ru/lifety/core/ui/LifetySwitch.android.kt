package ru.lifety.core.ui

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun LifetySwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier, enabled: Boolean) {
    Switch(checked, onCheckedChange, modifier, enabled = enabled)
}
