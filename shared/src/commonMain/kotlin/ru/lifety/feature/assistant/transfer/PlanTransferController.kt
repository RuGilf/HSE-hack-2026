package ru.lifety.feature.assistant.transfer

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.lifety.core.planning.*
import ru.lifety.core.model.*
import kotlinx.serialization.json.JsonObject
import ru.lifety.core.repository.Repositories
import kotlinx.datetime.TimeZone
import kotlin.uuid.Uuid
import kotlin.time.Clock

internal data class PlanTransferState(val busy: Boolean = false, val loading: Boolean = true,
    val snapshot: PlanSnapshot? = null, val sleep: SleepContext? = null, val workspace: ChatGptWorkspace = ChatGptWorkspace(),
    val extra: JsonObject? = null, val preview: ImportedPlan? = null, val error: String? = null, val applied: Boolean = false, val draftStale: Boolean = false)

internal class PlanTransferController(private val repositories: Repositories, private val scope: CoroutineScope,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() }) {
    private val planning = requireNotNull(repositories.planning)
    private val exchange = requireNotNull(repositories.chatGptExchange)
    private val mutable = MutableStateFlow(PlanTransferState())
    val state = mutable.asStateFlow()
    private var observation: Job? = null
    init { load() }

    fun load() {
        observation?.cancel()
        mutable.value = state.value.copy(loading = true, error = null)
        observation = scope.launch {
            try {
                val signals = listOfNotNull(
                    repositories.tasks.observeAll().map { Unit }, repositories.goals?.observeAll()?.map { Unit }, planning.observeAll().map { Unit },
                    repositories.health.observeAll().map { Unit }, exchange.observe().map { Unit }, repositories.healthMetrics?.observe()?.map { Unit },
                    repositories.expenses.observeAll().map { Unit }, repositories.people?.observeAll()?.map { Unit }, repositories.schedule?.observeAll()?.map { Unit })
                merge(*signals.toTypedArray()).conflate().collect {
                    val (snapshot, sleep, workspace, extra) = current()
                    val stale = workspace.draft?.let { it.sourceFingerprint != contextSourceFingerprint(snapshot, workspace.preferences, sleep, now(), zone(), extra) } ?: false
                    mutable.value = state.value.copy(snapshot = snapshot, sleep = sleep, workspace = workspace, extra = extra, loading = false, draftStale = stale)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.value = state.value.copy(loading = false, error = "Не удалось загрузить данные обмена. Повторите загрузку.") }
        }
    }
    private fun run(action: suspend () -> Unit) {
        if (state.value.busy) return
        mutable.value = state.value.copy(busy = true, error = null, applied = false)
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: IllegalArgumentException) { mutable.value = state.value.copy(error = error.message ?: "Проверьте план", preview = null) }
            catch (_: Exception) { mutable.value = state.value.copy(error = "Не удалось прочитать или сохранить данные. Попробуйте ещё раз.", preview = null) }
            finally { mutable.value = state.value.copy(busy = false) }
        }
    }
    private data class Current(val snapshot: PlanSnapshot, val sleep: SleepContext?, val workspace: ChatGptWorkspace, val extra: JsonObject?)
    private suspend fun current(): Current {
        val workspace = exchange.observe().first()
        val snapshot = planning.snapshot()
        val time = now(); val tz = zone()
        val sleep = if (workspace.preferences.includeSleep) {
            val history = repositories.health.observeAll().first()
            withContext(Dispatchers.Default) { summarizeSleep(history, time, tz) }
        } else null
        val metrics = if (workspace.preferences.healthMetricIds.isNotEmpty()) repositories.healthMetrics?.snapshot() ?: HealthMetricSnapshot() else HealthMetricSnapshot()
        val health = if (workspace.preferences.includeSteps) repositories.health.observeAll().first() else emptyList()
        val expenses = if (workspace.preferences.includeFinance) repositories.expenses.observeAll().first() else emptyList()
        val people = if (workspace.preferences.includePeople) repositories.people?.observeAll()?.first().orEmpty() else emptyList()
        val extra = withContext(Dispatchers.Default) { selectedExtraContext(workspace.preferences, metrics, health, expenses, people, time, tz) }
        return Current(snapshot, sleep, workspace, extra)
    }
    fun prepare(initial: Boolean, request: String) = run {
        val (snapshot, sleep, workspace, extra) = current()
        val time = now(); val tz = zone(); val id = Uuid.random().toString()
        val draft = withContext(Dispatchers.Default) { prepareChatGptDraft(snapshot, workspace.preferences, sleep, workspace.lastSent, initial, request, id, time, tz, extra) }
        exchange.saveDraft(draft)
        mutable.value = state.value.copy(preview = null)
    }
    fun savePreferences(preferences: ChatGptPreferences, onSaved: () -> Unit) = run {
        exchange.savePreferences(preferences.copy(instructions = preferences.instructions.trim(), conversationUrl = preferences.conversationUrl.trim()))
        mutable.value = state.value.copy(preview = null)
        onSaved()
    }
    private fun checkDraft(snapshot: PlanSnapshot, sleep: SleepContext?, workspace: ChatGptWorkspace, extra: JsonObject?): ChatGptDraft {
        val draft = requireNotNull(workspace.draft) { "Подготовьте запрос" }
        require(draft.sourceFingerprint == contextSourceFingerprint(snapshot, workspace.preferences, sleep, now(), zone(), extra)) { "Данные изменились. Подготовьте актуальное обновление." }
        return draft
    }
    fun handoff(onReady: (String) -> Unit) = run {
        val (snapshot, sleep, workspace, extra) = current()
        onReady(checkDraft(snapshot, sleep, workspace, extra).prompt)
    }
    fun markSent() = run {
        val (snapshot, sleep, workspace, extra) = current()
        val draft = checkDraft(snapshot, sleep, workspace, extra)
        exchange.markSent(draft.id, now())
    }
    fun discardPreview() { if (!state.value.busy) mutable.value = state.value.copy(preview = null, error = null, applied = false) }
    fun preview(text: String) = run {
        val parsed = withContext(Dispatchers.Default) { parseImportedPlan(text) }
        val (snapshot, sleep, workspace, extra) = current()
        validateImportedPlan(parsed, snapshot, now())
        validateChatGptResponse(parsed, workspace, snapshot, sleep, now(), zone(), extra)
        mutable.value = state.value.copy(preview = parsed, snapshot = snapshot)
    }
    fun apply() {
        val plan = state.value.preview ?: return
        run {
            // DB rechecks task snapshot and v2 context, then commits schedule/report/exchange receipt atomically.
            planning.apply(plan, now())
            mutable.value = state.value.copy(preview = null, applied = true)
        }
    }
}
