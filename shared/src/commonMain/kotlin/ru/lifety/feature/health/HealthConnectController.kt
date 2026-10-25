package ru.lifety.feature.health

import androidx.compose.runtime.Composable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import ru.lifety.core.model.HealthSnapshot
import ru.lifety.core.repository.HealthRepository
import kotlin.time.Clock

internal enum class HealthMetric(val label: String) { SLEEP("Сон"), STEPS("Шаги") }
internal enum class HealthConnectAvailability { AVAILABLE, INSTALL_REQUIRED, UNAVAILABLE }
internal class HealthPermissionRevoked : Exception()

/** Platform boundary. No server, background worker or Health Connect write permission. */
internal interface HealthConnectGateway {
    suspend fun availability(): HealthConnectAvailability
    suspend fun grantedMetrics(): Set<HealthMetric>
    suspend fun readSnapshot(metrics: Set<HealthMetric>, capturedAt: Long): HealthSnapshot
}

internal data class HealthImportPreview(
    val snapshot: HealthSnapshot,
    val baseline: HealthSnapshot?,
    val metrics: Set<HealthMetric>,
)

internal data class HealthConnectUiState(
    val availability: HealthConnectAvailability? = null,
    val selected: Set<HealthMetric> = HealthMetric.entries.toSet(),
    val granted: Set<HealthMetric> = emptySet(),
    val busy: Boolean = false,
    val preview: HealthImportPreview? = null,
    val message: String? = null,
    val error: String? = null,
)

internal class HealthConnectController(
    private val gateway: HealthConnectGateway,
    private val repository: HealthRepository?,
    private val scope: CoroutineScope,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val mutableState = MutableStateFlow(HealthConnectUiState())
    val state: StateFlow<HealthConnectUiState> = mutableState.asStateFlow()

    /** Safe on entry/resume: checks installation and permissions, never reads measurements. */
    fun refresh() = operation {
        val availability = gateway.availability()
        val granted = if (availability == HealthConnectAvailability.AVAILABLE) gateway.grantedMetrics() else emptySet()
        mutableState.value = state.value.copy(availability = availability, granted = granted,
            preview = state.value.preview?.takeIf { granted.containsAll(it.metrics) })
    }

    fun select(metric: HealthMetric, selected: Boolean) {
        if (state.value.busy) return
        val metrics = if (selected) state.value.selected + metric else state.value.selected - metric
        mutableState.value = state.value.copy(selected = metrics, preview = null, message = null, error = null)
    }

    /** Only an explicit Read button calls this. Partial permission grants are supported. */
    fun readPreview() {
        val repo = repository ?: return
        if (state.value.busy || state.value.selected.isEmpty()) return
        val selected = state.value.selected.toSet()
        operation {
            mutableState.value = state.value.copy(preview = null)
            val availability = gateway.availability()
            val granted = if (availability == HealthConnectAvailability.AVAILABLE) gateway.grantedMetrics() else emptySet()
            mutableState.value = state.value.copy(availability = availability, granted = granted)
            val allowed = selected.intersect(granted)
            if (allowed.isEmpty()) {
                showError("Разрешите чтение выбранных показателей в Health Connect.")
                return@operation
            }
            val baseline = repo.observeLatest().first()
            val snapshot = gateway.readSnapshot(allowed, now()).let {
                it.copy(sleepMinutes = it.sleepMinutes.takeIf { HealthMetric.SLEEP in allowed },
                    steps = it.steps.takeIf { HealthMetric.STEPS in allowed })
            }
            require(snapshot.sleepMinutes == null || snapshot.sleepMinutes in 0..1440)
            require(snapshot.steps == null || snapshot.steps >= 0)
            if (baseline != null && baseline.timestamp > snapshot.timestamp) {
                showError("Время чтения раньше последней локальной записи. Проверьте дату и время устройства.")
                return@operation
            }
            if (snapshot.sleepMinutes == null && snapshot.steps == null) {
                mutableState.value = state.value.copy(message = "За выбранный период записей пока нет. Локальные показатели сохранены без изменений.")
            } else {
                mutableState.value = state.value.copy(preview = HealthImportPreview(snapshot, baseline, allowed))
            }
        }
    }

    /** Preview and confirmation are separate. Never merge old values into a new snapshot. */
    fun savePreview() {
        val repo = repository ?: return
        val preview = state.value.preview ?: return
        operation {
            val availability = gateway.availability()
            val granted = if (availability == HealthConnectAvailability.AVAILABLE) gateway.grantedMetrics() else emptySet()
            mutableState.value = state.value.copy(availability = availability, granted = granted)
            if (!granted.containsAll(preview.metrics)) {
                mutableState.value = state.value.copy(preview = null)
                showError("Доступ изменился. Проверьте разрешения и заново прочитайте показатели.")
                return@operation
            }
            if (repo.observeLatest().first() != preview.baseline) {
                mutableState.value = state.value.copy(preview = null)
                showError("Локальные показатели изменились. Обновите предпросмотр перед импортом.")
                return@operation
            }
            repo.update(preview.snapshot)
            mutableState.value = state.value.copy(preview = null, message = "Снимок Health Connect сохранён на устройстве.")
        }
    }

    fun discardPreview() {
        if (!state.value.busy) mutableState.value = state.value.copy(preview = null, message = null)
    }

    fun showError(message: String) { mutableState.value = state.value.copy(error = message) }

    private fun operation(block: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.value = state.value.copy(busy = true, error = null, message = null)
        scope.launch {
            try { withTimeout(20_000) { block() } }
            catch (_: TimeoutCancellationException) { showError("Health Connect не ответил вовремя. Повторите действие.") }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: HealthPermissionRevoked) {
                mutableState.value = state.value.copy(preview = null, granted = emptySet())
                showError("Доступ к показателям отозван. Проверьте разрешения Health Connect.")
            }
            catch (_: Exception) { showError("Не удалось выполнить действие. Повторите попытку; ручной ввод остаётся доступен.") }
            finally { mutableState.value = state.value.copy(busy = false) }
        }
    }
}

@Composable
internal expect fun HealthConnectSection(repository: HealthRepository?, enabled: Boolean, onBusyChanged: (Boolean) -> Unit)
