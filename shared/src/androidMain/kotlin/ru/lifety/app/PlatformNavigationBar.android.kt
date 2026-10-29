package ru.lifety.app

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.lifety.app.navigation.AppDestination
import ru.lifety.core.ui.*
import ru.lifety.app.navigation.*

@Composable
internal actual fun PlatformNavigationBar(selected: AppDestination, onSelect: (AppDestination) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
        LocalNavigationItems.current.forEach { item ->
            NavigationBarItem(selected = selected == item, onClick = { onSelect(item) },
                icon = { LifetyIcon(item.symbol) },
                label = { Text(item.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}
