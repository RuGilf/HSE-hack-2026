package ru.lifety.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.lifety.app.navigation.AppDestination
import ru.lifety.app.navigation.RootNavigator
import ru.lifety.core.repository.Repositories
import ru.lifety.core.ui.LocalRepositories
import ru.lifety.feature.assistant.AssistantScreen
import ru.lifety.feature.calendar.CalendarScreen
import ru.lifety.feature.dashboard.DashboardScreen
import ru.lifety.feature.finance.FinanceScreen
import ru.lifety.feature.health.HealthScreen
import ru.lifety.feature.tasks.TasksScreen

@Composable
fun LifetyApp(repositories: Repositories? = null) {
    val navigator = rememberSaveable(
        saver = Saver<RootNavigator, String>(
            save = { it.destination.value.route },
            restore = { RootNavigator(AppDestination.fromRoute(it)) },
        ),
    ) { RootNavigator() }
    val destination by navigator.destination.collectAsState()
    MaterialTheme {
        CompositionLocalProvider(LocalRepositories provides repositories) {
            Scaffold(
                bottomBar = {
                    Row(
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                            .horizontalScroll(rememberScrollState()).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AppDestination.entries.forEach { item ->
                            FilterChip(
                                selected = destination == item,
                                onClick = { navigator.navigate(item) },
                                label = { Text(item.title) },
                            )
                        }
                    }
                },
            ) { padding ->
                val modifier = Modifier.padding(padding)
                when (destination) {
                    AppDestination.DASHBOARD -> DashboardScreen(modifier)
                    AppDestination.TASKS -> TasksScreen(modifier)
                    AppDestination.CALENDAR -> CalendarScreen(modifier)
                    AppDestination.FINANCE -> FinanceScreen(modifier)
                    AppDestination.ASSISTANT -> AssistantScreen(modifier)
                    AppDestination.HEALTH -> HealthScreen(modifier)
                }
            }
        }
    }
}
