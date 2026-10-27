package ru.lifety.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Shared Lifety tokens. Feature screens should use MaterialTheme, not duplicate hex values. */
object LifetyColors {
    val Background = Color(0xFF101512)
    val Surface = Color(0xFF1D2520)
    val PrimaryContainer = Color(0xFF173C25)
    val SecondaryContainer = Color(0xFF293C30)
    val SurfaceRaised = Color(0xFF263229)
    val Accent = Color(0xFF35EF70)
    val TextPrimary = Color(0xFFF4F7F3)
    val TextSecondary = Color(0xFF9BAA9F)
    val Outline = Color(0xFF34473A)
    val Error = Color(0xFFFF817D)

    // Stable chart/category accents. Amounts and labels keep the normal text color.
    object ExpenseCategory {
        val Food = Color(0xFF5CDF85)
        val Transport = Color(0xFF73B7FF)
        val Shopping = Color(0xFFD5A1FF)
        val Bills = Color(0xFFF6C76B)
        val Health = Color(0xFFFF9BA8)
        val Leisure = Color(0xFF60D5D2)
        val Education = Color(0xFFB6C5FF)
        val Other = Color(0xFFA8B5AC)
    }
}

object LifetySpacing {
    val Small = 8.dp
    val Item = 12.dp
    val Content = 16.dp
    val Screen = 16.dp
    val Section = 24.dp
    val ControlHeight = 48.dp
    val MascotInline = 56.dp
    val MascotHero = 120.dp
}
