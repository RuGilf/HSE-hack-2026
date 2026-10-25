package ru.lifety.feature.health

import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.lifety.core.model.HealthSnapshot
import ru.lifety.core.repository.HealthRepository

internal expect fun healthRecordedAt(timestamp: Long): String

internal fun manualSnapshot(hours: String, minutes: String, steps: String, now: Long): HealthSnapshot {
    fun parse(text: String, max: Int): Int? {
        if (text.isBlank()) return null
        val value = text.trim().toIntOrNull()
        require(value != null && value in 0..max) { "Проверьте целые неотрицательные значения." }
        return value
    }
    val h = parse(hours, 24)
    val m = parse(minutes, 59)
    val s = parse(steps, 200_000)
    val sleep = if (h == null && m == null) null else (h ?: 0) * 60 + (m ?: 0)
    require(sleep == null || sleep <= 24 * 60) { "Сон не может превышать 24 часа." }
    require(sleep != null || s != null) { "Введите хотя бы один показатель." }
    return HealthSnapshot(now, sleep, s)
}

data class HealthUiState(
    val snapshot: HealthSnapshot? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

class HealthController(private val repository: HealthRepository?, private val scope: CoroutineScope, private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }) {
    private val mutableState = MutableStateFlow(HealthUiState(loading = repository != null))
    val state: StateFlow<HealthUiState> = mutableState.asStateFlow()
    private var observation: Job? = null
    init { reload() }

    fun reload() {
        val repo = repository ?: return
        observation?.cancel()
        mutableState.value = state.value.copy(loading = true, error = null)
        observation = scope.launch {
            try { repo.observeLatest().collect { mutableState.value = state.value.copy(snapshot = it, loading = false) } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.value = state.value.copy(loading = false, error = "Не удалось прочитать показатели. Повторите попытку.") }
        }
    }

    fun save(hours: String, minutes: String, steps: String) {
        val repo = repository ?: return
        if (state.value.saving) return
        val snapshot = try { manualSnapshot(hours, minutes, steps, now()) }
        catch (invalid: IllegalArgumentException) {
            mutableState.value = state.value.copy(error = invalid.message, saved = false)
            return
        }
        mutableState.value = state.value.copy(saving = true, error = null, saved = false)
        scope.launch {
            try {
                repo.update(snapshot)
                mutableState.value = state.value.copy(saving = false, saved = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.value = state.value.copy(saving = false, error = "Не удалось сохранить. Введённые значения остались в форме.") }
        }
    }
}
