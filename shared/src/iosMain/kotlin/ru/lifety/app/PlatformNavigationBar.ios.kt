@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package ru.lifety.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.unit.dp
import platform.darwin.NSObject
import platform.UIKit.*
import ru.lifety.app.navigation.AppDestination
import ru.lifety.core.ui.LocalBottomNavigationInset

/** UIKit supplies SF Symbols, VoiceOver selection and the native tab-bar appearance. */
@Composable
internal actual fun PlatformNavigationBar(selected: AppDestination, onSelect: (AppDestination) -> Unit) {
    val currentSelect by rememberUpdatedState(onSelect)
    val delegate = remember { object : NSObject(), UITabBarDelegateProtocol {
        override fun tabBar(tabBar: UITabBar, didSelectItem: UITabBarItem) {
            primarySections.getOrNull(didSelectItem.tag.toInt())?.let { currentSelect(it) }
        }
    } }
    val symbols = listOf("house", "checklist", "creditcard", "heart", "bubble.left.and.bubble.right")
    val items = remember { primarySections.mapIndexed { index, destination ->
        UITabBarItem(title = destination.title, image = UIImage.systemImageNamed(symbols[index]), tag = index.toLong()).apply {
            accessibilityLabel = destination.title
        }
    } }
    Box(Modifier.fillMaxWidth()) {
        UIKitView(factory = {
            UITabBar().apply {
                this.delegate = delegate
                setItems(items, animated = false)
                tintColor = UIColor(red = 53.0 / 255, green = 239.0 / 255, blue = 112.0 / 255, alpha = 1.0)
                unselectedItemTintColor = UIColor.secondaryLabelColor
                val appearance = UITabBarAppearance().apply { configureWithDefaultBackground() }
                standardAppearance = appearance
                scrollEdgeAppearance = appearance
                overrideUserInterfaceStyle = UIUserInterfaceStyle.UIUserInterfaceStyleDark
                backgroundColor = UIColor.clearColor
                opaque = false
            }
        }, modifier = Modifier.fillMaxWidth().height(LocalBottomNavigationInset.current),
            properties = UIKitInteropProperties(interactionMode = UIKitInteropInteractionMode.NonCooperative,
                isNativeAccessibilityEnabled = true, placedAsOverlay = true), update = { bar ->
            bar.selectedItem = items[primarySections.indexOf(selected).coerceAtLeast(0)]
        })
    }
}
