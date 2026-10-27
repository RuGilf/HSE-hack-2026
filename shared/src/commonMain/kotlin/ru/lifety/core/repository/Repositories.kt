package ru.lifety.core.repository

/** One shared set of repository instances, supplied by the composition root. */
data class Repositories(
    val tasks: TaskRepository,
    val expenses: ExpenseRepository,
    val health: HealthRepository,
    val expenseCategories: ExpenseCategoryRepository? = null,
)
