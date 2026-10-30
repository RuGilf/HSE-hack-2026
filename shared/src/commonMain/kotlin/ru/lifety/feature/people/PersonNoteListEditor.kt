package ru.lifety.feature.people

import ru.lifety.core.ui.LifetyTextField

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import ru.lifety.core.ui.LifetyIcon
import ru.lifety.core.ui.LifetySymbol

@Composable
internal fun PersonNoteListEditor(title: String, itemLabel: String, addLabel: String, hint: String,
    items: List<String>, enabled: Boolean, onChange: (List<String>) -> Unit) {
    val focus = LocalFocusManager.current
    val lastItemFocus = remember { FocusRequester() }
    var focusAddedItem by remember { mutableStateOf(false) }
    LaunchedEffect(items.size) {
        if (focusAddedItem && items.isNotEmpty()) { lastItemFocus.requestFocus(); focusAddedItem = false }
    }
    fun add() { focusAddedItem = true; onChange(items + "") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        items.forEachIndexed { index, value ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LifetyTextField(value, { onChange(replacePersonNoteItem(items, index, it.take(10_000))) },
                    label = { Text("$itemLabel ${index + 1}") }, placeholder = { Text(hint) },
                    modifier = Modifier.weight(1f).then(if (index == items.lastIndex) Modifier.focusRequester(lastItemFocus) else Modifier),
                    enabled = enabled, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { if (index == items.lastIndex) add() else focus.moveFocus(FocusDirection.Down) }))
                IconButton(onClick = { onChange(items.filterIndexed { i, _ -> i != index }) }, enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = "Удалить пункт $title: ${value.ifBlank { (index + 1).toString() }}" }) {
                    LifetyIcon(LifetySymbol.Close, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        TextButton(onClick = ::add, enabled = enabled) {
            LifetyIcon(LifetySymbol.Plus, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(addLabel)
        }
    }
}
