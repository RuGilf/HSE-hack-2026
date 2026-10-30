package ru.lifety.app

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.lifety.core.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal actual fun PlatformTopBar(title: String, showMascot: Boolean, onSections: () -> Unit, onSettings: () -> Unit) {
    TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = { if (showMascot) LifetyMascot(Modifier.padding(start = 12.dp).size(32.dp), animated = false) },
        actions = {
            IconButton(onSections, Modifier.semantics { contentDescription = "Все разделы" }) { LifetyIcon(LifetySymbol.More) }
            IconButton(onSettings, Modifier.semantics { contentDescription = "Настройки" }) { LifetyIcon(LifetySymbol.Settings, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
}
