package ru.lifety.feature.assistant.agent

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import ru.lifety.core.repository.Repositories

data class AgentCredentials(val settings: ProviderSettings = ProviderSettings(), val key: String = "")
interface AgentVault {
    val description: String
    suspend fun load(): AgentCredentials
    suspend fun save(credentials: AgentCredentials)
}
class AgentRuntime internal constructor(val workspace: AgentWorkspace, private val store: AgentStore, val vault: AgentVault,
    private val now: () -> Long, private val gate: Mutex, private val sessionLease: Mutex = Mutex()) {
    /** No HTTP call is made until start(). Caller owns the returned session and must close it. */
    suspend fun open(repositories: Repositories? = null, credentials: AgentCredentials? = null): AgentSession {
        if (!sessionLease.tryLock()) throw AgentFault("busy", "Для этой рабочей папки уже открыта сессия агента")
        var model: OpenAiCompatibleModel? = null
        try {
            val configured = credentials ?: vault.load()
            model = OpenAiCompatibleModel(configured.settings, configured.key)
            val environment = LifetyAgentEnvironment(repositories, workspace, now)
            val runner = AgentRunner(model, store, environment.registry, environment::snapshot, now,
                AgentLimits(contextTokens = configured.settings.contextTokens), gate)
            runner.initialize()
            return AgentSession(runner, workspace, model) { sessionLease.unlock() }
        } catch (failure: Exception) { model?.close(); sessionLease.unlock(); throw failure }
    }
}

class AgentSession internal constructor(val runner: AgentRunner, val workspace: AgentWorkspace, private val model: OpenAiCompatibleModel, private val release: () -> Unit) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var active: Job? = null
    private val startGate = Mutex()
    private val closeGate = Mutex()
    /** User-driven foreground run; collect runner.state for progress and pending proposals. */
    fun start(text: String): Job {
        if (!scope.isActive) throw AgentFault("closed", "Сессия уже закрыта")
        if (!startGate.tryLock()) throw AgentFault("busy", "Предыдущий запуск ещё работает")
        return scope.launch { runner.send(text, runner.state.value.document.scopes) }.also { job -> active = job; job.invokeOnCompletion { startGate.unlock() } }
    }
    fun cancel() { active?.cancel() }
    fun close() {
        if (!closeGate.tryLock()) return
        scope.cancel()
        val finish = { model.close(); release() }
        active?.invokeOnCompletion { finish() } ?: finish()
    }
}
