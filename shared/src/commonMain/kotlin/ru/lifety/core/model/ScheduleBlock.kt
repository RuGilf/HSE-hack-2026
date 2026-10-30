package ru.lifety.core.model

import kotlinx.serialization.Serializable

/** Local wall-clock timetable. ISO weekdays 1=Monday..7=Sunday, or one ISO date. */
@Serializable
data class ScheduleBlock(
    val id: String,
    val title: String,
    val startMinute: Int,
    val endMinute: Int,
    val weekdays: Set<Int> = emptySet(),
    val date: String? = null,
)
