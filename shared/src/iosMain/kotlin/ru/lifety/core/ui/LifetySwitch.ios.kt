@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package ru.lifety.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.*
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSSelectorFromString
import platform.UIKit.*
import ru.lifety.core.appearance.toUiColor
import androidx.compose.ui.unit.DpSize

@Composable
internal actual fun LifetySwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier, enabled: Boolean) {
    val change by rememberUpdatedState(onCheckedChange)
    val accent = MaterialTheme.colorScheme.primary.toUiColor()
    // iOS 26 changed UISwitch dimensions. Never crop it to the old 51 × 31 size.
    val size = remember { UISwitch().intrinsicContentSize.useContents { DpSize(width.toFloat().dp, height.toFloat().dp) } }
    Box(modifier.sizeIn(minWidth = size.width, minHeight = maxOf(44.dp, size.height)), contentAlignment = Alignment.Center) {
        UIKitView(factory = {
            object : UISwitch(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
                @ObjCAction fun valueChanged() { change(on) }
            }.apply { addTarget(this, NSSelectorFromString("valueChanged"), UIControlEventValueChanged) }
        }, modifier = Modifier.size(size),
            properties = UIKitInteropProperties(interactionMode = UIKitInteropInteractionMode.Cooperative(),
                isNativeAccessibilityEnabled = true),
            update = { control -> control.enabled = enabled; control.onTintColor = accent; if (control.on != checked) control.setOn(checked, animated = false) },
            onRelease = { it.removeTarget(it, NSSelectorFromString("valueChanged"), UIControlEventValueChanged) })
    }
}
