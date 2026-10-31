@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package ru.lifety.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.*
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSSelectorFromString
import platform.UIKit.*
import ru.lifety.core.appearance.toUiColor

@Composable
internal actual fun LifetySwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier, enabled: Boolean) {
    val change by rememberUpdatedState(onCheckedChange)
    val accent = MaterialTheme.colorScheme.primary.toUiColor()
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val surface = LocalNativeSurfaceColor.current.takeOrElse { MaterialTheme.colorScheme.background }.toUiColor()
    // UISwitch's final style/size is resolved after it joins the native window.
    // A padded UIView hosts its natural size; the Metal cut-out never crops its rounded edge.
    UIKitView(factory = {
        val control = object : UISwitch(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
            @ObjCAction fun valueChanged() { change(on) }
        }.apply { addTarget(this, NSSelectorFromString("valueChanged"), UIControlEventValueChanged) }
        object : UIView(frame = CGRectMake(0.0, 0.0, 80.0, 44.0)) {
            init { addSubview(control) }
            override fun layoutSubviews() {
                super.layoutSubviews()
                control.sizeToFit()
                val size = control.bounds.useContents { size.width to size.height }
                bounds.useContents {
                    control.setFrame(CGRectMake((this.size.width - size.first) / 2, (this.size.height - size.second) / 2, size.first, size.second))
                }
            }
        }
    }, modifier = modifier.size(80.dp, 44.dp),
        properties = UIKitInteropProperties(interactionMode = UIKitInteropInteractionMode.Cooperative(), isNativeAccessibilityEnabled = true),
        update = { host ->
            if (host.backgroundColor != surface) host.backgroundColor = surface
            val style = if (dark) UIUserInterfaceStyle.UIUserInterfaceStyleDark else UIUserInterfaceStyle.UIUserInterfaceStyleLight
            if (host.overrideUserInterfaceStyle != style) host.overrideUserInterfaceStyle = style
            val control = host.subviews.first() as UISwitch
            if (control.enabled != enabled) control.enabled = enabled
            if (control.onTintColor != accent) control.onTintColor = accent
            if (control.on != checked) control.setOn(checked, animated = false)
        }, onRelease = { host -> (host.subviews.first() as UISwitch).let { it.removeTarget(it, NSSelectorFromString("valueChanged"), UIControlEventValueChanged) } })
}
