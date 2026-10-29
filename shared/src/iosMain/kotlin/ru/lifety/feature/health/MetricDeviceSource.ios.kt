package ru.lifety.feature.health

import androidx.compose.runtime.*
import kotlinx.coroutines.suspendCancellableCoroutine
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
        override suspend fun read(keys: Set<String>): List<HealthApiSample> = suspendCancellableCoroutine { continuation ->
            val service = requireNotNull(IosServices.healthMetrics)
            continuation.invokeOnCancellation { service.cancel() }
            service.readDays(keys.toList()) { rows, error -> if (continuation.isActive) {
                if (error == null) continuation.resume(rows.orEmpty()) else continuation.resumeWithException(IllegalStateException(error))
            } }
        }
        override fun cancel() { IosServices.healthMetrics?.cancel() }
    }
}
