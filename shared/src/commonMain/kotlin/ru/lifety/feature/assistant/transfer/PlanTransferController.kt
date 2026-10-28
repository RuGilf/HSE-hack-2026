package ru.lifety.feature.assistant.transfer

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.lifety.core.planning.*
import ru.lifety.core.repository.PlanningRepository
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

data class PlanTransferState(val busy: Boolean = false, val snapshot: PlanSnapshot? = null, val prompt: String? = null,
    val preview: ImportedPlan? = null, val error: String? = null, val applied: Boolean = false)

class PlanTransferController(private val repository: PlanningRepository, private val scope: CoroutineScope,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }) {
    private val mutable = MutableStateFlow(PlanTransferState())
    val state = mutable.asStateFlow()
    init { load() }
    private fun run(action: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, error = null, applied = false)
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: IllegalArgumentException) { mutable.value = mutable.value.copy(error = error.message ?: "Проверьте план", preview = null) }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = "Не удалось прочитать или сохранить план. Попробуйте ещё раз.", preview = null) }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun load() = run { mutable.value = mutable.value.copy(snapshot = repository.snapshot()) }
    fun export() = run {
        val snapshot = repository.snapshot()
        val zone = TimeZone.currentSystemDefault()
        val prompt = withContext(Dispatchers.Default) { exportChatGptPrompt(snapshot, now(), zone) }
        mutable.value = mutable.value.copy(snapshot = snapshot, prompt = prompt, preview = null)
    }
    fun discardPreview() { if (!state.value.busy) mutable.value = mutable.value.copy(preview = null, error = null, applied = false) }
    fun preview(text: String) = run {
        val parsed = withContext(Dispatchers.Default) { parseImportedPlan(text) }
        val snapshot = repository.snapshot()
        validateImportedPlan(parsed, snapshot, now())
        mutable.value = mutable.value.copy(preview = parsed, snapshot = snapshot)
    }
    fun apply() {
        val plan = state.value.preview ?: return
        run {
            repository.apply(plan, now())
            mutable.value = mutable.value.copy(preview = null, prompt = null, snapshot = repository.snapshot(), applied = true)
        }
    }
}
