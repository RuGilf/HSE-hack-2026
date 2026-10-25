package ru.lifety.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import ru.lifety.core.di.AppContainer
import ru.lifety.core.model.HealthSnapshot
import ru.lifety.database.generated.LifetyDatabase
import kotlin.test.*

class HealthHistoryPersistenceTest {
    @Test fun historyEmitsAnEntireBatchAndUpsertsTheSameHistoricalTimestamps() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LifetyDatabase.Schema.create(driver)
        val container = AppContainer(driver)
        val emissions = Channel<List<HealthSnapshot>>(Channel.UNLIMITED)
        val repo = container.repositories.health
        val job = launch(start = CoroutineStart.UNDISPATCHED) { repo.observeAll().collect { emissions.send(it) } }
        try {
            assertEquals(emptyList(), withTimeout(5000) { emissions.receive() })
            val rows = listOf(HealthSnapshot(100, 420, 100), HealthSnapshot(200, 480, 200))
            repo.updateAll(rows)
            assertEquals(rows.reversed(), withTimeout(5000) { emissions.receive() })
            repo.updateAll(rows.map { it.copy(steps = 300) })
            assertEquals(rows.reversed().map { it.copy(steps = 300) }, repo.observeAll().first())
            assertEquals(200L, repo.observeLatest().first()!!.timestamp)
        } finally { job.cancelAndJoin(); container.close() }
    }

    @Test fun aFailedBatchRollsBackEveryRow() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LifetyDatabase.Schema.create(driver)
        driver.execute(null, "CREATE TRIGGER test_abort_health BEFORE INSERT ON healthSnapshot WHEN NEW.steps < 0 BEGIN SELECT RAISE(ABORT, 'test_invalid'); END", 0)
        AppContainer(driver).use { container ->
            val repo = container.repositories.health
            val original = HealthSnapshot(1, 420, 10)
            repo.update(original)
            assertFailsWith<Exception> { repo.updateAll(listOf(HealthSnapshot(2, 480, 100), HealthSnapshot(3, 400, -1))) }
            assertEquals(listOf(original), repo.observeAll().first())
        }
    }

    @Test fun oldAndImportedHistorySurviveReopeningTheExistingSchema() = runBlocking {
        val file = Files.createTempFile("lifety-health-history", ".db")
        val rows = listOf(HealthSnapshot(100, 400, null), HealthSnapshot(200, null, 8000))
        try {
            val driver = JdbcSqliteDriver("jdbc:sqlite:$file")
            LifetyDatabase.Schema.create(driver)
            AppContainer(driver).use { it.repositories.health.updateAll(rows) }
            AppContainer(JdbcSqliteDriver("jdbc:sqlite:$file")).use {
                assertEquals(rows.reversed(), it.repositories.health.observeAll().first())
            }
        } finally { Files.deleteIfExists(file) }
    }
}
