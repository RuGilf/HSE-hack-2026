package ru.lifety.app

import androidx.compose.runtime.Composable
import ru.lifety.app.navigation.AppDestination

@Composable
internal expect fun PlatformNavigationBar(selected: AppDestination, onSelect: (AppDestination) -> Unit)

internal val primarySections = listOf(AppDestination.DASHBOARD, AppDestination.TASKS,
    AppDestination.FINANCE, AppDestination.HEALTH, AppDestination.ASSISTANT)
