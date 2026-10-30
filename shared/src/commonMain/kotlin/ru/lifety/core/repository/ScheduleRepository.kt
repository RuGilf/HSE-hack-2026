package ru.lifety.core.repository

import kotlinx.coroutines.flow.Flow
import ru.lifety.core.model.ScheduleBlock

interface ScheduleRepository {
    fun observeAll(): Flow<List<ScheduleBlock>>
    suspend fun upsert(block: ScheduleBlock)
    suspend fun delete(id: String)
}
