package ru.lifety.core.security

import androidx.compose.runtime.Composable
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock

internal val lockSections = linkedMapOf("dashboard" to "Главная", "tasks" to "План", "finance" to "Финансы", "health" to "Здоровье", "assistant" to "Ассистент")
internal fun validPin(pin: String) = pin.length == 6 && pin.all { it in '0'..'9' }

@Serializable
internal data class LockConfiguration(val version: Int = 1, val sections: Set<String> = emptySet(),
    val salt: String = "", val verifier: String = "", val failures: Int = 0, val blockedUntil: Long = 0) {
    val enabled get() = sections.isNotEmpty()
}

internal data class SectionLockState(val ready: Boolean = false, val busy: Boolean = false,
    val configuration: LockConfiguration = LockConfiguration(), val unlocked: Boolean = false,
    val epoch: Long = 0, val error: String? = null) {
    fun protects(route: String): Boolean {
        val canonical = if (route in setOf("calendar", "people")) "tasks" else route
        return canonical in configuration.sections ||
            (canonical in setOf("dashboard", "assistant") && configuration.sections.any { it in setOf("tasks", "finance", "health") })
    }
    fun needsUnlock(route: String) = !ready || (protects(route) && !unlocked)
}

/** Implementations keep the verifier/configuration in Keychain or Keystore-encrypted storage. */
internal interface SectionLockPlatform {
    fun read(): String?
    fun write(value: String)
    fun salt(): String
    fun derive(pin: String, salt: String): String
}

internal class SectionLockController(private val platform: SectionLockPlatform, private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() }) {
    private val json = Json { encodeDefaults = true }
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(SectionLockState())
    val state = mutable.asStateFlow()
    init { reload() }

    fun lock() {
        if (state.value.ready && !state.value.configuration.enabled && !state.value.busy) return
        mutable.update { it.copy(unlocked = false, epoch = it.epoch + 1, error = null) }
    }

    fun reload() = scope.launch {
        mutex.withLock {
            mutable.update { it.copy(busy = true, error = null, unlocked = false) }
            try {
                val configuration = withContext(dispatcher) {
                    platform.read()?.let { json.decodeFromString<LockConfiguration>(it) } ?: LockConfiguration()
                }
                require(configuration.version == 1 && configuration.sections.all { it in lockSections } &&
                    configuration.failures >= 0 && configuration.blockedUntil >= 0 &&
                    (!configuration.enabled || configuration.salt.isNotBlank() && configuration.verifier.isNotBlank()))
                mutable.update { it.copy(ready = true, configuration = configuration) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(ready = false, error = "Не удалось прочитать защиту. Попробуйте ещё раз.") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }

    fun unlock(pin: String, onSuccess: () -> Unit = {}): Job? {
        val epoch = state.value.epoch
        return operation {
            if (authenticate(pin)) {
                val reset = state.value.configuration.copy(failures = 0, blockedUntil = 0)
                persist(reset)
                if (epoch == state.value.epoch) {
                    mutable.update { it.copy(unlocked = true) }
                    onSuccess()
                }
            }
        }
    }

    fun configure(currentPin: String, newPin: String?, sections: Set<String>, onSuccess: () -> Unit = {}): Job? {
        val epoch = state.value.epoch
        return operation {
            require(sections.all { it in lockSections })
            val previous = state.value.configuration
            if (previous.enabled && !authenticate(currentPin)) return@operation
            require(newPin == null || validPin(newPin))
            require(sections.isEmpty() || previous.enabled || newPin != null)
            val next = if (sections.isEmpty()) LockConfiguration() else {
                val salt = if (newPin != null) withContext(dispatcher) { platform.salt() } else previous.salt
                val verifier = if (newPin != null) withContext(dispatcher) { platform.derive(newPin, salt) } else previous.verifier
                LockConfiguration(sections = sections, salt = salt, verifier = verifier)
            }
            persist(next)
            mutable.update { it.copy(unlocked = epoch == it.epoch) }
            if (epoch == state.value.epoch) onSuccess()
        }
    }

    private fun operation(action: suspend () -> Unit): Job? {
        if (state.value.busy || !state.value.ready) return null
        mutable.update { it.copy(busy = true, error = null) }
        return scope.launch {
            mutex.withLock {
                try { action() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { mutable.update { it.copy(ready = false, unlocked = false, error = "Не удалось проверить или сохранить защиту. Повторите попытку.") } }
                finally { mutable.update { it.copy(busy = false) } }
            }
        }
    }

    private suspend fun authenticate(pin: String): Boolean {
        val c = state.value.configuration
        if (!c.enabled) return true
        if (c.blockedUntil > now()) {
            mutable.update { it.copy(error = "Слишком много попыток. Подождите ${(c.blockedUntil - now() + 999) / 1000} с.") }
            return false
        }
        val candidate = if (validPin(pin)) withContext(dispatcher) { platform.derive(pin, c.salt) } else ""
        var difference = candidate.length xor c.verifier.length
        c.verifier.indices.forEach { difference = difference or (c.verifier[it].code xor (candidate.getOrNull(it)?.code ?: 0)) }
        if (difference == 0) return true
        val failures = (c.failures + 1).coerceAtMost(20)
        val delaySeconds = if (failures < 5) 0L else (30L shl (failures - 5).coerceAtMost(4)).coerceAtMost(300)
        persist(c.copy(failures = failures, blockedUntil = if (delaySeconds == 0L) 0 else now() + delaySeconds * 1000))
        mutable.update { it.copy(unlocked = false, error = if (delaySeconds == 0L) "Неверный код" else "Слишком много попыток. Подождите $delaySeconds с.") }
        return false
    }

    private suspend fun persist(configuration: LockConfiguration) {
        withContext(dispatcher) { platform.write(json.encodeToString(configuration)) }
        mutable.update { it.copy(configuration = configuration) }
    }
}

@Composable internal expect fun rememberSectionLockController(): SectionLockController
@Composable internal expect fun ObserveSectionLockBackground(onBackground: () -> Unit)
