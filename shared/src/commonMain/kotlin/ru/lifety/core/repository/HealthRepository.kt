package ru.lifety.core.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.lifety.core.model.HealthSnapshot

interface HealthRepository {
    fun observeLatest(): Flow<HealthSnapshot?>
    suspend fun update(snapshot: HealthSnapshot)

    /** Old adapters remain source compatible; persistent adapters override this with full history. */
    fun observeAll(): Flow<List<HealthSnapshot>> = observeLatest().map { listOfNotNull(it) }

    /** Persistent adapters import a preview atomically, with timestamp-based upserts. */
    suspend fun updateAll(snapshots: List<HealthSnapshot>) { snapshots.forEach { update(it) } }
}
