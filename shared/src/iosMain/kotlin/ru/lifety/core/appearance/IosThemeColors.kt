package ru.lifety.core.appearance

import platform.UIKit.UIColor

/** SwiftUI host and Compose use a single palette, including the safe-area background. */
fun iosThemeBackground(themeId: String): UIColor = LifetyPalette.fromId(themeId).palette.background.toUiColor()
