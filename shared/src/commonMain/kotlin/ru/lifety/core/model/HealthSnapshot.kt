package ru.lifety.core.model

import kotlinx.serialization.Serializable

@Serializable
data class HealthSnapshot(
    val timestamp: Long,
    val sleepMinutes: Int?,
    val steps: Int?,
)
