package ru.lifety.feature.assistant.agent

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import ru.lifety.core.repository.Repositories

data class AgentChatState(
    val credentials: AgentCredentials = AgentCredentials(),
    val view: AgentViewState = AgentViewState(phase = RunPhase.IDLE),
    val connected: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
)

/** Foreground screen owner. Credentials are never serialized into Compose saved state. */
class AgentChatController(private val runtime: AgentRuntime, private val repositories: Repositories?, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(AgentChatState())
    val state = mutableState.asStateFlow()
    val workspace get() = runtime.workspace
    val credentialStorageDescription get() = runtime.vault.description
    private val gate = Mutex()
    private var session: AgentSession? = null
    private var observer: Job? = null
    private var activeRun: Job? = null

    private fun operation(action: suspend () -> Unit): Job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
        if (!gate.tryLock()) return@launch
        mutableState.value = state.value.copy(busy = true, error = null)
        try { action() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (fault: AgentFault) { mutableState.value = state.value.copy(error = fault.message) }
        catch (_: Exception) { mutableState.value = state.value.copy(error = "Не удалось завершить действие. Проверьте подключение и повторите.") }
        finally {
            mutableState.value = state.value.copy(busy = false, connected = session != null,
                view = session?.runner?.state?.value ?: state.value.view)
            gate.unlock()
        }
    }

    fun initialize() = operation {
        val credentials = runtime.vault.load()
        mutableState.value = state.value.copy(credentials = credentials)
        if (credentials.key.isNotBlank() && credentials.settings.model.isNotBlank()) bind(credentials)
    }

    private suspend fun detach() {
        session?.cancel(); activeRun?.join(); observer?.cancelAndJoin(); session?.close()
        activeRun = null; session = null; observer = null
    }

    private suspend fun bind(credentials: AgentCredentials) {
        detach()
        val opened = runtime.open(repositories, credentials)
        session = opened
        mutableState.value = state.value.copy(view = opened.runner.state.value, connected = true)
        observer = scope.launch {
            opened.runner.state.collect { view -> mutableState.value = state.value.copy(view = view) }
        }
    }

    fun connect(credentials: AgentCredentials) = operation {
        credentials.settings.validate()
        if (credentials.key.isBlank()) throw AgentFault("settings", "Введите API-ключ")
        runtime.vault.save(credentials)
        mutableState.value = state.value.copy(credentials = credentials)
        bind(credentials)
    }

    fun send(text: String): Job? {
        val current = session ?: return null
        if (state.value.busy || text.isBlank()) return null
        return operation { activeRun = current.start(text); activeRun?.join() }
    }
    fun cancel() { session?.cancel() }
    private fun mutate(action: suspend (AgentRunner) -> Unit) = operation {
        val runner = session?.runner ?: throw AgentFault("settings", "Сначала подключите модель")
        action(runner)
    }
    fun changeScopes(scopes: AgentScopes) = mutate { it.changeScopes(scopes) }
    fun approve(id: String) = mutate { it.approve(id) }
    fun reject(id: String) = mutate { it.reject(id) }
    fun forget(id: String) = mutate { it.forget(id) }
    fun newChat() = mutate { it.newChat() }
    fun close() { observer?.cancel(); session?.cancel(); session?.close(); session = null }
}
