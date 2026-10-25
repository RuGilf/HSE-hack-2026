package ru.lifety.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val id: String,
    val title: String,
    val scheduledAt: Long?,
    val deadlineAt: Long?,
    val isCompleted: Boolean,
)
