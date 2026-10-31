package ru.lifety.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import ru.lifety.core.ui.isIos
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
import ru.lifety.feature.schedule.ScheduleScreen

/** One primary Plan destination, with independently saved day, calendar and people UI state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(initialCalendar: Boolean = false, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf(if (initialCalendar) 1 else 0) }
    val stateHolder = rememberSaveableStateHolder()
    Column(modifier.fillMaxSize()) {
        ru.lifety.core.ui.LifetySegmentedControl(listOf("День", "Календарь", "Люди", "Расписание"), tab, { tab = it },
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
        AnimatedContent(tab, modifier = Modifier.weight(1f), label = "planTab",
            transitionSpec = { if (isIos) EnterTransition.None togetherWith ExitTransition.None else fadeIn(tween(LifetyMotion.Standard)) togetherWith fadeOut(tween(LifetyMotion.Quick)) }) { selectedTab ->
            stateHolder.SaveableStateProvider(listOf("tasks", "calendar", "people", "schedule")[selectedTab]) {
                Box(Modifier.fillMaxSize()) {
                    when (selectedTab) {
                        1 -> CalendarScreen(Modifier.fillMaxSize())
                        2 -> PeopleScreen(Modifier.fillMaxSize())
                        3 -> ScheduleScreen(Modifier.fillMaxSize())
                        else -> TasksScreen(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
