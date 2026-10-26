package ru.lifety.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.lifety.core.ui.LifetyMotion
import ru.lifety.feature.calendar.CalendarScreen
import ru.lifety.feature.tasks.TasksScreen

/** One primary Plan destination, with independently saved task and calendar UI state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(initialCalendar: Boolean = false, modifier: Modifier = Modifier) {
    var calendar by rememberSaveable { mutableStateOf(initialCalendar) }
    val stateHolder = rememberSaveableStateHolder()
    Column(modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            listOf("День", "Календарь").forEachIndexed { index, title ->
                SegmentedButton(selected = calendar == (index == 1), onClick = { calendar = index == 1 },
                    shape = SegmentedButtonDefaults.itemShape(index, 2), label = { Text(title) })
            }
        }
        AnimatedContent(calendar, modifier = Modifier.weight(1f), label = "planTab",
            transitionSpec = { fadeIn(tween(LifetyMotion.Standard)) togetherWith fadeOut(tween(LifetyMotion.Quick)) }) { showCalendar ->
            stateHolder.SaveableStateProvider(if (showCalendar) "calendar" else "tasks") {
                Box(Modifier.fillMaxSize()) {
                    if (showCalendar) CalendarScreen(Modifier.fillMaxSize()) else TasksScreen(Modifier.fillMaxSize())
                }
            }
        }
    }
}
