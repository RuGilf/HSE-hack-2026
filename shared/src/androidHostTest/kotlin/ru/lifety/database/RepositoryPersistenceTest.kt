package ru.lifety.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import ru.lifety.core.di.AppContainer
import ru.lifety.core.model.Expense
import ru.lifety.core.model.HealthSnapshot
import ru.lifety.core.model.Task
import ru.lifety.database.generated.LifetyDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RepositoryPersistenceTest {
    private fun memoryContainer(): AppContainer {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LifetyDatabase.Schema.create(driver)
        return AppContainer(driver)
    }

    @Test
    fun tasksEmitInsertFullReplacementAndDelete() = runBlocking {
        val container = memoryContainer()
        val snapshots = Channel<List<Task>>(Channel.UNLIMITED)
        val job = launch(start = CoroutineStart.UNDISPATCHED) { container.repositories.tasks.observeAll().collect { snapshots.send(it) } }
        suspend fun next() = withTimeout(5_000) { snapshots.receive() }
        try {
            val repo = container.repositories.tasks
            assertEquals(emptyList(), next())
            val task = Task("opaque / ? % Ю", "Сохранить", 100L, 200L, false)
            repo.upsert(task)
            assertEquals(listOf(task), next())
            val edited = task.copy(title = "Изменено", scheduledAt = null, deadlineAt = null, isCompleted = true)
            repo.upsert(edited)
            assertEquals(listOf(edited), next())
            val earlier = task.copy(id = "a")
            repo.upsert(earlier)
            assertEquals(listOf(earlier, edited), next())
            repo.delete(task.id)
            assertEquals(listOf(earlier), next())
            repo.delete("absent")
            assertEquals(listOf(earlier), repo.observeAll().first())
            repo.delete(earlier.id)
            // A no-op delete can also invalidate a query; wait for the intended snapshot.
            assertTrue(withTimeout(5_000) { repo.observeAll().first().isEmpty() })
        } finally { job.cancel(); job.join(); container.close(); snapshots.close() }
    }

    @Test
    fun expensesEmitUpdatesAndUseStableTimestampThenIdOrder() = runBlocking {
        val container = memoryContainer()
        val snapshots = Channel<List<Expense>>(Channel.UNLIMITED)
        val job = launch(start = CoroutineStart.UNDISPATCHED) { container.repositories.expenses.observeAll().collect { snapshots.send(it) } }
        suspend fun next() = withTimeout(5_000) { snapshots.receive() }
        try {
            val repo = container.repositories.expenses
            assertEquals(emptyList(), next())
            val older = Expense("z", 9_007_199_254_740_993L, "RUB", "Магазин", "other", 100, "manual")
            repo.upsert(older)
            assertEquals(listOf(older), next())
            val newer = older.copy(id = "b", timestamp = 200)
            repo.upsert(newer)
            assertEquals(listOf(newer, older), next())
            val tie = newer.copy(id = "a")
            repo.upsert(tie)
            assertEquals(listOf(tie, newer, older), next())
            val edited = tie.copy(amountMinor = 0, currency = "JPY", merchant = "", category = "changed", source = "notification")
            repo.upsert(edited)
            assertEquals(listOf(edited, newer, older), next())
            repo.delete("b")
            assertEquals(listOf(edited, older), next())
            repo.delete("absent")
            assertEquals(listOf(edited, older), repo.observeAll().first())
        } finally { job.cancel(); job.join(); container.close(); snapshots.close() }
    }

    @Test
    fun healthEmitsLatestTimestampAndReplacesNullableValues() = runBlocking {
        val container = memoryContainer()
        val snapshots = Channel<HealthSnapshot?>(Channel.UNLIMITED)
        val job = launch(start = CoroutineStart.UNDISPATCHED) { container.repositories.health.observeLatest().collect { snapshots.send(it) } }
        suspend fun next() = withTimeout(5_000) { snapshots.receive() }
        try {
            val repo = container.repositories.health
            assertNull(next())
            val latest = HealthSnapshot(200, 480, Int.MAX_VALUE)
            repo.update(latest)
            assertEquals(latest, next())
            repo.update(HealthSnapshot(100, 420, 20))
            assertEquals(latest, next())
            val replaced = latest.copy(sleepMinutes = null, steps = null)
            repo.update(replaced)
            assertEquals(replaced, next())
        } finally { job.cancel(); job.join(); container.close(); snapshots.close() }
    }

    @Test
    fun everyRepositoryRestoresDataAfterClosingAndReopeningFile() = runBlocking {
        val file = Files.createTempFile("lifety-persistence-", ".db")
        val task = Task("t", "После перезапуска", null, 1792886400000L, true)
        val expense = Expense("e", 9_007_199_254_740_993L, "RUB", "Store", "other", 200, "manual")
        val health = HealthSnapshot(200, null, 12345)
        try {
            val firstDriver = JdbcSqliteDriver("jdbc:sqlite:$file")
            LifetyDatabase.Schema.create(firstDriver)
            AppContainer(firstDriver).use { first ->
                first.repositories.tasks.upsert(task)
                first.repositories.expenses.upsert(expense)
                first.repositories.health.update(health)
            }
            // No Schema.create on reopen: the same tables and rows must already exist.
            AppContainer(JdbcSqliteDriver("jdbc:sqlite:$file")).use { reopened ->
                assertEquals(listOf(task), reopened.repositories.tasks.observeAll().first())
                assertEquals(listOf(expense), reopened.repositories.expenses.observeAll().first())
                assertEquals(health, reopened.repositories.health.observeLatest().first())
                reopened.repositories.tasks.delete(task.id)
                reopened.repositories.expenses.delete(expense.id)
            }
            AppContainer(JdbcSqliteDriver("jdbc:sqlite:$file")).use { third ->
                assertEquals(emptyList(), third.repositories.tasks.observeAll().first())
                assertEquals(emptyList(), third.repositories.expenses.observeAll().first())
                assertEquals(health, third.repositories.health.observeLatest().first())
            }
        } finally { Files.deleteIfExists(file) }
    }
}
