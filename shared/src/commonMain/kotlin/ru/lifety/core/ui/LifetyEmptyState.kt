package ru.lifety.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun LifetyEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    emotion: LifetyEmotion = LifetyEmotion.Calm,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    mascotSize: Dp = LifetySpacing.MascotHero,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LifetyMascot(Modifier.size(mascotSize), emotion)
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) Button(onClick = onAction,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = LifetySpacing.ControlHeight)) { Text(actionLabel) }
    }
}
