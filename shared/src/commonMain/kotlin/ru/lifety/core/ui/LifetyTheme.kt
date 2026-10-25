package ru.lifety.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val colors = darkColorScheme(
    primary = LifetyColors.Accent,
    onPrimary = LifetyColors.Background,
    primaryContainer = LifetyColors.SurfaceRaised,
    onPrimaryContainer = LifetyColors.Accent,
    inversePrimary = LifetyColors.Accent,
    secondary = LifetyColors.Accent,
    onSecondary = LifetyColors.Background,
    secondaryContainer = LifetyColors.Accent,
    onSecondaryContainer = LifetyColors.Background,
    tertiary = LifetyColors.Accent,
    onTertiary = LifetyColors.Background,
    tertiaryContainer = LifetyColors.SurfaceRaised,
    onTertiaryContainer = LifetyColors.TextPrimary,
    background = LifetyColors.Background,
    onBackground = LifetyColors.TextPrimary,
    surface = LifetyColors.Background,
    onSurface = LifetyColors.TextPrimary,
    surfaceVariant = LifetyColors.Surface,
    onSurfaceVariant = LifetyColors.TextSecondary,
    surfaceTint = LifetyColors.Accent,
    surfaceDim = LifetyColors.Background,
    surfaceBright = LifetyColors.SurfaceRaised,
    surfaceContainerLowest = LifetyColors.Background,
    surfaceContainerLow = LifetyColors.Surface,
    surfaceContainer = LifetyColors.Surface,
    surfaceContainerHigh = LifetyColors.Surface,
    surfaceContainerHighest = LifetyColors.SurfaceRaised,
    outline = LifetyColors.Outline,
    outlineVariant = LifetyColors.Outline.copy(alpha = 0.6f),
    error = LifetyColors.Error,
    onError = LifetyColors.Background,
    errorContainer = androidx.compose.ui.graphics.Color(0xFF402522),
    onErrorContainer = LifetyColors.Error,
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val typography = Typography(
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp),
)

@Composable
fun LifetyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, shapes = shapes, typography = typography, content = content)
}
