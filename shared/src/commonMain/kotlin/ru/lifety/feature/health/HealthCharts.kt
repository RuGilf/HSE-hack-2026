package ru.lifety.feature.health

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.datetime.daysUntil
import ru.lifety.core.time.formatDate
import ru.lifety.core.ui.LifetyMotion
import kotlin.math.abs
import kotlin.math.ceil

@Composable
internal fun HealthChart(title: String, timeline: HealthTimeline, selected: Int,
    sleep: Boolean, onSelect: (Int) -> Unit) {
    val days = timeline.days
    val values = remember(days, sleep) { days.map { if (sleep) it.sleepMinutes else it.steps } }
    val known = values.filterNotNull()
    val upper = if (sleep) maxOf(480, ceil((known.maxOrNull() ?: 0) / 120.0).toInt() * 120)
        else maxOf(10_000, ceil((known.maxOrNull() ?: 0) / 1000.0).toInt() * 1000)
    val color = if (sleep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(days, sleep) { reveal.snapTo(0f); reveal.animateTo(1f, tween(LifetyMotion.Emphasis)) }
    fun axis(value: Int) = if (sleep) "${value / 60} ч" else if (value >= 1000) "${value / 1000} тыс." else value.toString()
    val average = if (sleep) sleepLabel(timeline.averageSleep) else timeline.averageSteps?.toString() ?: "Нет данных"
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text("${known.size} из ${timeline.calendarDays} дней", style = MaterialTheme.typography.labelMedium, color = outline)
            }
            Text(average, style = MaterialTheme.typography.headlineSmall)
            Text("В среднем за день с данными", style = MaterialTheme.typography.bodySmall, color = outline)
            if (known.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("Нет записей за этот период", color = outline, style = MaterialTheme.typography.bodyMedium)
                }
            } else Row(Modifier.fillMaxWidth().height(144.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.fillMaxHeight().width(44.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    listOf(upper, upper / 2, 0).forEach { Text(axis(it), style = MaterialTheme.typography.labelSmall, color = outline) }
                }
                val span = days.first().date.daysUntil(days.last().date).coerceAtLeast(1)
                val fractions = remember(days) { days.map { days.first().date.daysUntil(it.date).toFloat() / span } }
                Canvas(Modifier.weight(1f).fillMaxHeight().semantics {
                    contentDescription = "$title. Среднее: $average. ${known.size} дней с данными. Дни без данных не считаются нулём. Выбор дня доступен кнопками под графиками."
                }.pointerInput(days) {
                    detectTapGestures { position ->
                        val fraction = ((position.x - 8.dp.toPx()) / (size.width - 16.dp.toPx()).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        fractions.indices.minByOrNull { abs(fractions[it] - fraction) }?.let(onSelect)
                    }
                }) {
                    val left = 8.dp.toPx(); val width = size.width - left * 2
                    val top = 8.dp.toPx(); val height = size.height - top * 2
                    listOf(0f, .5f, 1f).forEach { fraction ->
                        val y = top + height * fraction
                        drawLine(grid, Offset(left, y), Offset(left + width, y), 1.dp.toPx())
                    }
                    clipRect(right = size.width * reveal.value) {
                        var previous: Offset? = null
                        values.forEachIndexed { index, value ->
                            val x = left + width * fractions[index]
                            if (value == null) {
                                previous = null
                                drawLine(outline.copy(alpha = .45f), Offset(x - 2.dp.toPx(), top + height), Offset(x + 2.dp.toPx(), top + height), 2.dp.toPx())
                            } else {
                                val point = Offset(x, top + height * (1f - value.toFloat() / upper))
                                if (index > 0 && days[index - 1].date.daysUntil(days[index].date) > 1) previous = null
                                previous?.let { drawLine(color, it, point, 2.dp.toPx(), StrokeCap.Round) }
                                drawCircle(color, if (values.size <= 31) 3.dp.toPx() else 1.5.dp.toPx(), point)
                                if (index == selected) {
                                    drawLine(color.copy(alpha = .25f), Offset(x, top), Offset(x, top + height), 1.dp.toPx())
                                    drawCircle(color.copy(alpha = .2f), 8.dp.toPx(), point)
                                    drawCircle(color, 4.dp.toPx(), point)
                                }
                                previous = point
                            }
                        }
                    }
                }
            }
            if (days.isNotEmpty()) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDate(days.first().date), style = MaterialTheme.typography.labelSmall, color = outline)
                Text(formatDate(days.last().date), style = MaterialTheme.typography.labelSmall, color = outline)
            }
        }
    }
}
