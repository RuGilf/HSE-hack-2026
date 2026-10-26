package ru.lifety.database

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSUUID
import ru.lifety.core.di.AppContainer
import ru.lifety.core.model.Task
import ru.lifety.core.model.Expense
import ru.lifety.core.model.HealthSnapshot
import ru.lifety.database.generated.LifetyDatabase
import kotlin.test.*

class IosPersistenceTest {
    @Test fun sqliteRestoresAllRepositoriesAndHealthHistoryAfterReopen(): Unit = runBlocking {
        val file = "lifety-test-${NSUUID().UUIDString}.db"
        fun open() = AppContainer(NativeSqliteDriver(LifetyDatabase.Schema, file))
        val task = Task("ios-task", "Task", 123L, 456L, false)
        val expense = Expense("ios-expense", 9_007_199_254_740_993L, "RUB", "Shop", "other", 100L, "manual")
        val history = listOf(HealthSnapshot(100, 400, null), HealthSnapshot(200, null, 7000))
        open().use { container ->
            container.repositories.tasks.upsert(task)
            container.repositories.expenses.upsert(expense)
            container.repositories.health.updateAll(history)
        }
        open().use { container ->
            assertEquals(listOf(task), container.repositories.tasks.observeAll().first())
            assertEquals(listOf(expense), container.repositories.expenses.observeAll().first())
            assertEquals(history.reversed(), container.repositories.health.observeAll().first())
            assertEquals(history.last(), container.repositories.health.observeLatest().first())
            container.repositories.tasks.delete(task.id)
            container.repositories.expenses.delete(expense.id)
        }
        open().use { container ->
            assertTrue(container.repositories.tasks.observeAll().first().isEmpty())
            assertTrue(container.repositories.expenses.observeAll().first().isEmpty())
            assertEquals(2, container.repositories.health.observeAll().first().size)
        }
    }
}
