package ru.lifety.feature.finance

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import ru.lifety.core.model.ExpenseCategory
import ru.lifety.core.repository.ExpenseCategoryRepository

internal fun defaultExpenseCategories(): List<ExpenseCategory> = financeCategories.map { (id, title) ->
    ExpenseCategory(id, title, "#" + (defaultCategoryColor(id).toArgb().toLong() and 0xFFFFFF).toString(16).padStart(6, '0').uppercase())
}

internal fun resolveExpenseCategories(overrides: List<ExpenseCategory>, usedIds: List<String> = emptyList()): List<ExpenseCategory> {
    val defaults = defaultExpenseCategories()
    val byId = overrides.associateBy { it.id }
    val known = defaults.map { byId[it.id] ?: it } + overrides.filter { it.id !in financeCategories }.sortedBy { it.name.lowercase() }
    val ids = known.map { it.id }.toSet()
    return known + usedIds.distinct().filter { it !in ids }.map { id ->
        ExpenseCategory(id, categoryTitle(id), defaults.last().colorHex)
    }
}

internal val LocalExpenseCategories = staticCompositionLocalOf { defaultExpenseCategories() }

internal fun categoryValidationError(category: ExpenseCategory, existing: List<ExpenseCategory>): String? = when {
    category.id.isBlank() || category.id.length > 80 -> "Некорректная категория."
    category.name.isBlank() -> "Введите название категории."
    category.name.trim().length > 40 -> "Название — до 40 символов."
    !Regex("#[0-9A-Fa-f]{6}").matches(category.colorHex) -> "Цвет указывается в формате #RRGGBB."
    category.archived && existing.none { it.id != category.id && !it.archived } -> "Оставьте хотя бы одну видимую категорию."
    !category.archived && existing.any { !it.archived && it.id != category.id && it.name.trim().equals(category.name.trim(), ignoreCase = true) } -> "Категория с таким названием уже есть."
    else -> null
}

internal data class CategoryUiState(val categories: List<ExpenseCategory> = defaultExpenseCategories(),
    val loading: Boolean = true, val loaded: Boolean = false, val saving: Boolean = false, val error: String? = null)

internal class FinanceCategoryController(private val repository: ExpenseCategoryRepository, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(CategoryUiState())
    val state = mutableState.asStateFlow()
    private val writes = Mutex()
    private var observation: Job? = null
    init { retry() }

    fun retry() {
        observation?.cancel()
        mutableState.update { it.copy(loading = true, error = null) }
        observation = scope.launch {
            try { repository.observeAll().collect { rows -> mutableState.update { it.copy(categories = resolveExpenseCategories(rows), loading = false, loaded = true, error = null) } } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutableState.update { it.copy(loading = false, error = "Не удалось загрузить категории. Повторите попытку.") } }
        }
    }

    suspend fun save(category: ExpenseCategory): Boolean {
        if (mutableState.value.loading || !mutableState.value.loaded || !writes.tryLock()) return false
        return try {
            val normalized = category.copy(name = category.name.trim(), colorHex = category.colorHex.uppercase())
            val error = categoryValidationError(normalized, mutableState.value.categories)
            if (error != null) { mutableState.update { it.copy(error = error) }; false }
            else {
                mutableState.update { it.copy(saving = true, error = null) }
                repository.upsert(normalized)
                // Reflect a successful write immediately even before the reactive query is delivered.
                mutableState.update { it.copy(categories = resolveExpenseCategories(it.categories.filterNot { row -> row.id == normalized.id } + normalized)) }
                if (observation?.isActive != true) retry()
                true
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { mutableState.update { it.copy(error = "Не удалось сохранить категорию. Попробуйте ещё раз.") }; false }
        finally { mutableState.update { it.copy(saving = false) }; writes.unlock() }
    }
}
