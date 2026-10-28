package ru.lifety.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Goal(val id: String, val title: String, val description: String = "", val targetAt: Long? = null, val isCompleted: Boolean = false)

@Serializable
data class TaskPlan(val taskId: String, val durationMinutes: Int, val approach: String = "", val goalId: String? = null)

@Serializable
data class PlanReport(val summary: String, val importedAt: Long)
