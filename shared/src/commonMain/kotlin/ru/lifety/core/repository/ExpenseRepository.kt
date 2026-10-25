package ru.lifety.core.repository

import kotlinx.coroutines.flow.Flow
import ru.lifety.core.model.Expense

interface ExpenseRepository {
    fun observeAll(): Flow<List<Expense>>
    suspend fun upsert(expense: Expense)
    suspend fun delete(id: String)
}
