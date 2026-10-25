package ru.lifety.core.repository

import kotlinx.coroutines.flow.Flow
import ru.lifety.core.model.HealthSnapshot

interface HealthRepository {
    fun observeLatest(): Flow<HealthSnapshot?>
    suspend fun update(snapshot: HealthSnapshot)
}
