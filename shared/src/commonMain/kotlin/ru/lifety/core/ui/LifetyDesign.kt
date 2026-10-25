package ru.lifety.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared Lifety tokens. Feature screens should use MaterialTheme, not duplicate hex values. */
object LifetyColors {
    val Background = Color(0xFF101512)
    val Surface = Color(0xFF1D2520)
    val SurfaceRaised = Color(0xFF263229)
    val Accent = Color(0xFF35EF70)
    val TextPrimary = Color(0xFFF4F7F3)
    val TextSecondary = Color(0xFF9BAA9F)
    val Outline = Color(0xFF34473A)
    val Error = Color(0xFFFF817D)
}

object LifetySpacing {
    val Small = 8.dp
    val Item = 12.dp
    val Content = 16.dp
    val Screen = 20.dp
    val Section = 24.dp
    val ControlHeight = 52.dp
    val MascotInline = 56.dp
    val MascotHero = 120.dp
}
