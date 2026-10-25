package ru.lifety.app

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
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
import ru.lifety.core.ui.LifetyMotion
import ru.lifety.core.ui.LifetyIcon
import ru.lifety.core.ui.LifetyMascot
import ru.lifety.core.ui.LifetySymbol
import ru.lifety.core.ui.LifetyTheme
import ru.lifety.core.ui.LocalRepositories
import ru.lifety.feature.assistant.AssistantScreen
import ru.lifety.feature.dashboard.DashboardScreen
import ru.lifety.feature.finance.FinanceScreen
import ru.lifety.feature.health.HealthScreen
import ru.lifety.feature.tasks.TaskEditorScreen

private val sections = listOf(
    AppDestination.DASHBOARD to LifetySymbol.Home,
    AppDestination.TASKS to LifetySymbol.Plan,
    AppDestination.FINANCE to LifetySymbol.Wallet,
    AppDestination.HEALTH to LifetySymbol.Heart,
    AppDestination.ASSISTANT to LifetySymbol.Assistant,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifetyApp(repositories: Repositories? = null) {
    val controller = rememberNavController()
    val entry by controller.currentBackStackEntryAsState()
    val isEditor = entry?.destination?.hasRoute<TaskEditorRoute>() == true
    val route = entry?.destination?.route
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    val keyboardVisible by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    var settings by rememberSaveable { mutableStateOf(false) }
    var planRoute by rememberSaveable { mutableStateOf(AppDestination.TASKS.route) }
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
        AppDestination.entries.firstOrNull { it.route == route }?.let { navigator.syncDestination(it) }
        if (route == AppDestination.TASKS.route || route == AppDestination.CALENDAR.route) planRoute = route
    }
    val planner = remember(controller) {
        object : PlannerNavigation {
            override fun openTask(id: String) { controller.navigate(TaskEditorRoute(taskId = id)) { launchSingleTop = true } }
            override fun createTask(scheduledAt: Long?) { controller.navigate(TaskEditorRoute(scheduledAt = scheduledAt)) { launchSingleTop = true } }
        }
    }
    LifetyTheme {
        CompositionLocalProvider(LocalRepositories provides repositories, LocalPlannerNavigation provides planner, LocalRootNavigator provides navigator) {
            val section = if (route == AppDestination.CALENDAR.route) AppDestination.TASKS else AppDestination.fromRoute(route.orEmpty())
            Scaffold(
                topBar = {
                    if (!isEditor) TopAppBar(
                        title = { Text(if (section == AppDestination.DASHBOARD) "Lifety" else section.title, style = MaterialTheme.typography.titleLarge) },
                        navigationIcon = {
                            if (section == AppDestination.DASHBOARD) LifetyMascot(Modifier.padding(start = 12.dp).size(32.dp), animated = false)
                        },
                        actions = {
                            IconButton(onClick = { settings = true }, modifier = Modifier.semantics { contentDescription = "Настройки" }) {
                                LifetyIcon(LifetySymbol.Settings, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                    )
                },
                bottomBar = {
                    if (!isEditor && !keyboardVisible) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
                        sections.forEach { (item, symbol) ->
                            NavigationBarItem(selected = section == item,
                                onClick = {
                                    val target = if (item == AppDestination.TASKS) AppDestination.fromRoute(planRoute) else item
                                    if (section != item) navigator.navigate(target)
                                },
                                icon = { LifetyIcon(symbol) },
                                label = { Text(item.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                },
            ) { padding ->
                NavHost(
                    controller, startDestination = AppDestination.DASHBOARD.route,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    enterTransition = { fadeIn(tween(LifetyMotion.Standard)) },
                    exitTransition = { fadeOut(tween(LifetyMotion.Quick)) },
                    popEnterTransition = { fadeIn(tween(LifetyMotion.Standard)) },
                    popExitTransition = { fadeOut(tween(LifetyMotion.Quick)) },
                ) {
                    composable(AppDestination.DASHBOARD.route) { DashboardScreen(Modifier.fillMaxSize()) }
                    composable(AppDestination.TASKS.route) { PlanScreen(modifier = Modifier.fillMaxSize()) }
                    composable(AppDestination.CALENDAR.route) { PlanScreen(initialCalendar = true, modifier = Modifier.fillMaxSize()) }
                    composable(AppDestination.FINANCE.route) { FinanceScreen(Modifier.fillMaxSize()) }
                    composable(AppDestination.ASSISTANT.route) { AssistantScreen(Modifier.fillMaxSize()) }
                    composable(AppDestination.HEALTH.route) { HealthScreen(Modifier.fillMaxSize()) }
                    composable<TaskEditorRoute>(
                        enterTransition = { EnterTransition.None },
                        exitTransition = { ExitTransition.None },
                        popEnterTransition = { EnterTransition.None },
                        popExitTransition = { ExitTransition.None },
                    ) { backStackEntry ->
                        val editor = backStackEntry.toRoute<TaskEditorRoute>()
                        TaskEditorScreen(editor.taskId, editor.scheduledAt, onClose = { controller.popBackStack() })
                    }
                }
            }
            if (settings) LifetySettingsSheet(onDismiss = { settings = false }, onFinance = {
                settings = false
                navigator.navigate(AppDestination.FINANCE)
            })
        }
    }
}
