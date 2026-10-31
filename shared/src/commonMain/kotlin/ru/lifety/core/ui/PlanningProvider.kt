package ru.lifety.core.ui

import androidx.compose.runtime.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import ru.lifety.core.model.*
import ru.lifety.core.repository.Repositories

internal data class PlanningUiState(val loading: Boolean = false, val goals: List<Goal> = emptyList(),
    val plans: Map<String, TaskPlan> = emptyMap(), val report: PlanReport? = null, val error: String? = null,
    val schedule: List<ScheduleBlock> = emptyList())
internal val LocalPlanningState = compositionLocalOf { PlanningUiState() }
internal val LocalPlanningRetry = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
internal fun PlanningProvider(repositories: Repositories?, content: @Composable () -> Unit) {
    var retry by remember { mutableIntStateOf(0) }
    val state by produceState(PlanningUiState(loading = repositories?.planning != null), repositories, retry) {
        val goals = repositories?.goals
        val planning = repositories?.planning
        if (goals == null || planning == null) { value = PlanningUiState(); return@produceState }
        combine(goals.observeAll(), planning.observeAll(), planning.observeReport(), repositories.schedule?.observeAll() ?: flowOf(emptyList())) { g, p, r, s ->
            PlanningUiState(goals = g, plans = p.associateBy { it.taskId }, report = r, schedule = s)
        }.catch { error ->
            if (error is CancellationException) throw error
            emit(PlanningUiState(error = "Не удалось загрузить цели и план"))
        }.collect { value = it }
    }
    val reload: () -> Unit = remember { { retry += 1 } }
    CompositionLocalProvider(LocalPlanningState provides state, LocalPlanningRetry provides reload, content = content)
}
