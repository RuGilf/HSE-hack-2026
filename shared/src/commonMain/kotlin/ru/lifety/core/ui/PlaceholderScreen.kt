package ru.lifety.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun PlaceholderScreen(title: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        LifetyEmptyState(title, "Lifety готовится помогать здесь. Этот раздел ещё в разработке.", emotion = LifetyEmotion.Happy)
    }
}
