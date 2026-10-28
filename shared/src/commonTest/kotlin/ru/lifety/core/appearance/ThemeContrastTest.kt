package ru.lifety.core.appearance

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import ru.lifety.core.ui.colorScheme
import kotlin.test.*

class ThemeContrastTest {
    private fun contrast(a: Color, b: Color): Float = (maxOf(a.luminance(), b.luminance()) + .05f) / (minOf(a.luminance(), b.luminance()) + .05f)
    @Test fun palettesKeepSmallTextReadableAcrossContainers() {
        LifetyPalette.entries.forEach { theme ->
            val c = theme.colorScheme()
            listOf(c.background, c.surfaceContainer, c.surfaceContainerHighest).forEach { background ->
                listOf(c.onSurface, c.onSurfaceVariant, c.primary, c.error).forEach { text ->
                    assertTrue(contrast(text, background) >= 4.5f, "${theme.title} text contrast ${contrast(text, background)}")
                }
            }
            listOf(c.onPrimary to c.primary, c.onSecondaryContainer to c.secondaryContainer, c.onPrimaryContainer to c.primaryContainer,
                c.onErrorContainer to c.errorContainer).forEach { (text, background) ->
                assertTrue(contrast(text, background) >= 4.5f, "${theme.title} control contrast ${contrast(text, background)}")
            }
        }
    }
}
