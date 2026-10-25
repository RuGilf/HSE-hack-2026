package ru.lifety.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val colors = darkColorScheme(
    primary = LifetyColors.Accent,
    onPrimary = LifetyColors.Background,
    primaryContainer = LifetyColors.PrimaryContainer,
    onPrimaryContainer = LifetyColors.TextPrimary,
    inversePrimary = LifetyColors.Accent,
    secondary = LifetyColors.Accent,
    onSecondary = LifetyColors.Background,
    secondaryContainer = LifetyColors.SecondaryContainer,
    onSecondaryContainer = LifetyColors.TextPrimary,
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
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

// Material 3's type scale keeps controls and content aligned with Android conventions.
private val typography = Typography()

@Composable
fun LifetyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, shapes = shapes, typography = typography, content = content)
}
