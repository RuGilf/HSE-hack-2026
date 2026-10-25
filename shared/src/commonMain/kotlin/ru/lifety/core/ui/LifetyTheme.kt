package ru.lifety.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun LifetyTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFFA8D5BC), primaryContainer = Color(0xFF224D3C))
    else lightColorScheme(
        primary = Color(0xFF2D6750), onPrimary = Color.White,
        primaryContainer = Color(0xFFDCEDE1), onPrimaryContainer = Color(0xFF173C2B),
        secondary = Color(0xFF766342), surface = Color(0xFFF7F8F3),
        background = Color(0xFFF7F8F3), surfaceContainerLow = Color.White,
    )
    MaterialTheme(colorScheme = colors, content = content)
}
