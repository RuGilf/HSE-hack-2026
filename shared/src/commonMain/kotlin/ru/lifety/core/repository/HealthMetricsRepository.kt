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
    /** Confirms one preview atomically across built-in and custom health tables. */
    suspend fun importDeviceSamples(samples: List<HealthApiSample>, source: String, baseline: List<HealthSnapshot>): Unit =
        throw UnsupportedOperationException("Unified health import is unavailable")
}
interface NavigationPreferencesRepository {
    fun observe(): Flow<List<String>>
    suspend fun save(routes: List<String>)
    fun observePresentation(): Flow<String> = kotlinx.coroutines.flow.flowOf("tabs")
    suspend fun saveConfiguration(routes: List<String>, presentation: String) {
        require(presentation == "tabs") { "This repository does not support arc navigation" }
        save(routes)
    }
}
