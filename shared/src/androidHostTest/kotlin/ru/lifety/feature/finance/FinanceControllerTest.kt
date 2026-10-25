package ru.lifety.feature.finance

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import ru.lifety.core.model.Expense
import ru.lifety.core.repository.ExpenseRepository

class FinanceControllerTest {
    @Test fun savesEditsAndDeletesThroughTheSharedRepository() = runBlocking {
        val repository = FakeExpenses()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = FinanceController(repository, scope)
            val expense = Expense("one", 12345, "RUB", "Shop", "other", 100, "manual")
            assertTrue(controller.save(expense))
            assertEquals(listOf(expense), controller.state.value.expenses)
            assertTrue(controller.save(expense.copy(merchant = "Edited", amountMinor = 25000)))
            assertEquals(1, controller.state.value.expenses.size)
            assertEquals(25000, controller.state.value.expenses.single().amountMinor)
            assertTrue(controller.delete("one"))
            assertTrue(controller.state.value.expenses.isEmpty())
        } finally { scope.cancel() }
    }

    @Test fun failedWritesKeepExistingExpensesAndShowAnError() = runBlocking {
        val repository = FakeExpenses()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = FinanceController(repository, scope)
            val expense = Expense("one", 100, "RUB", "Shop", "other", 100, "manual")
            controller.save(expense)
            repository.failure = IllegalStateException("disk full")
            assertFalse(controller.delete("one"))
            assertEquals(listOf(expense), controller.state.value.expenses)
            assertTrue(controller.state.value.error != null)
            repository.failure = CancellationException("cancelled")
            var cancelled = false
            try { controller.save(expense) } catch (_: CancellationException) { cancelled = true }
            assertTrue(cancelled)
            assertFalse(controller.state.value.saving)
        } finally { scope.cancel() }
    }

    @Test fun restartsFailedObservationAfterSuccessfulWrite() = runBlocking {
        var subscriptions = 0
        val values = MutableStateFlow(emptyList<Expense>())
        val repository = object : ExpenseRepository {
            override fun observeAll() = kotlinx.coroutines.flow.flow {
                subscriptions++
                if (subscriptions == 1) throw IllegalStateException("read failed")
                values.collect { emit(it) }
            }
            override suspend fun upsert(expense: Expense) { values.value = listOf(expense) }
            override suspend fun delete(id: String) { values.value = emptyList() }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = FinanceController(repository, scope)
            assertTrue(controller.state.value.error != null)
            val expense = Expense("one", 100, "RUB", "Shop", "other", 100, "manual")
            assertTrue(controller.save(expense))
            assertEquals(listOf(expense), controller.state.value.expenses)
            assertEquals(2, subscriptions)
            assertEquals(null, controller.state.value.error)
        } finally { scope.cancel() }
    }
    @Test fun reflectsRepositoryFlowChangesFromOtherFeatures() = runBlocking {
        val repository = FakeExpenses()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = FinanceController(repository, scope)
            assertFalse(controller.state.value.loading)
            val imported = Expense("external", 4250, "RUB", "Shop", "food", 100, "manual")
            repository.values.value = listOf(imported)
            assertEquals(listOf(imported), controller.state.value.expenses)
            repository.values.value = emptyList()
            assertTrue(controller.state.value.expenses.isEmpty())
        } finally { scope.cancel() }
    }
    @Test fun repeatedNotificationConfirmationPreservesTheAlreadyEditedExpense() = runBlocking {
        val repository = FakeExpenses()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = FinanceController(repository, scope)
            val original = Expense("notification:stable", 10000, "RUB", "SHOP", "other", 100, "notification:bank")
            assertTrue(controller.confirmNotification(original))
            val edited = original.copy(amountMinor = 12000, category = "food")
            assertTrue(controller.save(edited))
            // A new subscription simulates reopening Finance after process/UI recreation.
            val reopened = FinanceController(repository, scope)
            assertTrue(reopened.confirmNotification(original))
            assertEquals(listOf(edited), repository.values.value)
            assertEquals(listOf(edited), reopened.state.value.expenses)
        } finally { scope.cancel() }
    }

    private class FakeExpenses : ExpenseRepository {
        val values = MutableStateFlow(emptyList<Expense>())
        var failure: Exception? = null
        override fun observeAll() = values
        override suspend fun upsert(expense: Expense) {
            failure?.let { throw it }
            values.value = values.value.filterNot { it.id == expense.id } + expense
        }
        override suspend fun delete(id: String) {
            failure?.let { throw it }
            values.value = values.value.filterNot { it.id == id }
        }
    }
}
