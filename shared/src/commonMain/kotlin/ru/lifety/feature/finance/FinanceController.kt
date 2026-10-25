package ru.lifety.feature.finance

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import ru.lifety.core.model.Expense
import ru.lifety.core.repository.ExpenseRepository

data class FinanceUiState(
    val expenses: List<Expense> = emptyList(),
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
)

class FinanceController(private val repository: ExpenseRepository, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(FinanceUiState())
    val state = mutableState.asStateFlow()
    private val writes = Mutex()
    private var observation: Job? = null

    init { retry() }

    fun retry() {
        observation?.cancel()
        mutableState.update { it.copy(loading = true, error = null) }
        observation = scope.launch {
            try {
                repository.observeAll().collect { expenses ->
                    mutableState.update { it.copy(expenses = expenses, loading = false, error = null) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.update { it.copy(loading = false, error = "Не удалось загрузить расходы. Повторите попытку.") }
            }
        }
    }

    suspend fun save(expense: Expense): Boolean {
        if (expense.id.isBlank() || expense.amountMinor <= 0 || expense.merchant.isBlank() ||
            expense.merchant.length > 120 || expense.category.isBlank() || expense.category.length > 80 ||
            !Regex("[A-Z]{3}").matches(expense.currency)) {
            mutableState.update { it.copy(error = "Проверьте магазин, сумму, валюту и категорию.") }
            return false
        }
        return write { repository.upsert(expense) }
    }

    suspend fun delete(id: String): Boolean = write { repository.delete(id) }

    private suspend fun write(action: suspend () -> Unit): Boolean {
        if (!writes.tryLock()) return false
        mutableState.update { it.copy(saving = true, error = null) }
        return try {
            action()
            if (observation?.isActive != true) retry()
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.update { it.copy(error = "Не удалось сохранить изменения. Данные сохранены на экране; повторите попытку.") }
            false
        } finally {
            mutableState.update { it.copy(saving = false) }
            writes.unlock()
        }
    }
}
