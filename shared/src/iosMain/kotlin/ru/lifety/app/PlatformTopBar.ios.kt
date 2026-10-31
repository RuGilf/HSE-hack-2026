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
    val colors = MaterialTheme.colorScheme
    val accent = remember(colors.primary) { colors.primary.toUiColor() }
    val appearance = remember(colors.onSurface) {
        UINavigationBarAppearance().apply {
            configureWithTransparentBackground()
            titleTextAttributes = mapOf(NSForegroundColorAttributeName to colors.onSurface.toUiColor())
        }
    }
    val applied = remember { object { var appearance: UINavigationBarAppearance? = null } }
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
            if (bar.tintColor != accent) bar.tintColor = accent
            val style = if (dark) UIUserInterfaceStyle.UIUserInterfaceStyleDark else UIUserInterfaceStyle.UIUserInterfaceStyleLight
            if (bar.overrideUserInterfaceStyle != style) bar.overrideUserInterfaceStyle = style
            if (bar.topItem?.title != title) bar.topItem?.title = title
            if (applied.appearance !== appearance) {
                bar.standardAppearance = appearance
                bar.scrollEdgeAppearance = appearance
                bar.compactAppearance = appearance
                applied.appearance = appearance
            }
        }, onRelease = { bar ->
            applied.appearance = null
            bar.topItem?.leftBarButtonItem?.target = null
            bar.topItem?.rightBarButtonItem?.target = null
        })
}
