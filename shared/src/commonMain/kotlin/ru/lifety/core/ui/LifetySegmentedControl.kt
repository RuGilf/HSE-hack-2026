package ru.lifety.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** UIKit on iOS, Material 3 on Android. Selection always belongs to the caller. */
@Composable
internal expect fun LifetySegmentedControl(labels: List<String>, selected: Int,
    onSelect: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true)
