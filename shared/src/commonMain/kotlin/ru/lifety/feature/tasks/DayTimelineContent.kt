package ru.lifety.feature.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import ru.lifety.core.model.Task
import ru.lifety.core.planner.*
import ru.lifety.core.time.formatDate
import ru.lifety.core.time.localDate
import ru.lifety.core.ui.LifetyIcon
import ru.lifety.core.ui.LifetySymbol
import kotlin.math.roundToInt

private data class TimelineRow(val at: Long, val task: TimedTask? = null, val free: TimeWindow? = null, val fixed: ScheduleOccurrence? = null)

internal fun LazyListScope.dayTimelineItems(timeline: DayTimeline, date: LocalDate, zone: TimeZone, now: Long,
    showFree: Boolean, busyIds: Set<String>, open: (Task) -> Unit, toggle: (Task) -> Unit, create: (Long) -> Unit,
    openSchedule: (String) -> Unit = {}) {
    item(key = "timeline-summary") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Расписание", style = MaterialTheme.typography.titleMedium)
            Text("Дел: ${timeline.tasks.size} · занятий: ${timeline.schedule.size} · занято ${timeline.busyMinutes / 60} ч ${timeline.busyMinutes % 60} мин в выбранных часах",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (localDate(now, zone) == date) Text("Сейчас ${formatPlannerTime(now, zone)}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            if (timeline.unknownDurations > 0) Text("У ${timeline.unknownDurations} дел нет длительности. Свободные окна рассчитаны без них — уточните оценку.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (timeline.tasks.any { it.overlaps }) Text("Есть пересечения — откройте задачу и измените время", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (timeline.schedule.isNotEmpty()) item(key = "day-grid") {
        var expanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Скрыть сетку времени" else "Показать сетку времени") }
            if (expanded) DayTimeGrid(timeline, date, zone, open, openSchedule, create)
        }
    }
    val rows = (timeline.tasks.map { TimelineRow(it.start, task = it) } + timeline.schedule.map { TimelineRow(it.start, fixed = it) } +
        if (showFree) availableTimeWindows(timeline.free, date, now, zone).filter { it.minutes >= 15 }.map { TimelineRow(it.start, free = it) } else emptyList()).sortedBy { it.at }
    items(rows, key = { it.task?.let { t -> "interval:${t.task.id}" } ?: it.fixed?.let { f -> "fixed:${f.block.id}:${f.start}" } ?: "free:${it.at}" }, contentType = { if (it.task != null) "interval" else if (it.fixed != null) "fixed" else "free" }) { row ->
        val item = row.task
        if (row.fixed != null) {
            val fixed = row.fixed
            Card(onClick = { openSchedule(fixed.block.id) }, modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${formatPlannerTime(fixed.start, zone)} — ${formatPlannerTime(fixed.end, zone)} · Расписание", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    Text(fixed.block.title, style = MaterialTheme.typography.titleMedium)
                    if (localDate(fixed.start, zone) != date || localDate(fixed.end, zone) != date)
                        Text("${formatDate(localDate(fixed.start, zone))} — ${formatDate(localDate(fixed.end, zone))}", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else if (item == null) {
            val free = requireNotNull(row.free)
            OutlinedCard(onClick = { create(free.start) }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("${formatPlannerTime(free.start, zone)} — ${if (localDate(free.end, zone) > date) "24:00" else formatPlannerTime(free.end, zone)}", style = MaterialTheme.typography.labelLarge)
                        Text("Свободно · ${free.minutes} мин", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    LifetyIcon(LifetySymbol.Plus, color = MaterialTheme.colorScheme.primary)
                }
            }
        } else {
            Card(onClick = { open(item.task) }, enabled = item.task.id !in busyIds, modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.width(74.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(formatPlannerTime(item.start, zone), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(item.end?.let { formatPlannerTime(it, zone) } ?: "—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(item.task.title, style = MaterialTheme.typography.titleMedium)
                        Text(item.end?.let { "${(it - item.start) / 60_000} мин" } ?: "Длительность не указана", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (localDate(item.start, zone) < date) Text("Начато ${formatDate(localDate(item.start, zone))}", style = MaterialTheme.typography.labelSmall)
                        item.end?.takeIf { localDate(it, zone) > date }?.let { Text("Окончание ${formatDate(localDate(it, zone))}", style = MaterialTheme.typography.labelSmall) }
                        if (item.overlaps) Text("Пересечение с задачей или расписанием", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        if (item.missesDeadline) Text("Окончание позже дедлайна", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        item.task.deadlineAt?.let { Text("Дедлайн ${formatDate(localDate(it, zone))} · ${formatPlannerTime(it, zone)}", style = MaterialTheme.typography.labelSmall) }
                    }
                    Checkbox(item.task.isCompleted, { toggle(item.task) }, enabled = item.task.id !in busyIds)
                }
            }
        }
    }
}

@Composable
internal fun TimelineHoursDialog(fromHour: Int, untilHour: Int, onSave: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    var from by remember { mutableFloatStateOf(fromHour.toFloat()) }
    var until by remember { mutableFloatStateOf(untilHour.toFloat()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Часы для свободных окон") }, text = {
        Column {
            Text("Дела за пределами этих часов останутся в расписании.", style = MaterialTheme.typography.bodySmall)
            Text("С ${from.roundToInt().toString().padStart(2, '0')}:00")
            Slider(from, { from = minOf(it, until - 1) }, valueRange = 0f..23f, steps = 22)
            Text("До ${until.roundToInt().toString().padStart(2, '0')}:00")
            Slider(until, { until = maxOf(it, from + 1) }, valueRange = 1f..24f, steps = 22)
        }
    }, confirmButton = { TextButton(onClick = { onSave(from.roundToInt(), until.roundToInt()) }) { Text("Готово") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}
