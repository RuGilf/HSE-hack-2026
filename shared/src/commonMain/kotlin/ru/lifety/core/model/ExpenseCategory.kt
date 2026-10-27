package ru.lifety.core.model

import kotlinx.serialization.Serializable

/** Stable ID is stored in Expense.category; labels and colors can change independently. */
@Serializable
data class ExpenseCategory(val id: String, val name: String, val colorHex: String, val archived: Boolean = false)
