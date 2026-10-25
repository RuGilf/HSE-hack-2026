package ru.lifety.app.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Top-level destination selection; no feature-specific back stack. */
class RootNavigator(initialDestination: AppDestination = AppDestination.DASHBOARD) {
    private val mutableDestination = MutableStateFlow(initialDestination)
    val destination: StateFlow<AppDestination> = mutableDestination.asStateFlow()

    fun navigate(destination: AppDestination) {
        mutableDestination.value = destination
    }
}
