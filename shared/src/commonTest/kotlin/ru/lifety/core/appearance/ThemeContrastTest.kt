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
            listOf(c.background, c.surfaceContainer, c.surfaceContainerHighest, c.surfaceBright, c.surfaceDim).forEach { background ->
                listOf(c.onSurface, c.onSurfaceVariant, c.primary, c.error).forEach { text ->
                    assertTrue(contrast(text, background) >= 4.5f, "${theme.title} text contrast ${contrast(text, background)}")
                }
            }
            listOf(c.onPrimary to c.primary, c.onSecondary to c.secondary, c.onTertiary to c.tertiary,
                c.onSecondaryContainer to c.secondaryContainer, c.onPrimaryContainer to c.primaryContainer,
                c.onError to c.error, c.onErrorContainer to c.errorContainer,
                c.inverseOnSurface to c.inverseSurface, c.inversePrimary to c.inverseSurface).forEach { (text, background) ->
                assertTrue(contrast(text, background) >= 4.5f, "${theme.title} control contrast ${contrast(text, background)}")
            }
        }
    }

    @Test fun lightThemesUseLightSurfacesAndRestoreTheirOwnIdsAndIcons() {
        val light = listOf(LifetyPalette.LifetyLight, LifetyPalette.CatppuccinLatte, LifetyPalette.GruvboxLight)
        light.forEach { theme ->
            val scheme = theme.colorScheme()
            assertFalse(theme.palette.isDark)
            assertTrue(scheme.background.luminance() > 0.5f)
            assertTrue(scheme.onBackground.luminance() < 0.2f)
            assertEquals(theme, LifetyPalette.fromId(theme.id))
            assertEquals(theme, LifetyPalette.fromIconName(theme.alternateIconName))
        }
        assertTrue(LifetyPalette.Lifety.palette.isDark)
        assertTrue(LifetyPalette.Catppuccin.palette.isDark)
    }
}
