package ru.lifety.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.lifety.core.model.Task
import ru.lifety.core.model.TaskPlan
import ru.lifety.core.planner.taskIntervalError
import ru.lifety.core.repository.TaskRepository
import ru.lifety.core.repository.PlanningRepository
import kotlin.uuid.Uuid

data class TaskEditorState(
    val task: Task? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val duration: String = "",
    val approach: String = "",
    val goalId: String? = null,
    val hadDetails: Boolean = false,
)

class TaskEditorViewModel(
    private val repository: TaskRepository,
    private val taskId: String?,
    initialScheduledAt: Long?,
    private val planning: PlanningRepository? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        if (taskId == null) TaskEditorState(Task(Uuid.random().toString(), "", initialScheduledAt, null, false), loading = false)
        else TaskEditorState(),
    )
    val state = mutableState.asStateFlow()
    private val mutableFinished = MutableSharedFlow<Unit>()
    val finished = mutableFinished.asSharedFlow()

    init { if (taskId != null) reload() }

    fun reload() {
        if (taskId == null) return
        mutableState.value = TaskEditorState()
        viewModelScope.launch {
            try {
                val snapshot = planning?.snapshot()
                val task = (snapshot?.tasks ?: repository.observeAll().first()).firstOrNull { it.id == taskId }
                val details = snapshot?.details?.firstOrNull { it.taskId == taskId }
                mutableState.value = TaskEditorState(task = task, loading = false,
                    duration = details?.durationMinutes?.toString().orEmpty(), approach = details?.approach.orEmpty(), goalId = details?.goalId, hadDetails = details != null,
                    error = if (task == null) "Эта задача уже удалена" else null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.value = TaskEditorState(loading = false, error = "Не удалось открыть задачу") }
        }
    }

    fun setTitle(title: String) = edit { copy(title = title) }
    fun setScheduledAt(value: Long?) = edit { copy(scheduledAt = value) }
    fun setDeadlineAt(value: Long?) = edit { copy(deadlineAt = value) }
    fun setCompleted(value: Boolean) = edit { copy(isCompleted = value) }
    fun setDuration(value: String) { if (!state.value.saving) mutableState.update { it.copy(duration = value.filter { c -> c in '0'..'9' }.take(4), error = null) } }
    fun setApproach(value: String) { if (!state.value.saving) mutableState.update { it.copy(approach = value.take(6000), error = null) } }
    fun setGoal(value: String?) { if (!state.value.saving) mutableState.update { it.copy(goalId = value, error = null) } }

    private fun edit(update: Task.() -> Task) {
        if (mutableState.value.saving) return
        mutableState.update { it.copy(task = it.task?.update(), error = null) }
    }

    fun save() {
        val snapshot = mutableState.value
        val task = snapshot.task ?: return
        if (snapshot.saving) return
        if (task.title.isBlank()) {
            mutableState.update { it.copy(error = "Добавьте название задачи") }
            return
        }
        val duration = snapshot.duration.toIntOrNull()
        if (planning != null && ((snapshot.duration.isNotBlank() || snapshot.approach.isNotBlank() || snapshot.goalId != null || snapshot.hadDetails) && (duration == null || duration !in 1..1440))) {
            mutableState.update { it.copy(error = "Укажите длительность от 1 до 1440 минут") }; return
        }
        taskIntervalError(task, duration)?.let { error ->
            mutableState.update { it.copy(error = error) }
            return
        }
        val savedTask = task.copy(title = task.title.trim())
        persist {
            if (planning == null) repository.upsert(savedTask)
            else planning.saveTask(savedTask, duration?.let { TaskPlan(task.id, it, snapshot.approach.trim(), snapshot.goalId) })
        }
    }

    fun delete() {
        val task = mutableState.value.task ?: return
        if (taskId == null || mutableState.value.saving) return
        persist { repository.delete(task.id) }
    }

    private fun persist(action: suspend () -> Unit) {
        mutableState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try { action(); mutableFinished.emit(Unit) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(error = "Не удалось сохранить изменения. Попробуйте ещё раз.") } }
            finally { mutableState.update { it.copy(saving = false) } }
        }
    }
}
