package ru.lifety.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

/** Scroll content draws behind the floating iOS bar, but its final item and actions clear it. */
internal val LocalBottomNavigationInset = staticCompositionLocalOf { 0.dp }
