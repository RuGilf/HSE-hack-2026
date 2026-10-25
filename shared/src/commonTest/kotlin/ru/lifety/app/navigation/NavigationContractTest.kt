package ru.lifety.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class NavigationContractTest {
    @Test
    fun savedStableRouteRestoresDestinationAndUnknownRouteFallsBack() {
        val navigator = RootNavigator()
        navigator.navigate(AppDestination.FINANCE)
        val savedRoute = navigator.destination.value.route
        val restored = RootNavigator(AppDestination.fromRoute(savedRoute))
        assertEquals(AppDestination.FINANCE, restored.destination.value)
        assertEquals(AppDestination.DASHBOARD, AppDestination.fromRoute("removed-route"))
    }
}
