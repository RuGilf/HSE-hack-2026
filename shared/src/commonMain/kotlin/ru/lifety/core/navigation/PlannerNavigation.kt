package ru.lifety.core.navigation

import androidx.compose.runtime.staticCompositionLocalOf

/** Feature-independent integration point. IDs are opaque strings, not route fragments. */
interface PlannerNavigation {
    fun openTask(id: String)
    fun createTask(scheduledAt: Long? = null)
}

val LocalPlannerNavigation = staticCompositionLocalOf<PlannerNavigation?> { null }
