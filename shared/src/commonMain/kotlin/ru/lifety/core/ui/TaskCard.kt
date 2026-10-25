package ru.lifety.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import ru.lifety.core.model.Task
import ru.lifety.core.time.formatDateTime
import kotlin.time.Clock

@Composable
fun TaskCard(task: Task, busy: Boolean, onOpen: () -> Unit, onToggle: () -> Unit, onDelete: (() -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    val titleColor by animateColorAsState(
        if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        tween(LifetyMotion.Standard), label = "taskCompletion",
    )
    Card(onClick = onOpen, enabled = !busy, modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = task.isCompleted, enabled = !busy, onCheckedChange = { onToggle() },
                modifier = Modifier.semantics { contentDescription = "Выполнение задачи ${task.title}" })
            Column(Modifier.weight(1f).padding(top = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                    color = titleColor)
                task.scheduledAt?.let { Text("План · ${formatDateTime(it)}", style = MaterialTheme.typography.bodySmall) }
                task.deadlineAt?.let {
                    val overdue = !task.isCompleted && it < Clock.System.now().toEpochMilliseconds()
                    Text("${if (overdue) "Просрочено" else "Дедлайн"} · ${formatDateTime(it)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (task.scheduledAt == null && task.deadlineAt == null) {
                    Text("Без даты", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (onDelete != null) {
                Column {
                    IconButton(onClick = { menu = true }, enabled = !busy,
                        modifier = Modifier.semantics { contentDescription = "Действия с задачей ${task.title}" }) { LifetyIcon(LifetySymbol.More) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Редактировать") }, onClick = { menu = false; onOpen() })
                        DropdownMenuItem(text = { Text("Удалить", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
                    }
                }
            }
        }
    }
}
