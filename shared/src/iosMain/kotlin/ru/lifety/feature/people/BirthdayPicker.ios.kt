@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
package ru.lifety.feature.people

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import kotlinx.datetime.number
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import platform.darwin.NSObject
import platform.UIKit.*
import ru.lifety.core.time.russianMonths
import ru.lifety.core.time.today

/** Native wheel picker, including an absent year as in the iOS Contacts birthday editor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal actual fun BirthdayPickerDialog(initial: BirthdaySelection?, onConfirm: (BirthdaySelection) -> Unit, onDismiss: () -> Unit) {
    val currentDay = today()
    var day by rememberSaveable { mutableStateOf(initial?.day ?: currentDay.day) }
    var month by rememberSaveable { mutableStateOf(initial?.month ?: currentDay.month.number) }
    var year by rememberSaveable { mutableStateOf(initial?.year) }
    val selection = BirthdaySelection(day, month, year)
    val current by rememberUpdatedState(selection)
    val choose by rememberUpdatedState<(BirthdaySelection) -> Unit>({ value ->
        val clamped = clampBirthday(value)
        day = clamped.day; month = clamped.month; year = clamped.year
    })
    val delegate = remember(currentDay.year) { object : NSObject(), UIPickerViewDataSourceProtocol, UIPickerViewDelegateProtocol {
        override fun numberOfComponentsInPickerView(pickerView: UIPickerView): Long = 3
        @ObjCSignatureOverride
        override fun pickerView(pickerView: UIPickerView, numberOfRowsInComponent: Long): Long = when (numberOfRowsInComponent.toInt()) {
            0 -> birthdayMonthDays(current.month, current.year).toLong()
            1 -> 12L
            else -> currentDay.year.toLong() + 1
        }
        @ObjCSignatureOverride
        override fun pickerView(pickerView: UIPickerView, widthForComponent: Long): Double =
            pickerView.bounds.useContents { size.width } * when (widthForComponent.toInt()) { 0 -> 0.18; 1 -> 0.46; else -> 0.36 }
        @ObjCSignatureOverride
        override fun pickerView(pickerView: UIPickerView, titleForRow: Long, forComponent: Long): String = when (forComponent.toInt()) {
            0 -> (titleForRow + 1).toString()
            1 -> russianMonths[titleForRow.toInt()]
            else -> if (titleForRow == 0L) "Без года" else (currentDay.year - titleForRow + 1).toString()
        }
        @ObjCSignatureOverride
        override fun pickerView(pickerView: UIPickerView, didSelectRow: Long, inComponent: Long) {
            choose(when (inComponent.toInt()) {
                0 -> current.copy(day = didSelectRow.toInt() + 1)
                1 -> current.copy(month = didSelectRow.toInt() + 1)
                else -> current.copy(year = if (didSelectRow == 0L) null else currentDay.year - didSelectRow.toInt() + 1)
            })
            pickerView.reloadComponent(0)
        }
    } }
    val error = birthdaySelectionError(selection, currentDay)
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                Text("День рождения", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp))
                Text("Если год неизвестен, выберите «Без года».", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                UIKitView(factory = {
                    UIPickerView().apply {
                        this.dataSource = delegate; this.delegate = delegate
                        overrideUserInterfaceStyle = UIUserInterfaceStyle.UIUserInterfaceStyleDark
                        backgroundColor = UIColor.clearColor
                    }
                }, modifier = Modifier.fillMaxWidth().height(216.dp),
                    properties = UIKitInteropProperties(interactionMode = UIKitInteropInteractionMode.NonCooperative, isNativeAccessibilityEnabled = true, placedAsOverlay = true),
                    update = { picker ->
                        picker.reloadComponent(0)
                        val rows = listOf((day - 1).toLong(), (month - 1).toLong(), year?.let { (currentDay.year - it + 1).toLong() } ?: 0L)
                        rows.forEachIndexed { index, row -> if (picker.selectedRowInComponent(index.toLong()) != row) picker.selectRow(row, index.toLong(), animated = false) }
                    }, onRelease = { picker -> picker.delegate = null; picker.dataSource = null })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 24.dp)) }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Отмена") }
                    TextButton(onClick = { onConfirm(selection) }, enabled = error == null) { Text("Готово") }
                }
            }
        }
    }
}
