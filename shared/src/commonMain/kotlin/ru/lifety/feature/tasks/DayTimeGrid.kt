package ru.lifety.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import ru.lifety.core.model.Task
import ru.lifety.core.planner.*
import ru.lifety.core.time.localDate

private data class GridBlock(val id: String, val title: String, val start: Long, val end: Long, val conflict: Boolean = false)
private data class PlacedBlock(val block: GridBlock, val lane: Int)

/** Independent columns keep fixed commitments visible even when a task conflicts with them. */
@Composable
internal fun DayTimeGrid(timeline: DayTimeline, date: LocalDate, zone: TimeZone, open: (Task) -> Unit, openSchedule: (String) -> Unit, create: (Long) -> Unit) {
    val window = timeline.window ?: return
    val height = (window.minutes * 1.2f).dp
    fun top(at: Long) = (((at - window.start) / 60_000f) * 1.2f).dp
    val fixed = timeline.schedule.map { GridBlock("${it.block.id}:${it.start}", it.block.title, it.start, it.end) }
    val tasks = timeline.tasks.map { GridBlock(it.task.id, it.task.title, it.start, it.end ?: it.start + 15 * 60_000L, it.overlaps) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Расписание", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Задачи", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Box(Modifier.fillMaxWidth().height(height + 18.dp)) {
            var tick = window.start
            while (tick <= window.end) {
                val at = tick
                Row(Modifier.fillMaxWidth().offset(y = top(at))) {
                    Text(if (localDate(at, zone) > date) "24:00" else formatPlannerTime(at, zone), Modifier.width(48.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
                }
                tick += 60 * 60_000L
            }
            Row(Modifier.fillMaxWidth().padding(start = 48.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GridColumn(fixed, window, Modifier.weight(1f).height(height), false, zone, null) { id -> openSchedule(id.substringBeforeLast(':')) }
                GridColumn(tasks, window, Modifier.weight(1f).height(height), true, zone, create) { id -> timeline.tasks.find { it.task.id == id }?.task?.let(open) }
            }
        }
        Text("Коснитесь записи, чтобы изменить её, или свободного часа справа, чтобы добавить дело. Дела без длительности отмечены коротким блоком.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GridColumn(blocks: List<GridBlock>, window: TimeWindow, modifier: Modifier, tasks: Boolean, zone: TimeZone, create: ((Long) -> Unit)?, onOpen: (String) -> Unit) {
    val visible = blocks.filter { it.start < window.end && it.end > window.start }.sortedWith(compareBy<GridBlock> { it.start }.thenBy { it.id })
    val laneEnds = mutableListOf<Long>()
    val placed = visible.map { block ->
        val free = laneEnds.indexOfFirst { it <= block.start }
        val lane = if (free < 0) laneEnds.size.also { laneEnds += block.end } else free.also { laneEnds[it] = block.end }
        PlacedBlock(block, lane)
    }
    val lanes = laneEnds.size.coerceAtLeast(1)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(lanes) { lane ->
            Box(Modifier.weight(1f).fillMaxHeight()) {
                if (create != null) {
                    var hour = window.start
                    while (hour < window.end) {
                        val at = hour
                        Box(Modifier.fillMaxWidth().offset(y = ((at - window.start) / 60_000f * 1.2f).dp)
                            .height((minOf(60L, (window.end - at) / 60_000) * 1.2f).dp)
                            .clickable { create(at) }.semantics { contentDescription = "Добавить дело в ${formatPlannerTime(at, zone)}" })
                        hour += 60 * 60_000L
                    }
                }
                placed.filter { it.lane == lane }.forEach { (block, _) ->
                    val from = maxOf(window.start, block.start)
                    val until = minOf(window.end, block.end)
                    val color = when { block.conflict -> MaterialTheme.colorScheme.errorContainer; tasks -> MaterialTheme.colorScheme.primaryContainer; else -> MaterialTheme.colorScheme.surfaceContainerHigh }
                    val content = when { block.conflict -> MaterialTheme.colorScheme.onErrorContainer; tasks -> MaterialTheme.colorScheme.onPrimaryContainer; else -> MaterialTheme.colorScheme.onSurface }
                    Surface(onClick = { onOpen(block.id) }, modifier = Modifier.fillMaxWidth()
                        .offset(y = ((from - window.start) / 60_000f * 1.2f).dp)
                        .height(((until - from) / 60_000f * 1.2f).dp), shape = RoundedCornerShape(8.dp), color = color, contentColor = content) {
                        Row {
                            Box(Modifier.width(3.dp).fillMaxHeight().background(if (tasks) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary))
                            Text(block.title, Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall,
                                maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
