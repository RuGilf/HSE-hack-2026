package ru.lifety.core.appearance

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

data class ThemePalette(val background: Color, val surface: Color, val raised: Color,
    val accent: Color, val text: Color, val muted: Color, val outline: Color, val error: Color) {
    val isDark: Boolean get() = background.luminance() < 0.5f
}

/** Adapted from the upstream Neovim palettes. Sources and licenses: docs/APPEARANCE.md. */
enum class LifetyPalette(val id: String, val title: String, val subtitle: String, val palette: ThemePalette) {
    Lifety("lifety", "Lifety", "Зелёный · оригинал", ThemePalette(Color(0xFF101512), Color(0xFF1D2520), Color(0xFF263229), Color(0xFF35EF70), Color(0xFFF4F7F3), Color(0xFF9BAA9F), Color(0xFF34473A), Color(0xFFFF817D))),
    TokyoNight("tokyo-night", "Tokyo Night", "Ночной синий", ThemePalette(Color(0xFF1A1B26), Color(0xFF24283B), Color(0xFF292E42), Color(0xFF7AA2F7), Color(0xFFC0CAF5), Color(0xFFA9B1D6), Color(0xFF545C7E), Color(0xFFF7768E))),
    Catppuccin("catppuccin", "Catppuccin", "Mocha · лиловый", ThemePalette(Color(0xFF1E1E2E), Color(0xFF313244), Color(0xFF45475A), Color(0xFFCBA6F7), Color(0xFFCDD6F4), Color(0xFFBAC2DE), Color(0xFF6C7086), Color(0xFFF38BA8))),
    Gruvbox("gruvbox", "Gruvbox", "Тёплый янтарный", ThemePalette(Color(0xFF282828), Color(0xFF3C3836), Color(0xFF504945), Color(0xFFFABD2F), Color(0xFFEBDBB2), Color(0xFFBDAE93), Color(0xFF7C6F64), Color(0xFFFB4934))),
    Nord("nord", "Nord", "Холодный голубой", ThemePalette(Color(0xFF2E3440), Color(0xFF3B4252), Color(0xFF434C5E), Color(0xFF88C0D0), Color(0xFFECEFF4), Color(0xFFD8DEE9), Color(0xFF68768C), Color(0xFFBF616A))),
    LifetyLight("lifety-light", "Lifety Light", "Светлый · зелёный", ThemePalette(Color(0xFFF4F8F4), Color(0xFFFFFFFF), Color(0xFFE2ECE3), Color(0xFF15723B), Color(0xFF17291D), Color(0xFF4E6354), Color(0xFF829689), Color(0xFFBA1A1A))),
    CatppuccinLatte("catppuccin-latte", "Catppuccin Latte", "Молочный · лиловый", ThemePalette(Color(0xFFEFF1F5), Color(0xFFFFFFFF), Color(0xFFDCE0E8), Color(0xFF8839EF), Color(0xFF4C4F69), Color(0xFF6C6F85), Color(0xFF9CA0B0), Color(0xFFD20F39))),
    GruvboxLight("gruvbox-light", "Gruvbox Light", "Кремовый · янтарный", ThemePalette(Color(0xFFFBF1C7), Color(0xFFF9F5D7), Color(0xFFEBDBB2), Color(0xFFB57614), Color(0xFF3C3836), Color(0xFF665C54), Color(0xFFA89984), Color(0xFF9D0006)));

    val alternateIconName: String? get() = if (this == Lifety) null else "AppIcon$name"
    companion object {
        fun fromId(id: String?): LifetyPalette = entries.firstOrNull { it.id == id } ?: Lifety
        fun fromIconName(name: String?): LifetyPalette = entries.firstOrNull { it.alternateIconName == name } ?: Lifety
    }
}
