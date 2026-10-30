package ru.lifety.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Background exposed by UIKit cut-outs must match the enclosing Compose surface. */
internal val LocalNativeSurfaceColor = staticCompositionLocalOf { Color.Unspecified }
