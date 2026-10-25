package ru.lifety.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Expense(
    val id: String,
    val amountMinor: Long,
    val currency: String,
    val merchant: String,
    val category: String,
    val timestamp: Long,
    val source: String,
)
