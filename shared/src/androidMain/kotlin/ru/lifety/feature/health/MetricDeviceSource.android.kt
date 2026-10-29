package ru.lifety.feature.health

import android.os.Build
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import ru.lifety.core.model.HealthApiSample
import java.time.*

@Composable internal actual fun rememberMetricDeviceSource(): MetricDeviceSource {
    val context = LocalContext.current.applicationContext
    val status = remember(context) { if (Build.VERSION.SDK_INT >= 28) HealthConnectClient.getSdkStatus(context) else HealthConnectClient.SDK_UNAVAILABLE }
    val client = remember(context, status) { if (status == HealthConnectClient.SDK_AVAILABLE) HealthConnectClient.getOrCreate(context) else null }
    var pending by remember { mutableStateOf<CancellableContinuation<Unit>?>(null) }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { pending?.let { if (it.isActive) it.resume(Unit) }; pending = null }
    return remember(client, launcher, status) { object : MetricDeviceSource {
        override val available = client != null
        override val source = "health-connect"
        override val setupRequired = status == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED
        override fun openSetup() {
            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.apps.healthdata")).setPackage("com.android.vending").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            catch (_: android.content.ActivityNotFoundException) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        private fun permission(key: String): String = when (key) {
            "sleep" -> HealthPermission.getReadPermission(SleepSessionRecord::class)
            "steps" -> HealthPermission.getReadPermission(StepsRecord::class)
            "weight" -> HealthPermission.getReadPermission(WeightRecord::class)
            "resting-heart-rate" -> HealthPermission.getReadPermission(RestingHeartRateRecord::class)
            "water" -> HealthPermission.getReadPermission(HydrationRecord::class)
            "active-energy" -> HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
            else -> error("Unsupported metric")
        }
        override suspend fun authorize(keys: Set<String>) {
            val service = requireNotNull(client)
            val needed = keys.map(::permission).toSet()
            if (service.permissionController.getGrantedPermissions().containsAll(needed)) return
            suspendCancellableCoroutine<Unit> { c ->
                check(pending == null)
                pending = c; c.invokeOnCancellation { pending = null }
                try { launcher.launch(needed) }
                catch (e: Exception) { pending = null; c.resumeWith(Result.failure(e)) }
            }
        }
        override suspend fun read(keys: Set<String>): List<HealthApiSample> = withContext(Dispatchers.IO) {
            val service = requireNotNull(client)
            val granted = service.permissionController.getGrantedPermissions()
            val allowed = keys.filter { permission(it) in granted }.toSet()
            check(allowed.isNotEmpty())
            val needed = allowed.map(::permission).toSet()
            val zone = ZoneId.systemDefault(); val end = Instant.now(); val first = end.atZone(zone).toLocalDate().minusDays(29)
            val start = first.atStartOfDay(zone).toInstant(); val range = TimeRangeFilter.between(start, end)
            val rows = mutableListOf<HealthApiSample>()
            val basic = buildSet { if ("sleep" in allowed) add(HealthMetric.SLEEP); if ("steps" in allowed) add(HealthMetric.STEPS) }
            if (basic.isNotEmpty()) AndroidHealthConnectGateway(context).readHistory(basic, end.toEpochMilli(), 30).forEach { row ->
                val day = Instant.ofEpochMilli(row.timestamp).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
                row.sleepMinutes?.let { rows += HealthApiSample("sleep", day, it.toDouble()) }
                row.steps?.let { rows += HealthApiSample("steps", day, it.toDouble()) }
            }
            if ("weight" in allowed) {
                val values = mutableListOf<WeightRecord>(); var token: String? = null
                do { val page = service.readRecords(ReadRecordsRequest(WeightRecord::class, range, pageSize = 1000, pageToken = token)); values += page.records; token = page.pageToken
                    require(values.size <= 20_000) { "Too many weight records" }
                } while (token != null)
                values.groupBy { it.time.atZone(zone).toLocalDate() }.forEach { (day, samples) -> rows += HealthApiSample("weight", day.atStartOfDay(zone).toInstant().toEpochMilli(), samples.maxBy { it.time }.weight.inKilograms) }
            }
            if ("resting-heart-rate" in allowed) {
                val values = mutableListOf<RestingHeartRateRecord>(); var token: String? = null
                do { val page = service.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, range, pageSize = 1000, pageToken = token)); values += page.records; token = page.pageToken
                    require(values.size <= 20_000)
                } while (token != null)
                values.groupBy { it.time.atZone(zone).toLocalDate() }.forEach { (day, samples) -> rows += HealthApiSample("resting-heart-rate", day.atStartOfDay(zone).toInstant().toEpochMilli(), samples.maxBy { it.time }.beatsPerMinute.toDouble()) }
            }
            val localRange = TimeRangeFilter.between(first.atStartOfDay(), end.atZone(zone).toLocalDateTime())
            if ("water" in allowed) service.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(setOf(HydrationRecord.VOLUME_TOTAL), localRange, Period.ofDays(1))).forEach { bucket ->
                bucket.result[HydrationRecord.VOLUME_TOTAL]?.let { rows += HealthApiSample("water", bucket.startTime.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli(), it.inMilliliters) }
            }
            if ("active-energy" in allowed) service.aggregateGroupByPeriod(AggregateGroupByPeriodRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), localRange, Period.ofDays(1))).forEach { bucket ->
                bucket.result[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.let { rows += HealthApiSample("active-energy", bucket.startTime.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli(), it.inKilocalories) }
            }
            check(service.permissionController.getGrantedPermissions().containsAll(needed))
            rows.sortedBy { it.timestamp }
        }
        override fun cancel() { pending?.cancel(); pending = null }
    } }
}
