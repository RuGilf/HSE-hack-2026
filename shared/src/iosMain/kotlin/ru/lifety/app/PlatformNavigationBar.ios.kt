@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package ru.lifety.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import ru.lifety.core.appearance.toUiColor
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.unit.dp
import platform.darwin.NSObject
import platform.UIKit.*
import ru.lifety.app.navigation.AppDestination
import ru.lifety.app.navigation.LocalNavigationItems
import ru.lifety.core.ui.LocalBottomNavigationInset

/** UIKit supplies SF Symbols, VoiceOver selection and the native tab-bar appearance. */
@Composable
internal actual fun PlatformNavigationBar(selected: AppDestination, onSelect: (AppDestination) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val currentSelect by rememberUpdatedState(onSelect)
    val destinations = LocalNavigationItems.current
    val currentDestinations by rememberUpdatedState(destinations)
    val delegate = remember { object : NSObject(), UITabBarDelegateProtocol {
        override fun tabBar(tabBar: UITabBar, didSelectItem: UITabBarItem) {
            AppDestination.entries.getOrNull(didSelectItem.tag.toInt())?.takeIf { it in currentDestinations }?.let { currentSelect(it) }
        }
    } }
    val items = remember(destinations) { destinations.map { destination ->
        val symbol = when (destination) {
            AppDestination.DASHBOARD -> "house"; AppDestination.TASKS -> "checklist"; AppDestination.FINANCE -> "creditcard"
            AppDestination.HEALTH -> "heart"; AppDestination.ASSISTANT -> "bubble.left.and.bubble.right"
            AppDestination.CALENDAR -> "calendar"; AppDestination.PEOPLE -> "person.2"
        }
        UITabBarItem(title = destination.title, image = UIImage.systemImageNamed(symbol), tag = destination.ordinal.toLong()).apply {
            accessibilityLabel = destination.title
        }
    } }
    Box(Modifier.fillMaxWidth()) {
        UIKitView(factory = {
            UITabBar().apply {
                this.delegate = delegate
                setItems(items, animated = false)
                val appearance = UITabBarAppearance().apply { configureWithDefaultBackground() }
                standardAppearance = appearance
                scrollEdgeAppearance = appearance
                backgroundColor = UIColor.clearColor
                opaque = false
            }
        }, modifier = Modifier.fillMaxWidth().height(LocalBottomNavigationInset.current),
            properties = UIKitInteropProperties(interactionMode = UIKitInteropInteractionMode.NonCooperative,
                isNativeAccessibilityEnabled = true, placedAsOverlay = true), update = { bar ->
            bar.tintColor = accent.toUiColor()
            bar.overrideUserInterfaceStyle = if (dark) UIUserInterfaceStyle.UIUserInterfaceStyleDark else UIUserInterfaceStyle.UIUserInterfaceStyleLight
            bar.unselectedItemTintColor = secondary.toUiColor()
            if (bar.items?.map { (it as UITabBarItem).tag } != items.map { it.tag }) bar.setItems(items, animated = false)
            bar.selectedItem = destinations.indexOf(selected).takeIf { it >= 0 }?.let { items[it] }
        })
    }
}
