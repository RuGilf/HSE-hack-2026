package ru.lifety.core.repository

import kotlinx.coroutines.flow.Flow
import ru.lifety.core.model.ExpenseCategory

interface ExpenseCategoryRepository {
    /** User categories and overrides of built-in categories, including archived definitions. */
    fun observeAll(): Flow<List<ExpenseCategory>>
    suspend fun upsert(category: ExpenseCategory)
}
