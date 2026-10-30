package ru.lifety.core.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal actual fun LifetySegmentedControl(labels: List<String>, selected: Int,
    onSelect: (Int) -> Unit, modifier: Modifier, enabled: Boolean) {
    SingleChoiceSegmentedButtonRow(modifier) {
        labels.forEachIndexed { index, label ->
            SegmentedButton(selected == index, { onSelect(index) },
                SegmentedButtonDefaults.itemShape(index, labels.size), enabled = enabled) { Text(label) }
        }
    }
}
