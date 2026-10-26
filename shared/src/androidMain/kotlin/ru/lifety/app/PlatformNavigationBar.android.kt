package ru.lifety.app

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.lifety.app.navigation.AppDestination
import ru.lifety.core.ui.*

@Composable
internal actual fun PlatformNavigationBar(selected: AppDestination, onSelect: (AppDestination) -> Unit) {
    val symbols = listOf(LifetySymbol.Home, LifetySymbol.Plan, LifetySymbol.Wallet, LifetySymbol.Heart, LifetySymbol.Assistant)
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
        primarySections.forEachIndexed { index, item ->
            NavigationBarItem(selected = selected == item, onClick = { onSelect(item) },
                icon = { LifetyIcon(symbols[index]) },
                label = { Text(item.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary, indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant, unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant))
        }
    }
}
