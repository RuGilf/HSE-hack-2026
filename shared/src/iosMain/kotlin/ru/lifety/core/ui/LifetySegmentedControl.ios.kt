@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package ru.lifety.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.*
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSSelectorFromString
import platform.UIKit.*
import platform.CoreGraphics.CGRectMake
import ru.lifety.core.appearance.toUiColor

@Composable
internal actual fun LifetySegmentedControl(labels: List<String>, selected: Int,
    onSelect: (Int) -> Unit, modifier: Modifier, enabled: Boolean) {
    require(labels.isNotEmpty())
    val choose by rememberUpdatedState(onSelect)
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val text = MaterialTheme.colorScheme.onSurface.toUiColor()
    val raised = MaterialTheme.colorScheme.surfaceContainerHighest.toUiColor()
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val height = (32 * fontScale).dp
    // A separate remembered UIKit instance is unnecessary: UIKitView owns its lifetime.
    key(labels) {
        Box(modifier.heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
            UIKitView(factory = {
                object : UISegmentedControl(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
                    @ObjCAction fun selectionChanged() { choose(selectedSegmentIndex.toInt()) }
                }.apply {
                    labels.forEachIndexed { index, label -> insertSegmentWithTitle(label, index.toULong(), animated = false) }
                    addTarget(this, NSSelectorFromString("selectionChanged"), UIControlEventValueChanged)
                }
            }, modifier = Modifier.fillMaxWidth().height(height),
                properties = UIKitInteropProperties(interactionMode = UIKitInteropInteractionMode.Cooperative(),
                    isNativeAccessibilityEnabled = true),
                update = { control ->
                    control.enabled = enabled
                    control.overrideUserInterfaceStyle = if (dark) UIUserInterfaceStyle.UIUserInterfaceStyleDark else UIUserInterfaceStyle.UIUserInterfaceStyleLight
                    control.selectedSegmentTintColor = raised
                    control.setTitleTextAttributes(mapOf(NSForegroundColorAttributeName to text, NSFontAttributeName to UIFont.systemFontOfSize(13.0 * fontScale)), UIControlStateNormal)
                    control.setTitleTextAttributes(mapOf(NSForegroundColorAttributeName to text, NSFontAttributeName to UIFont.systemFontOfSize(13.0 * fontScale, UIFontWeightSemibold)), UIControlStateSelected)
                    if (control.selectedSegmentIndex != selected.toLong()) control.selectedSegmentIndex = selected.toLong()
                }, onRelease = { it.removeTarget(it, NSSelectorFromString("selectionChanged"), UIControlEventValueChanged) })
        }
    }
}
