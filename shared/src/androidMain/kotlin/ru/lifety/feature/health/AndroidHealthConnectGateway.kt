package ru.lifety.feature.health

import android.content.Context
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.lifety.core.model.HealthSnapshot
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period

internal data class HealthReadWindow(val capturedAt: Instant, val stepsStart: Instant, val sleepStart: Instant)

internal fun healthReadWindow(capturedAt: Long, zone: ZoneId): HealthReadWindow {
    val end = Instant.ofEpochMilli(capturedAt)
    return HealthReadWindow(end, end.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant(), end.minus(Duration.ofHours(24)))
}

/** Small testable adapter around Jetpack; all health queries use aggregate(), never raw records. */
internal interface HealthConnectApi {
    suspend fun availability(): HealthConnectAvailability
    suspend fun grantedMetrics(): Set<HealthMetric>
    suspend fun steps(start: Instant, end: Instant): Long?
    suspend fun sleep(start: Instant, end: Instant): Duration?
    suspend fun daily(metrics: Set<HealthMetric>, start: LocalDateTime, end: LocalDateTime): List<DailyHealthValue> =
        error("Daily aggregate adapter is unavailable")
}

internal data class DailyHealthValue(val date: LocalDate, val sleep: Duration?, val steps: Long?)

internal fun healthPermissions(metrics: Set<HealthMetric>): Set<String> = metrics.map { metric ->
    when (metric) {
        HealthMetric.SLEEP -> HealthPermission.getReadPermission(SleepSessionRecord::class)
        HealthMetric.STEPS -> HealthPermission.getReadPermission(StepsRecord::class)
    }
}.toSet()

internal class AndroidHealthConnectGateway(
    private val api: HealthConnectApi,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) : HealthConnectGateway {
    constructor(context: Context) : this(JetpackHealthConnectApi(context.applicationContext))

    override suspend fun availability(): HealthConnectAvailability = api.availability()
    override suspend fun grantedMetrics(): Set<HealthMetric> = permissionSafe { api.grantedMetrics() }

    override suspend fun readSnapshot(metrics: Set<HealthMetric>, capturedAt: Long): HealthSnapshot = permissionSafe {
        check(api.availability() == HealthConnectAvailability.AVAILABLE)
        require(metrics.isNotEmpty())
        val window = healthReadWindow(capturedAt, zone())
        val steps = if (HealthMetric.STEPS in metrics && window.stepsStart < window.capturedAt) {
            requirePermission(HealthMetric.STEPS)
            api.steps(window.stepsStart, window.capturedAt)?.also { require(it in 0..Int.MAX_VALUE.toLong()) }?.toInt()
        } else null
        val sleep = if (HealthMetric.SLEEP in metrics) {
            requirePermission(HealthMetric.SLEEP)
            api.sleep(window.sleepStart, window.capturedAt)?.also {
                require(!it.isNegative && it <= Duration.ofHours(24))
            }?.toMinutes()?.toInt()
        } else null
        HealthSnapshot(capturedAt, sleep, steps)
    }

    override suspend fun readHistory(metrics: Set<HealthMetric>, capturedAt: Long, days: Int): List<HealthSnapshot> = permissionSafe {
        require(days in 1..30 && metrics.isNotEmpty())
        check(api.availability() == HealthConnectAvailability.AVAILABLE)
        metrics.forEach { requirePermission(it) }
        val zone = zone()
        val end = Instant.ofEpochMilli(capturedAt).atZone(zone)
        val start = end.toLocalDate().minusDays(days.toLong() - 1)
        val buckets = api.daily(metrics, start.atStartOfDay(), end.toLocalDateTime())
        metrics.forEach { requirePermission(it) }
        require(buckets.map { it.date }.distinct().size == buckets.size)
        buckets.map { bucket ->
            require(!bucket.date.isBefore(start) && !bucket.date.isAfter(end.toLocalDate()))
            val sleep = if (HealthMetric.SLEEP in metrics) bucket.sleep?.also {
                require(!it.isNegative && it <= Duration.ofHours(26))
            }?.toMinutes()?.toInt() else null
            val steps = if (HealthMetric.STEPS in metrics) bucket.steps?.also {
                require(it in 0..Int.MAX_VALUE.toLong())
            }?.toInt() else null
            // Finished days have a stable key. Today's changing cumulative reading is a new snapshot.
            val timestamp = if (bucket.date == end.toLocalDate()) capturedAt else bucket.date.atStartOfDay(zone).toInstant().toEpochMilli()
            HealthSnapshot(timestamp, sleep, steps)
        }.filter { it.sleepMinutes != null || it.steps != null }.sortedBy { it.timestamp }
    }

    private suspend fun requirePermission(metric: HealthMetric) {
        if (metric !in api.grantedMetrics()) throw HealthPermissionRevoked()
    }

    private suspend fun <T> permissionSafe(block: suspend () -> T): T =
        try { block() } catch (_: SecurityException) { throw HealthPermissionRevoked() }
}

private class JetpackHealthConnectApi(private val context: Context) : HealthConnectApi {
    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    override suspend fun availability(): HealthConnectAvailability = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 28) return@withContext HealthConnectAvailability.UNAVAILABLE
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.INSTALL_REQUIRED
            else -> HealthConnectAvailability.UNAVAILABLE
        }
    }

    override suspend fun grantedMetrics(): Set<HealthMetric> = withContext(Dispatchers.IO) {
        val permissions = client.permissionController.getGrantedPermissions()
        HealthMetric.entries.filter { permissions.containsAll(healthPermissions(setOf(it))) }.toSet()
    }

    override suspend fun steps(start: Instant, end: Instant): Long? = withContext(Dispatchers.IO) {
        client.aggregate(AggregateRequest(metrics = setOf(StepsRecord.COUNT_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(start, end)))[StepsRecord.COUNT_TOTAL]
    }

    override suspend fun sleep(start: Instant, end: Instant): Duration? = withContext(Dispatchers.IO) {
        client.aggregate(AggregateRequest(metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
            timeRangeFilter = TimeRangeFilter.between(start, end)))[SleepSessionRecord.SLEEP_DURATION_TOTAL]
    }

    override suspend fun daily(metrics: Set<HealthMetric>, start: LocalDateTime, end: LocalDateTime): List<DailyHealthValue> = withContext(Dispatchers.IO) {
        if (start >= end) return@withContext emptyList()
        val requested = buildSet<AggregateMetric<*>> {
            if (HealthMetric.SLEEP in metrics) add(SleepSessionRecord.SLEEP_DURATION_TOTAL)
            if (HealthMetric.STEPS in metrics) add(StepsRecord.COUNT_TOTAL)
        }
        client.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(metrics = requested,
            timeRangeFilter = TimeRangeFilter.between(start, end), timeRangeSlicer = Period.ofDays(1)))
            .map { DailyHealthValue(it.startTime.toLocalDate(), it.result[SleepSessionRecord.SLEEP_DURATION_TOTAL], it.result[StepsRecord.COUNT_TOTAL]) }
    }
}
