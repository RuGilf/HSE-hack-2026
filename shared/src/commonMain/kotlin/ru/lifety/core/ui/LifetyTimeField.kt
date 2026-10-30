package ru.lifety.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal expect fun LifetyTimeField(label: String, hour: Int, minute: Int,
    onChange: (Int, Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)
