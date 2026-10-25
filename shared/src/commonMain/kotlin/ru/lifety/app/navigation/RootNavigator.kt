package ru.lifety.app.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Stable integration facade over the root NavHost. */
class RootNavigator(
    initialDestination: AppDestination = AppDestination.DASHBOARD,
    private val onNavigate: (AppDestination) -> Unit = {},
) {
    private val mutableDestination = MutableStateFlow(initialDestination)
    val destination: StateFlow<AppDestination> = mutableDestination.asStateFlow()

    fun navigate(destination: AppDestination) {
        mutableDestination.value = destination
        onNavigate(destination)
    }

    internal fun syncDestination(destination: AppDestination) { mutableDestination.value = destination }
}

val LocalRootNavigator = staticCompositionLocalOf<RootNavigator?> { null }
