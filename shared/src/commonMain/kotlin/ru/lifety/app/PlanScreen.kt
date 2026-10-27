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
import ru.lifety.feature.people.PeopleScreen

/** One primary Plan destination, with independently saved day, calendar and people UI state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(initialCalendar: Boolean = false, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf(if (initialCalendar) 1 else 0) }
    val stateHolder = rememberSaveableStateHolder()
    Column(modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            listOf("День", "Календарь", "Люди").forEachIndexed { index, title ->
                SegmentedButton(selected = tab == index, onClick = { tab = index },
                    shape = SegmentedButtonDefaults.itemShape(index, 3), label = { Text(title) })
            }
        }
        AnimatedContent(tab, modifier = Modifier.weight(1f), label = "planTab",
            transitionSpec = { fadeIn(tween(LifetyMotion.Standard)) togetherWith fadeOut(tween(LifetyMotion.Quick)) }) { selectedTab ->
            stateHolder.SaveableStateProvider(listOf("tasks", "calendar", "people")[selectedTab]) {
                Box(Modifier.fillMaxSize()) {
                    when (selectedTab) {
                        1 -> CalendarScreen(Modifier.fillMaxSize())
                        2 -> PeopleScreen(Modifier.fillMaxSize())
                        else -> TasksScreen(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
