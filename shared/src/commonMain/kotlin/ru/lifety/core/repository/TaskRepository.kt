package ru.lifety.core.repository

import kotlinx.coroutines.flow.Flow
import ru.lifety.core.model.Task

interface TaskRepository {
    fun observeAll(): Flow<List<Task>>
    suspend fun upsert(task: Task)
    suspend fun delete(id: String)
}
