package ru.lifety.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import ru.lifety.core.di.AppContainer
import ru.lifety.core.model.ExpenseCategory
import ru.lifety.database.generated.LifetyDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExpenseCategoryPersistenceTest {
    @Test fun definitionsEmitRenamesColorsAndArchiveAndSurviveReopen() = runBlocking {
        val file = Files.createTempFile("lifety-categories-", ".db")
        val rows = Channel<List<ExpenseCategory>>(Channel.UNLIMITED)
        try {
            val driver = JdbcSqliteDriver("jdbc:sqlite:$file")
            LifetyDatabase.Schema.create(driver)
            val category = ExpenseCategory("custom:travel", "Поездки", "#73B7FF")
            val changed = category.copy(name = "Путешествия", colorHex = "#F6C76B", archived = true)
            AppContainer(driver).use { app ->
                val repo = requireNotNull(app.repositories.expenseCategories)
                val job = launch(start = CoroutineStart.UNDISPATCHED) { repo.observeAll().collect { rows.send(it) } }
                try {
                    assertEquals(emptyList(), withTimeout(5_000) { rows.receive() })
                    repo.upsert(category)
                    assertEquals(listOf(category), withTimeout(5_000) { rows.receive() })
                    repo.upsert(changed)
                    assertEquals(listOf(changed), withTimeout(5_000) { rows.receive() })
                    assertFailsWith<IllegalArgumentException> { repo.upsert(changed.copy(colorHex = "wrong")) }
                    assertEquals(listOf(changed), repo.observeAll().first())
                } finally { job.cancel(); job.join() }
            }
            AppContainer(JdbcSqliteDriver("jdbc:sqlite:$file")).use { app ->
                assertEquals(listOf(changed), requireNotNull(app.repositories.expenseCategories).observeAll().first())
            }
        } finally { rows.close(); Files.deleteIfExists(file) }
    }

    @Test fun migrationFromVersionOnePreservesAllExistingData() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        // The actual version-1 storage, before expenseCategory existed.
        driver.execute(null, "CREATE TABLE task(id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, scheduledAt INTEGER, deadlineAt INTEGER, isCompleted INTEGER NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE expense(id TEXT NOT NULL PRIMARY KEY, amountMinor INTEGER NOT NULL, currency TEXT NOT NULL, merchant TEXT NOT NULL, category TEXT NOT NULL, timestamp INTEGER NOT NULL, source TEXT NOT NULL)", 0)
        driver.execute(null, "CREATE TABLE healthSnapshot(timestamp INTEGER NOT NULL PRIMARY KEY, sleepMinutes INTEGER, steps INTEGER)", 0)
        driver.execute(null, "INSERT INTO task VALUES ('t','План',NULL,300,0)", 0)
        driver.execute(null, "INSERT INTO expense VALUES ('e',9007199254740993,'RUB','Магазин','food',100,'manual')", 0)
        driver.execute(null, "INSERT INTO healthSnapshot VALUES (100,420,8000)", 0)
        LifetyDatabase.Schema.migrate(driver, 1, LifetyDatabase.Schema.version)
        AppContainer(driver).use { app ->
            val expense = app.repositories.expenses.observeAll().first().single()
            assertEquals(9_007_199_254_740_993L, expense.amountMinor)
            assertEquals("food", expense.category)
            assertEquals("План", app.repositories.tasks.observeAll().first().single().title)
            assertEquals(420, app.repositories.health.observeLatest().first()!!.sleepMinutes)
            assertEquals(emptyList(), requireNotNull(app.repositories.people).observeAll().first())
            val categories = requireNotNull(app.repositories.expenseCategories)
            assertEquals(emptyList(), categories.observeAll().first())
            categories.upsert(ExpenseCategory("food", "Еда", "#F6C76B"))
            assertEquals("food", app.repositories.expenses.observeAll().first().single().category)
        }
    }
}
