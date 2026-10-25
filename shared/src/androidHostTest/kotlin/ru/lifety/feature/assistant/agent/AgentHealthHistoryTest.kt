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
    @Test fun exportsAllStoredDaysAndRevokingHealthRemovesTheHistory() = runBlocking {
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
}
