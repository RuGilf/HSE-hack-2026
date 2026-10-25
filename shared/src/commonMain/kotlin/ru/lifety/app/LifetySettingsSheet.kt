package ru.lifety.app

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.lifety.core.ui.LifetyColors
import ru.lifety.core.ui.LifetyIcon
import ru.lifety.core.ui.LifetyMascot
import ru.lifety.core.ui.LifetySymbol

/** Only existing controls and truthful local information, without placeholder account toggles. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifetySettingsSheet(onDismiss: () -> Unit, onFinance: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Настройки", style = MaterialTheme.typography.headlineMedium)
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LifetyMascot(Modifier.size(56.dp), animated = false)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Неоновая тёмная", style = MaterialTheme.typography.titleMedium)
                        Text("Единый стиль Lifety", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(LifetyColors.Background, LifetyColors.Surface, LifetyColors.Accent).forEach { color ->
                                androidx.compose.foundation.layout.Box(Modifier.size(14.dp).background(color, CircleShape))
                            }
                        }
                    }
                }
            }
            Card(onClick = onFinance, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LifetyIcon(LifetySymbol.Wallet, color = MaterialTheme.colorScheme.primary)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Банковские уведомления", style = MaterialTheme.typography.titleMedium)
                        Text("Доступ и подтверждение — в финансах", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text("Lifety · твой день в одном контексте", style = MaterialTheme.typography.titleSmall)
            Text("Задачи и расходы хранятся на устройстве. Чтение банковских уведомлений включается только по твоему выбору.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Готово") }
        }
    }
}
