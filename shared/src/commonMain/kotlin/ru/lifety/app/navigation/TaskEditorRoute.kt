package ru.lifety.app.navigation

import kotlinx.serialization.Serializable

@Serializable
data class TaskEditorRoute(val taskId: String? = null, val scheduledAt: Long? = null)
