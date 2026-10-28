package ru.lifety.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import ru.lifety.core.appearance.*
import androidx.compose.ui.unit.dp

/** Material roles are derived once per selected palette; feature screens keep using MaterialTheme. */
internal fun LifetyPalette.colorScheme(): androidx.compose.material3.ColorScheme {
    val p = palette
    val accent = readableOn(p.accent, p.raised, p.text)
    val muted = readableOn(p.muted, p.raised, p.text)
    val error = readableOn(p.error, p.raised, p.text)
    val primaryContainer = if (this == LifetyPalette.Lifety) LifetyColors.PrimaryContainer else lerp(p.background, p.accent, 0.16f)
    val secondaryContainer = if (this == LifetyPalette.Lifety) LifetyColors.SecondaryContainer else lerp(p.surface, p.accent, 0.10f)
    return darkColorScheme(
        primary = accent, onPrimary = p.background,
        primaryContainer = primaryContainer, onPrimaryContainer = p.text, inversePrimary = accent,
        secondary = accent, onSecondary = p.background,
        secondaryContainer = secondaryContainer, onSecondaryContainer = p.text,
        tertiary = accent, onTertiary = p.background,
        tertiaryContainer = p.raised, onTertiaryContainer = p.text,
        background = p.background, onBackground = p.text,
        surface = p.background, onSurface = p.text,
        surfaceVariant = p.surface, onSurfaceVariant = muted, surfaceTint = accent,
        surfaceDim = p.background, surfaceBright = p.raised,
        surfaceContainerLowest = p.background, surfaceContainerLow = p.surface,
        surfaceContainer = p.surface, surfaceContainerHigh = p.surface, surfaceContainerHighest = p.raised,
        outline = p.outline, outlineVariant = p.outline.copy(alpha = 0.6f),
        error = error, onError = p.background,
        errorContainer = lerp(p.background, p.error, 0.16f), onErrorContainer = p.text,
        inverseSurface = p.text, inverseOnSurface = p.background,
    )
}

/** Editor syntax colors need a little more contrast when used for small interface text. */
private fun readableOn(color: Color, background: Color, light: Color): Color {
    for (step in 0..20) {
        val candidate = lerp(color, light, step / 20f)
        if ((candidate.luminance() + .05f) / (background.luminance() + .05f) >= 4.5f) return candidate
    }
    return light
}

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
    val appearance = rememberAppearanceController()
    val state by appearance.state.collectAsState()
    val colors = remember(state.theme) { state.theme.colorScheme() }
    ApplyPlatformAppearance(state.theme.palette)
    CompositionLocalProvider(LocalAppearance provides appearance) {
        MaterialTheme(colorScheme = colors, shapes = shapes, typography = typography, content = content)
    }
}
