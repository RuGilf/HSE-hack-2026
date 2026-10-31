@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package ru.lifety.core.ui

import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.*
import kotlinx.cinterop.ObjCAction
import platform.Foundation.*
import platform.CoreGraphics.CGRectMake
import platform.UIKit.*
import ru.lifety.core.appearance.toUiColor
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlinx.datetime.*

/** Compact UIKit controls present their own calendar/wheels; no extra Compose modal. */
@Composable
internal fun IosDatePicker(value: Long, mode: UIDatePickerMode, label: String,
    modifier: Modifier = Modifier, enabled: Boolean = true, useUtc: Boolean = false,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceContainerLow, onChange: (Long) -> Unit) {
    val changed by rememberUpdatedState(onChange)
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val accent = MaterialTheme.colorScheme.primary.toUiColor()
    UIKitView(factory = {
        object : UIDatePicker(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
            @ObjCAction fun valueChanged() {
                changed((date.timeIntervalSince1970 * 1000).roundToLong())
            }
        }.apply {
            datePickerMode = mode
            preferredDatePickerStyle = UIDatePickerStyle.UIDatePickerStyleCompact
            calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian).apply {
                locale = NSLocale(localeIdentifier = "ru_RU")
                firstWeekday = 2uL
            }
            locale = NSLocale(localeIdentifier = "ru_RU")
            timeZone = if (useUtc) NSTimeZone.timeZoneForSecondsFromGMT(0) else NSTimeZone.localTimeZone
            minuteInterval = 1
            if (mode != UIDatePickerMode.UIDatePickerModeTime) {
                val zone = if (useUtc) TimeZone.UTC else TimeZone.currentSystemDefault()
                minimumDate = NSDate.dateWithTimeIntervalSince1970(LocalDate(1900, 1, 1).atStartOfDayIn(zone).toEpochMilliseconds() / 1000.0)
                maximumDate = NSDate.dateWithTimeIntervalSince1970(LocalDateTime(9999, 12, 31, 23, 59).toInstant(zone).toEpochMilliseconds() / 1000.0)
            }
            contentHorizontalAlignment = UIControlContentHorizontalAlignmentRight
            addTarget(this, NSSelectorFromString("valueChanged"), UIControlEventValueChanged)
        }
    }, modifier = modifier.height(44.dp),
        properties = UIKitInteropProperties(interactionMode = UIKitInteropInteractionMode.Cooperative(),
            isNativeAccessibilityEnabled = true),
        update = { picker ->
            val background = backgroundColor.toUiColor()
            if (picker.backgroundColor != background) picker.backgroundColor = background
            if (picker.enabled != enabled) picker.enabled = enabled
            if (picker.accessibilityLabel != label) picker.accessibilityLabel = label
            if (picker.tintColor != accent) picker.tintColor = accent
            val style = if (dark) UIUserInterfaceStyle.UIUserInterfaceStyleDark else UIUserInterfaceStyle.UIUserInterfaceStyleLight
            if (picker.overrideUserInterfaceStyle != style) picker.overrideUserInterfaceStyle = style
            if (abs(picker.date.timeIntervalSince1970 - value / 1000.0) >= .001) {
                picker.setDate(NSDate.dateWithTimeIntervalSince1970(value / 1000.0), animated = false)
            }
        }, onRelease = { it.removeTarget(it, NSSelectorFromString("valueChanged"), UIControlEventValueChanged) })
}
