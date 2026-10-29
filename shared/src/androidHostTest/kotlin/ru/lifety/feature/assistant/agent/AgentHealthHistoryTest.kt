package ru.lifety.feature.assistant.agent

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import ru.lifety.core.di.AppContainer
import ru.lifety.core.model.HealthSnapshot
import ru.lifety.database.generated.LifetyDatabase
import kotlin.test.*

class AgentHealthHistoryTest {
    @Test fun exportsRecentDaysAndRevokingHealthRemovesTheHistory() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        LifetyDatabase.Schema.create(driver)
        val container = AppContainer(driver)
        val root = Files.createTempDirectory("lifety-health-export").toFile()
        try {
            for (day in 1..3) container.repositories.health.update(HealthSnapshot(day * 86400000L, 400 + day, null))
            val workspace = AgentWorkspace(LocalAgentDisk(root.path))
            val env = LifetyAgentEnvironment(container.repositories, workspace, { 300000000 })
            env.prepare(AgentScopes(health = true), AgentDocument())
            val history = agentJson.parseToJsonElement(workspace.read("data/health/history/records.json")).jsonArray
            assertEquals(listOf(401, 402, 403), history.map { it.jsonObject["sleepMinutes"]!!.jsonPrimitive.int })
            assertEquals(259200000L, agentJson.parseToJsonElement(workspace.read("data/health/latest.json")).jsonObject["timestamp"]!!.jsonPrimitive.long)
            assertTrue(workspace.read("INDEX.md").contains("healthRepositoryHistoryAvailable=true"))
            env.reset(AgentScopes()); env.prepare(AgentScopes(), AgentDocument())
            assertTrue(workspace.files("data/health").isEmpty())
        } finally { container.close(); root.deleteRecursively() }
    }
    @Test fun selectedCustomMetricsUseSummariesAndOldTextIsNeverExported() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY); LifetyDatabase.Schema.create(driver)
        val container = AppContainer(driver)
        val root = Files.createTempDirectory("lifety-selected-health").toFile()
        val now = kotlin.time.Instant.parse("2026-10-30T12:00:00Z").toEpochMilliseconds()
        try {
            val repo = container.repositories.healthMetrics!!
            repo.saveMetric(ru.lifety.core.model.HealthMetric("weight", "Вес", "кг"))
            repo.saveMetric(ru.lifety.core.model.HealthMetric("notes", "Заметки", kind = ru.lifety.core.model.MetricKind.TEXT))
            repo.saveEntry(ru.lifety.core.model.HealthEntry("old", "notes", now - 40L * 86400000, "old-private-note"))
            repo.saveEntry(ru.lifety.core.model.HealthEntry("new", "notes", now, "recent-note"))
            repo.saveEntry(ru.lifety.core.model.HealthEntry("w", "weight", now, "75"))
            container.repositories.health.update(HealthSnapshot(now - 40L * 86400000, 420, 9999))
            val workspace = AgentWorkspace(LocalAgentDisk(root.path))
            val env = LifetyAgentEnvironment(container.repositories, workspace, { now })
            env.prepare(AgentScopes(health = true, healthSleep = false, healthSteps = false, healthMetricIds = setOf("notes")), AgentDocument())
            val summary = workspace.read("data/health/summary.json")
            assertTrue(summary.contains("recent-note"))
            assertFalse(summary.contains("old-private-note")); assertFalse(summary.contains("Вес"))
            assertFalse(summary.contains("sleep")); assertFalse(summary.contains("steps"))
            assertEquals("[]", workspace.read("data/health/history/records.json"))
            env.prepare(AgentScopes(health = true, healthSleep = true, healthSteps = false), AgentDocument())
            val sleep = workspace.read("data/health/summary.json")
            assertTrue(sleep.contains("medianMinutes")); assertFalse(sleep.contains("recent-note")); assertFalse(sleep.contains("9999"))
            assertEquals("[]", workspace.read("data/health/history/records.json"))
        } finally { container.close(); root.deleteRecursively() }
    }

}
