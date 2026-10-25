package ru.lifety.core.planner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.lifety.core.model.Task
import ru.lifety.core.repository.TaskRepository

data class TaskListState(
    val tasks: List<Task> = emptyList(),
    val loading: Boolean = true,
    val readError: String? = null,
    val actionError: String? = null,
    val busyIds: Set<String> = emptySet(),
)

enum class TaskFilter(val title: String) {
    ACTIVE("Актуальные"), COMPLETED("Завершённые"), ALL("Все");
}

fun visibleTasks(tasks: List<Task>, filter: TaskFilter): List<Task> = tasks
    .filter { filter == TaskFilter.ALL || it.isCompleted == (filter == TaskFilter.COMPLETED) }
    .sortedWith(compareBy<Task> { it.isCompleted }.thenBy { it.scheduledAt ?: it.deadlineAt ?: Long.MAX_VALUE }.thenBy { it.id })

@OptIn(ExperimentalCoroutinesApi::class)
class TaskListViewModel(private val repository: TaskRepository) : ViewModel() {
    private val reload = MutableStateFlow(0)
    private val actionError = MutableStateFlow<String?>(null)
    private val busy = MutableStateFlow<Set<String>>(emptySet())
    private val rows = reload.flatMapLatest {
        repository.observeAll().map { TaskListState(tasks = it, loading = false) }.catch { error ->
            if (error is CancellationException) throw error
            emit(TaskListState(loading = false, readError = "Не удалось загрузить задачи"))
        }
    }
    val state: StateFlow<TaskListState> = combine(rows, actionError, busy) { list, error, ids ->
        list.copy(actionError = error, busyIds = ids)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListState())

    fun retry() { reload.update { it + 1 } }
    fun dismissError() { actionError.value = null }
    fun toggle(task: Task) = mutate(task.id) { repository.upsert(task.copy(isCompleted = !task.isCompleted)) }
    fun delete(id: String) = mutate(id) { repository.delete(id) }

    private fun mutate(id: String, action: suspend () -> Unit) {
        if (id in busy.value) return
        busy.update { it + id }
        actionError.value = null
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { actionError.value = "Не удалось изменить задачу. Попробуйте ещё раз." }
            finally { busy.update { it - id } }
        }
    }
}
