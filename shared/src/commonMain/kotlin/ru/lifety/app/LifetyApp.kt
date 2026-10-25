package ru.lifety.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import ru.lifety.app.navigation.AppDestination
import ru.lifety.app.navigation.LocalRootNavigator
import ru.lifety.app.navigation.RootNavigator
import ru.lifety.app.navigation.TaskEditorRoute
import ru.lifety.core.navigation.LocalPlannerNavigation
import ru.lifety.core.navigation.PlannerNavigation
import ru.lifety.core.repository.Repositories
import ru.lifety.core.ui.LifetyTheme
import ru.lifety.core.ui.LocalRepositories
import ru.lifety.feature.assistant.AssistantScreen
import ru.lifety.feature.calendar.CalendarScreen
import ru.lifety.feature.dashboard.DashboardScreen
import ru.lifety.feature.finance.FinanceScreen
import ru.lifety.feature.health.HealthScreen
import ru.lifety.feature.tasks.TaskEditorScreen
import ru.lifety.feature.tasks.TasksScreen

@Composable
fun LifetyApp(repositories: Repositories? = null) {
    val controller = rememberNavController()
    val entry by controller.currentBackStackEntryAsState()
    val isEditor = entry?.destination?.hasRoute<TaskEditorRoute>() == true
    val navigator = remember(controller) {
        RootNavigator(onNavigate = { destination ->
            controller.navigate(destination.route) {
                popUpTo(controller.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        })
    }
    LaunchedEffect(entry) {
        AppDestination.entries.firstOrNull { it.route == entry?.destination?.route }?.let { navigator.syncDestination(it) }
    }
    val planner = remember(controller) {
        object : PlannerNavigation {
            override fun openTask(id: String) { controller.navigate(TaskEditorRoute(taskId = id)) { launchSingleTop = true } }
            override fun createTask(scheduledAt: Long?) { controller.navigate(TaskEditorRoute(scheduledAt = scheduledAt)) { launchSingleTop = true } }
        }
    }
    LifetyTheme {
        CompositionLocalProvider(LocalRepositories provides repositories, LocalPlannerNavigation provides planner, LocalRootNavigator provides navigator) {
            Scaffold(bottomBar = {
                if (!isEditor) Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppDestination.entries.forEach { item ->
                            FilterChip(selected = entry?.destination?.route == item.route, onClick = { navigator.navigate(item) }, label = { Text(item.title) })
                        }
                    }
                }
            }) { padding ->
                NavHost(controller, startDestination = AppDestination.DASHBOARD.route, modifier = Modifier.fillMaxSize().padding(padding)) {
                    composable(AppDestination.DASHBOARD.route) { DashboardScreen(Modifier.fillMaxSize()) }
                    composable(AppDestination.TASKS.route) { TasksScreen(Modifier.fillMaxSize()) }
                    composable(AppDestination.CALENDAR.route) { CalendarScreen(Modifier.fillMaxSize()) }
                    composable(AppDestination.FINANCE.route) { FinanceScreen(Modifier.fillMaxSize()) }
                    composable(AppDestination.ASSISTANT.route) { AssistantScreen(Modifier.fillMaxSize()) }
                    composable(AppDestination.HEALTH.route) { HealthScreen(Modifier.fillMaxSize()) }
                    composable<TaskEditorRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<TaskEditorRoute>()
                        TaskEditorScreen(route.taskId, route.scheduledAt, onClose = { controller.popBackStack() })
                    }
                }
            }
        }
    }
}
