package ru.lifety.feature.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import ru.lifety.core.model.Person
import ru.lifety.core.repository.PersonRepository

data class PeopleState(val people: List<Person> = emptyList(), val loading: Boolean = true, val error: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class PeopleViewModel(repository: PersonRepository) : ViewModel() {
    private val reload = MutableStateFlow(0)
    val state: StateFlow<PeopleState> = reload.flatMapLatest {
        repository.observeAll().map { PeopleState(people = it, loading = false) }
            .onStart { emit(PeopleState()) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(PeopleState(loading = false, error = "Не удалось загрузить записи о людях"))
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeopleState())

    fun retry() { reload.update { it + 1 } }
}
