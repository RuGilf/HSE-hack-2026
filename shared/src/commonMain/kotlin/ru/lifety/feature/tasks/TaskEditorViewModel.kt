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
import ru.lifety.core.planner.taskTimingError
import ru.lifety.core.repository.TaskRepository
import kotlin.uuid.Uuid

data class TaskEditorState(
    val task: Task? = null,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
)

class TaskEditorViewModel(
    private val repository: TaskRepository,
    private val taskId: String?,
    initialScheduledAt: Long?,
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
                val task = repository.observeAll().first().firstOrNull { it.id == taskId }
                mutableState.value = TaskEditorState(task = task, loading = false,
                    error = if (task == null) "Эта задача уже удалена" else null)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.value = TaskEditorState(loading = false, error = "Не удалось открыть задачу") }
        }
    }

    fun setTitle(title: String) = edit { copy(title = title) }
    fun setScheduledAt(value: Long?) = edit { copy(scheduledAt = value) }
    fun setDeadlineAt(value: Long?) = edit { copy(deadlineAt = value) }
    fun setCompleted(value: Boolean) = edit { copy(isCompleted = value) }

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
        taskTimingError(task)?.let { error ->
            mutableState.update { it.copy(error = error) }
            return
        }
        persist { repository.upsert(task.copy(title = task.title.trim())) }
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
