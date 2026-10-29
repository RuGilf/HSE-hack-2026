package ru.lifety.feature.health

import androidx.compose.runtime.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import ru.lifety.core.time.localDate
import ru.lifety.core.model.HealthSnapshot
import ru.lifety.core.model.HealthApiSample
import ru.lifety.core.platform.IosServices
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Composable internal actual fun rememberMetricDeviceSource(): MetricDeviceSource = remember {
    object : MetricDeviceSource {
        override val available get() = IosServices.healthMetrics?.available == true
        override val source = "healthkit"
        override suspend fun authorize(keys: Set<String>): Unit = suspendCancellableCoroutine { continuation ->
            val service = requireNotNull(IosServices.healthMetrics)
            service.requestAccess(keys.toList()) { error -> if (continuation.isActive) {
                if (error == null) continuation.resume(Unit) else continuation.resumeWithException(IllegalStateException(error))
            } }
        }
        override suspend fun read(keys: Set<String>): List<HealthApiSample> {
            val output = mutableListOf<HealthApiSample>()
            if ("sleep" in keys || "steps" in keys) {
                val rows = suspendCancellableCoroutine<List<HealthSnapshot>> { continuation ->
                    val service = requireNotNull(IosServices.health)
                    continuation.invokeOnCancellation { service.cancelRead() }
                    service.readDays("sleep" in keys, "steps" in keys, 30) { rows, error -> if (continuation.isActive) {
                        if (error == null) continuation.resume(rows.orEmpty()) else continuation.resumeWithException(IllegalStateException(error))
                    } }
                }
                rows.forEach { row ->
                    val day = localDate(row.timestamp).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
                    if ("sleep" in keys) row.sleepMinutes?.let { output += HealthApiSample("sleep", day, it.toDouble()) }
                    if ("steps" in keys) row.steps?.let { output += HealthApiSample("steps", day, it.toDouble()) }
                }
            }
            val extra = keys - setOf("sleep", "steps")
            if (extra.isNotEmpty()) output += suspendCancellableCoroutine<List<HealthApiSample>> { continuation ->
                val service = requireNotNull(IosServices.healthMetrics)
                continuation.invokeOnCancellation { service.cancel() }
                service.readDays(extra.toList()) { rows, error -> if (continuation.isActive) {
                    if (error == null) continuation.resume(rows.orEmpty()) else continuation.resumeWithException(IllegalStateException(error))
                } }
            }
            return output
        }
        override fun cancel() { IosServices.healthMetrics?.cancel(); IosServices.health?.cancelRead() }
    }
}
