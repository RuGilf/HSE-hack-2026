package ru.lifety.feature.finance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.lifety.core.model.ExpenseCategory
import ru.lifety.core.ui.LifetyIcon
import ru.lifety.core.ui.LifetySymbol
import kotlin.uuid.Uuid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryManagerSheet(state: CategoryUiState, onSave: suspend (ExpenseCategory) -> Boolean,
    onRetry: () -> Unit, onDismiss: () -> Unit) {
    var editing by remember { mutableStateOf<ExpenseCategory?>(null) }
    var closing by remember { mutableStateOf(false) }
    val busy by rememberUpdatedState(state.saving || closing)
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !busy || closing })
    val close = { if (!busy) scope.launch { closing = true; sheet.hide(); onDismiss() }; Unit }
    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.9f).imePadding().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (editing != null) IconButton(onClick = { editing = null }, enabled = !busy,
                    modifier = Modifier.semantics { contentDescription = "К списку категорий" }) { LifetyIcon(LifetySymbol.Back) }
                Text(if (editing == null) "Категории" else if (editing!!.name.isEmpty()) "Новая категория" else "Изменить категорию",
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = close, enabled = !busy,
                    modifier = Modifier.semantics { contentDescription = "Закрыть категории" }) { LifetyIcon(LifetySymbol.Close) }
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (!state.loaded) TextButton(onClick = onRetry, enabled = !busy && !state.loading) { Text("Повторить загрузку") }
            }
            if (state.loading || state.saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            val selected = editing
            if (selected != null) key(selected.id) {
                CategoryDetails(selected, state.categories, enabled = !busy && state.loaded, onSave = {
                    scope.launch { if (onSave(it)) editing = null }
                })
            } else {
                Text("Названия и цвета можно менять. Скрытые категории остаются у старых расходов.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { editing = ExpenseCategory("custom:${Uuid.random()}", "", defaultExpenseCategories().first().colorHex) },
                    enabled = !busy && state.loaded, modifier = Modifier.fillMaxWidth()) {
                    LifetyIcon(LifetySymbol.Plus, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Добавить категорию")
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.categories, key = { it.id }) { category ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(16.dp).background(categoryHexColor(category.colorHex) ?: MaterialTheme.colorScheme.outline, CircleShape))
                                Column(Modifier.weight(1f)) {
                                    Text(category.name, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (category.archived) Text("Скрыта", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    TextButton(onClick = { editing = category }, enabled = !busy && state.loaded,
                                        contentPadding = PaddingValues(0.dp), modifier = Modifier.semantics { contentDescription = "Изменить категорию ${category.name}" }) { Text("Изменить") }
                                }
                                TextButton(onClick = { scope.launch { onSave(category.copy(archived = !category.archived)) } }, enabled = !busy && state.loaded,
                                    modifier = Modifier.semantics { contentDescription = "${if (category.archived) "Вернуть" else "Скрыть"} категорию ${category.name}" }) {
                                    Text(if (category.archived) "Вернуть" else "Скрыть")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.CategoryDetails(initial: ExpenseCategory, categories: List<ExpenseCategory>, enabled: Boolean,
    onSave: (ExpenseCategory) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var hex by rememberSaveable { mutableStateOf(initial.colorHex) }
    var attempted by remember { mutableStateOf(false) }
    val draft = initial.copy(name = name.trim(), colorHex = hex.uppercase())
    val error = categoryValidationError(draft, categories)
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(name, { name = it.take(40) }, enabled = enabled, singleLine = true,
            label = { Text("Название категории") }, modifier = Modifier.fillMaxWidth(), isError = attempted && name.isBlank())
        Text("Цвет категории", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            defaultExpenseCategories().forEach { preset ->
                val color = requireNotNull(categoryHexColor(preset.colorHex))
                val chosen = preset.colorHex.equals(hex, ignoreCase = true)
                Surface(onClick = { hex = preset.colorHex }, enabled = enabled, shape = CircleShape, color = color,
                    border = if (chosen) BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface) else null,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Цвет ${preset.colorHex}"; this.selected = chosen }) {
                    if (chosen) Box(contentAlignment = Alignment.Center) { LifetyIcon(LifetySymbol.Check, color = MaterialTheme.colorScheme.background) }
                }
            }
        }
        OutlinedTextField(hex, { hex = it.take(7) }, enabled = enabled, singleLine = true,
            label = { Text("HEX-цвет") }, supportingText = { Text("Свой цвет в формате #RRGGBB") },
            modifier = Modifier.fillMaxWidth(), isError = categoryHexColor(hex) == null)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(20.dp).background(categoryHexColor(hex) ?: MaterialTheme.colorScheme.outline, CircleShape))
            Text(name.ifBlank { "Новая категория" })
        }
        if (attempted && error != null) Text(error, color = MaterialTheme.colorScheme.error)
    }
    Button(onClick = { attempted = true; if (error == null) onSave(draft) }, enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp).height(48.dp)) { Text("Сохранить категорию") }
}
