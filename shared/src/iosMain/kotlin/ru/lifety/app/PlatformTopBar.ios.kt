@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package ru.lifety.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.*
import kotlinx.cinterop.ObjCAction
import platform.darwin.NSObject
import platform.Foundation.NSSelectorFromString
import platform.UIKit.*
import ru.lifety.core.appearance.toUiColor

@Composable
internal actual fun PlatformTopBar(title: String, showMascot: Boolean, onSections: () -> Unit, onSettings: () -> Unit) {
    val sections by rememberUpdatedState(onSections)
    val settings by rememberUpdatedState(onSettings)
    val actions = remember { object : NSObject() {
        @ObjCAction fun openSections() { sections() }
        @ObjCAction fun openSettings() { settings() }
    } }
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val accent = MaterialTheme.colorScheme.primary.toUiColor()
    val text = MaterialTheme.colorScheme.onSurface.toUiColor()
    UIKitView(factory = {
        UINavigationBar().apply {
            translucent = true
            backgroundColor = UIColor.clearColor
            opaque = false
            setItems(listOf(UINavigationItem(title).apply {
                leftBarButtonItem = UIBarButtonItem(UIImage.systemImageNamed("square.grid.2x2"), UIBarButtonItemStyle.UIBarButtonItemStylePlain,
                    actions, NSSelectorFromString("openSections")).apply { accessibilityLabel = "Все разделы" }
                rightBarButtonItem = UIBarButtonItem(UIImage.systemImageNamed("gearshape"), UIBarButtonItemStyle.UIBarButtonItemStylePlain,
                    actions, NSSelectorFromString("openSettings")).apply { accessibilityLabel = "Настройки" }
            }), animated = false)
        }
    }, modifier = Modifier.fillMaxWidth().statusBarsPadding().height(44.dp),
        properties = UIKitInteropProperties(interactionMode = UIKitInteropInteractionMode.NonCooperative,
            isNativeAccessibilityEnabled = true, placedAsOverlay = true),
        update = { bar ->
            bar.tintColor = accent
            bar.overrideUserInterfaceStyle = if (dark) UIUserInterfaceStyle.UIUserInterfaceStyleDark else UIUserInterfaceStyle.UIUserInterfaceStyleLight
            bar.topItem?.title = title
            val appearance = UINavigationBarAppearance().apply {
                configureWithTransparentBackground()
                titleTextAttributes = mapOf(NSForegroundColorAttributeName to text)
            }
            bar.standardAppearance = appearance
            bar.scrollEdgeAppearance = appearance
            bar.compactAppearance = appearance
        }, onRelease = { bar ->
            bar.topItem?.leftBarButtonItem?.target = null
            bar.topItem?.rightBarButtonItem?.target = null
        })
}
