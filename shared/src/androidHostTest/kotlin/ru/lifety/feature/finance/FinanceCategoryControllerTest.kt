package ru.lifety.feature.finance

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import ru.lifety.core.model.ExpenseCategory
import ru.lifety.core.repository.ExpenseCategoryRepository
import kotlin.test.*

class FinanceCategoryControllerTest {
    private class Store : ExpenseCategoryRepository {
        val rows = MutableStateFlow<List<ExpenseCategory>>(emptyList())
        var failing = false
        var writes = 0
        var gate: CompletableDeferred<Unit>? = null
        override fun observeAll() = rows
        override suspend fun upsert(category: ExpenseCategory) {
            writes++
            gate?.await()
            if (failing) error("storage unavailable")
            rows.value = rows.value.filterNot { it.id == category.id } + category
        }
    }

    @Test fun failedSaveKeepsOldDefinitionAndRetryUpdatesItWithoutReclassification() = runBlocking {
        val job = SupervisorJob()
        val store = Store()
        val controller = FinanceCategoryController(store, CoroutineScope(job + Dispatchers.Unconfined))
        try {
            withTimeout(5_000) { controller.state.first { it.loaded } }
            val changed = controller.state.value.categories.first().copy(name = "Еда", colorHex = "#abcdef")
            store.failing = true
            assertFalse(controller.save(changed))
            assertNotNull(controller.state.value.error)
            assertFalse(controller.state.value.saving)
            assertEquals("Продукты и кафе", controller.state.value.categories.first().name)
            store.failing = false
            assertTrue(controller.save(changed))
            assertEquals(changed.copy(colorHex = "#ABCDEF"), controller.state.value.categories.single { it.id == "food" })
        } finally { job.cancel() }
    }

    @Test fun duplicateAndConcurrentWritesCannotCreateCompetingDefinitions() = runBlocking {
        val job = SupervisorJob()
        val store = Store()
        val controller = FinanceCategoryController(store, CoroutineScope(job + Dispatchers.Unconfined))
        try {
            withTimeout(5_000) { controller.state.first { it.loaded } }
            val custom = ExpenseCategory("custom:travel", "Поездки", "#123456")
            assertFalse(controller.save(custom.copy(name = "Транспорт")))
            assertEquals(0, store.writes)
            val gate = CompletableDeferred<Unit>(); store.gate = gate
            val first = async(start = CoroutineStart.UNDISPATCHED) { controller.save(custom) }
            assertFalse(controller.save(custom.copy(name = "Другое имя")))
            gate.complete(Unit)
            assertTrue(first.await())
            assertEquals(1, store.writes)
            assertEquals(listOf(custom), store.rows.value)
        } finally { job.cancel() }
    }
}
