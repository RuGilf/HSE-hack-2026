package ru.lifety.database

import app.cash.sqldelight.coroutines.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import ru.lifety.core.model.*
import ru.lifety.core.model.HealthMetric
import ru.lifety.core.model.HealthEntry
import ru.lifety.core.repository.*
import ru.lifety.database.generated.LifetyDatabase
import ru.lifety.app.navigation.*

internal fun LifetyQueries.metricSnapshot() = HealthMetricSnapshot(
    selectHealthMetrics { id, name, unit, kind, aggregation, source, archived -> HealthMetric(id, name, unit, MetricKind.valueOf(kind), MetricAggregation.valueOf(aggregation), source, archived) }.executeAsList(),
    selectHealthEntries(::HealthEntry).executeAsList())

class SqlDelightHealthMetricsRepository(database: LifetyDatabase, private val dispatcher: CoroutineDispatcher = Dispatchers.IO) : HealthMetricsRepository {
    private val q = database.lifetyQueries
    override fun observe(): Flow<HealthMetricSnapshot> = combine(q.selectHealthMetrics().asFlow(), q.selectHealthEntries().asFlow()) { _, _ -> snapshot() }
    override suspend fun snapshot() = withContext(dispatcher) { q.transactionWithResult { q.metricSnapshot() } }
    override suspend fun saveMetric(metric: HealthMetric) = withContext(dispatcher) {
        require(metric.id.isNotBlank() && metric.name.isNotBlank() && metric.name.length <= 80 && metric.unit.length <= 24)
        require(metric.kind == MetricKind.NUMBER || metric.aggregation == MetricAggregation.LATEST)
        q.transaction {
            val before = q.metricSnapshot()
            val old = before.metrics.find { it.id == metric.id }
            require(old == null || old.sourceKey == metric.sourceKey)
            require(old == null || before.entries.none { it.metricId == metric.id } || old.kind == metric.kind) { "Нельзя менять тип показателя с записями" }
            if (metric.sourceKey != null) {
                val spec = healthApiMetrics.firstOrNull { it.sourceKey == metric.sourceKey }
                require(spec != null && metric.kind == spec.kind && metric.unit == spec.unit && metric.aggregation == spec.aggregation)
            }
            q.upsertHealthMetric(metric.id, metric.name.trim(), metric.unit.trim(), metric.kind.name, metric.aggregation.name, metric.sourceKey, metric.archived)
        }
    }
    override suspend fun saveEntry(entry: HealthEntry) = withContext(dispatcher) {
        require(entry.id.isNotBlank() && entry.source == "manual")
        q.transaction {
            val state = q.metricSnapshot(); val metric = requireNotNull(state.metrics.find { it.id == entry.metricId && !it.archived })
            require(state.entries.none { it.id == entry.id && (it.metricId != entry.metricId || it.source != "manual") })
            val value = normalizedMetricValue(metric, entry.value)
            q.upsertHealthEntry(entry.id, entry.metricId, entry.timestamp, value, "manual")
        }
    }
    override suspend fun deleteEntry(id: String) = withContext(dispatcher) { q.deleteHealthEntry(id); Unit }
    override suspend fun importSamples(samples: List<HealthApiSample>, source: String) = withContext(dispatcher) {
        require(source in setOf("healthkit", "health-connect") && samples.size <= 120)
        require(samples.distinctBy { it.key to it.timestamp }.size == samples.size)
        q.transaction {
            val old = q.metricSnapshot()
            samples.forEach { sample ->
                val spec = requireNotNull(healthApiMetrics.find { it.sourceKey == sample.key })
                require(sample.value.isFinite() && sample.value >= 0 && sample.value <= 1e9)
                val metric = old.metrics.find { it.sourceKey == sample.key } ?: spec
                q.upsertHealthMetric(metric.id, metric.name, spec.unit, spec.kind.name, spec.aggregation.name, spec.sourceKey, false)
                q.upsertHealthEntry("$source:${sample.key}:${sample.timestamp}", metric.id, sample.timestamp, sample.value.toString(), source)
            }
        }
    }
}

class SqlDelightNavigationPreferencesRepository(database: LifetyDatabase, private val dispatcher: CoroutineDispatcher = Dispatchers.IO) : NavigationPreferencesRepository {
    private val q = database.lifetyQueries
    override fun observe(): Flow<List<String>> = q.selectNavigationPreference().asFlow().mapToOneOrNull(dispatcher).map { raw ->
        raw?.let { runCatching { normalizedNavigation(Json.decodeFromString<List<String>>(it)) }.getOrNull() } ?: defaultNavigation
    }
    override suspend fun save(routes: List<String>) = withContext(dispatcher) {
        require(routes.size in 1..5 && routes.distinct().size == routes.size && routes.all { it in availableNavigation })
        q.upsertNavigationPreference(Json.encodeToString(routes)); Unit
    }
}
