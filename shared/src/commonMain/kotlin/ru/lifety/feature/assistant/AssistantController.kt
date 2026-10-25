package ru.lifety.feature.assistant

import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.lifety.core.repository.Repositories
import ru.lifety.feature.assistant.ai.*

/** Process memory only. No saved state, preferences, SQLite or serialization. */
internal object AssistantSession { var settings = AiSettings() }

data class AssistantUiState(
    val settings: AiSettings = AiSettings(),
    val goal: AnalysisGoal = AnalysisGoal.DAY_PLAN,
    val preview: AssistantContext? = null,
    val result: AnalysisResult? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

class AssistantController(
    private val repositories: Repositories?,
    private val scope: CoroutineScope,
    private val provider: AiProvider = KtorAiProvider(),
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    initialSettings: AiSettings = AssistantSession.settings,
    private val saveSettings: (AiSettings) -> Unit = { AssistantSession.settings = it },
) {
    private val mutableState = MutableStateFlow(AssistantUiState(settings = initialSettings))
    val state: StateFlow<AssistantUiState> = mutableState.asStateFlow()
    private var job: Job? = null

    fun updateSettings(settings: AiSettings) {
        if (state.value.busy) return
        saveSettings(settings)
        mutableState.value = state.value.copy(settings = settings, preview = null, result = null, error = null)
    }

    fun chooseGoal(goal: AnalysisGoal) {
        if (!state.value.busy) mutableState.value = state.value.copy(goal = goal, preview = null, result = null, error = null)
    }

    fun preparePreview() {
        val current = state.value
        if (current.busy) return
        if (repositories == null) { mutableState.value = current.copy(error = "Локальное хранилище ещё не подключено."); return }
        if (!current.settings.permissions.any) { mutableState.value = current.copy(error = "Разрешите хотя бы одну категорию для предпросмотра."); return }
        mutableState.value = current.copy(busy = true, preview = null, result = null, error = null)
        job = scope.launch {
            try {
                val context = ContextBuilder(repositories).build(current.settings.permissions, now())
                mutableState.value = mutableState.value.copy(preview = context, busy = false)
            } catch (_: TimeoutCancellationException) {
                mutableState.value = mutableState.value.copy(busy = false, error = "Хранилище не ответило вовремя. Повторите предпросмотр.")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.value = mutableState.value.copy(busy = false, error = "Не удалось прочитать локальные данные. Повторите предпросмотр.") }
        }
    }

    /** The only path to the provider; it sends precisely the frozen preview. */
    fun analyze() {
        val current = state.value
        val context = current.preview ?: return
        if (current.busy) return
        if (!settingsAreValid(current.settings)) {
            mutableState.value = current.copy(error = "Введите API-ключ, модель и полный HTTPS URL chat/completions без query-параметров.")
            return
        }
        mutableState.value = current.copy(busy = true, result = null, error = null)
        job = scope.launch {
            try {
                val result = provider.analyze(current.settings, current.goal, context)
                mutableState.value = mutableState.value.copy(result = result, busy = false)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: AiFailure) { mutableState.value = mutableState.value.copy(busy = false, error = failure.userMessage) }
            catch (_: Exception) { mutableState.value = mutableState.value.copy(busy = false, error = "Не удалось получить рекомендацию. Повторите запрос.") }
        }
    }

    fun cancel() {
        job?.cancel()
        mutableState.value = state.value.copy(busy = false)
    }
}
