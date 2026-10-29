package ru.lifety.core.repository

import kotlinx.coroutines.flow.Flow
import ru.lifety.core.model.*

interface HealthMetricsRepository {
    fun observe(): Flow<HealthMetricSnapshot>
    suspend fun snapshot(): HealthMetricSnapshot
    suspend fun saveMetric(metric: HealthMetric)
    suspend fun saveEntry(entry: HealthEntry)
    suspend fun deleteEntry(id: String)
    /** Imported daily totals replace previous imported values, never accumulate on re-import. */
    suspend fun importSamples(samples: List<HealthApiSample>, source: String)
}
interface NavigationPreferencesRepository {
    fun observe(): Flow<List<String>>
    suspend fun save(routes: List<String>)
}
