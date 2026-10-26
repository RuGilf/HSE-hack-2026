package ru.lifety.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.lifety.core.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifetySettingsSheet(onDismiss: () -> Unit, onFinance: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Настройки", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { scope.launch { sheet.hide(); onDismiss() } }, modifier = Modifier.semantics { contentDescription = "Закрыть настройки" }) { LifetyIcon(LifetySymbol.Close) }
            }
            ListItem(
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                leadingContent = { LifetyMascot(Modifier.size(40.dp), animated = false) },
                headlineContent = { Text("Lifety") }, supportingContent = { Text("Тёмная тема · зелёный акцент") },
            )
            ListItem(
                modifier = Modifier.clickable { scope.launch { sheet.hide(); onFinance() } },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                leadingContent = { LifetyIcon(LifetySymbol.Wallet) },
                headlineContent = { Text(if (isIos) "Финансы" else "Банковские уведомления") }, supportingContent = { Text(if (isIos) "Расходы, категории и бюджет" else "Выбор банков и доступ — в финансах") },
                trailingContent = { LifetyIcon(LifetySymbol.Chevron) },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text("Задачи, расходы и показатели здоровья хранятся на устройстве. Данные для ИИ выбираются отдельно перед отправкой.",
                modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
