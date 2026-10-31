@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package ru.lifety.core.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.takeOrElse
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
    val surfaceColor = LocalNativeSurfaceColor.current.takeOrElse { MaterialTheme.colorScheme.background }
    val textColor = MaterialTheme.colorScheme.onSurface
    val raisedColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val surface = remember(surfaceColor) { surfaceColor.toUiColor() }
    val raised = remember(raisedColor) { raisedColor.toUiColor() }
    val fontScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val height = (32 * fontScale).dp
    val attributes = remember(textColor, fontScale) {
        val text = textColor.toUiColor()
        mapOf<Any?, Any>(NSForegroundColorAttributeName to text, NSFontAttributeName to UIFont.systemFontOfSize(13.0 * fontScale)) to
            mapOf<Any?, Any>(NSForegroundColorAttributeName to text, NSFontAttributeName to UIFont.systemFontOfSize(13.0 * fontScale, UIFontWeightSemibold))
    }
    // A separate remembered UIKit instance is unnecessary: UIKitView owns its lifetime.
    key(labels) {
        val applied = remember { object { var attributes: Any? = null } }
        Box(modifier.heightIn(min = 44.dp), contentAlignment = Alignment.Center) {
            UIKitView(factory = {
                val control = object : UISegmentedControl(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
                    @ObjCAction fun selectionChanged() { choose(selectedSegmentIndex.toInt()) }
                }.apply {
                    labels.forEachIndexed { index, label -> insertSegmentWithTitle(label, index.toULong(), animated = false) }
                    addTarget(this, NSSelectorFromString("selectionChanged"), UIControlEventValueChanged)
                }
                object : UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
                    init { addSubview(control) }
                    override fun layoutSubviews() { super.layoutSubviews(); control.setFrame(bounds) }
                }
            }, modifier = Modifier.fillMaxWidth().height(height),
                properties = UIKitInteropProperties(interactionMode = UIKitInteropInteractionMode.Cooperative(),
                    isNativeAccessibilityEnabled = true),
                update = { host ->
                    if (host.backgroundColor != surface) host.backgroundColor = surface
                    val control = host.subviews.first() as UISegmentedControl
                    if (control.enabled != enabled) control.enabled = enabled
                    val style = if (dark) UIUserInterfaceStyle.UIUserInterfaceStyleDark else UIUserInterfaceStyle.UIUserInterfaceStyleLight
                    if (control.overrideUserInterfaceStyle != style) control.overrideUserInterfaceStyle = style
                    if (control.selectedSegmentTintColor != raised) control.selectedSegmentTintColor = raised
                    if (applied.attributes !== attributes) {
                        control.setTitleTextAttributes(attributes.first, UIControlStateNormal)
                        control.setTitleTextAttributes(attributes.second, UIControlStateSelected)
                        applied.attributes = attributes
                    }
                    if (control.selectedSegmentIndex != selected.toLong()) control.selectedSegmentIndex = selected.toLong()
                }, onRelease = { host -> applied.attributes = null; (host.subviews.first() as UISegmentedControl).let { it.removeTarget(it, NSSelectorFromString("selectionChanged"), UIControlEventValueChanged) } })
        }
    }
}
