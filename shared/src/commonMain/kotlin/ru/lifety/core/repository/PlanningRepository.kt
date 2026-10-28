package ru.lifety.core.repository

import kotlinx.coroutines.flow.Flow
import ru.lifety.core.model.*
import ru.lifety.core.planning.*

interface GoalRepository {
    fun observeAll(): Flow<List<Goal>>
    suspend fun upsert(goal: Goal)
    suspend fun delete(id: String)
}

interface PlanningRepository {
    fun observeAll(): Flow<List<TaskPlan>>
    fun observeReport(): Flow<PlanReport?>
    /** Consistent snapshot across tasks, goals and planning metadata. */
    suspend fun snapshot(): PlanSnapshot
    /** Revalidates the current snapshot and applies every schedule/estimate in one transaction. */
    suspend fun apply(plan: ImportedPlan, now: Long)
    /** Saves a manual schedule and its estimate/steps atomically; null clears planning metadata. */
    suspend fun saveTask(task: Task, details: TaskPlan?) { throw UnsupportedOperationException("Manual planning is unavailable") }
}
