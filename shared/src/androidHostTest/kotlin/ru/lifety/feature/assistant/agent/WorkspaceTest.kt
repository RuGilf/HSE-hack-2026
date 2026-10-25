package ru.lifety.feature.assistant.agent

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import ru.lifety.core.model.*
import ru.lifety.core.repository.*
import kotlinx.coroutines.flow.*
import kotlin.test.*

class WorkspaceTest {
    @Test fun resetErasesAnOrphanBackupAndItCannotBeResurrected(): Unit = runBlocking {
        val root = Files.createTempDirectory("lifety-agent-backup").toFile()
        try {
            root.resolve("reports").mkdirs()
            root.resolve("reports/.private.md.bak").writeText("revoked personal data")
            root.resolve("reports/.other.md.tmp").writeText("unfinished private data")
            val disk = LocalAgentDisk(root.path)
            AgentWorkspace(disk).clear()
            assertNull(disk.read("reports/private.md"))
            assertNull(disk.read("reports/other.md"))
            assertFalse(root.resolve("reports/.private.md.bak").exists())
            assertFalse(root.resolve("reports/.other.md.tmp").exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun runsWrittenAnalysisWithExactMoneyAndPersistsItsReport() = runBlocking {
        val root = Files.createTempDirectory("lifety-agent").toFile()
        try {
            val workspace = AgentWorkspace(LocalAgentDisk(root.path))
            val env = LifetyAgentEnvironment(testRepositories(), workspace, { 1000 })
            env.prepare(AgentScopes(finance = true), AgentDocument())
            workspace.write("scripts/total.js", """
                const rows = JSON.parse(await fs.readText('data/expenses/records.json'));
                const totals = {};
                for (const r of rows) totals[r.currency] = (totals[r.currency] || 0n) + BigInt(r.amountMinor);
                await fs.writeText('reports/total.json', JSON.stringify(totals, (_, v) => typeof v === 'bigint' ? v.toString() : v));
                console.log('done');
            """.trimIndent())
            val result = workspace.runScript("scripts/total.js")
            assertEquals("ok", result["status"]!!.jsonPrimitive.content)
            val reopened = AgentWorkspace(LocalAgentDisk(root.path))
            val report = agentJson.parseToJsonElement(reopened.read("reports/total.json")).jsonObject
            assertEquals("9007199254740993", report["RUB"]!!.jsonPrimitive.content)
            assertEquals("7", report["USD"]!!.jsonPrimitive.content)
            assertTrue(result.toString().contains("done"))
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsEscapesSymlinksAndWritesIntoSourceData() = runBlocking {
        val root = Files.createTempDirectory("lifety-agent").toFile()
        val outside = Files.createTempDirectory("lifety-outside").toFile()
        try {
            val workspace = AgentWorkspace(LocalAgentDisk(root.path))
            for (path in listOf("../secret", "/secret", "C:/secret", "notes/../../secret", "data/expenses/x", "skills/x")) {
                assertFailsWith<AgentFault>(path) { workspace.write(path, "bad") }
            }
        } finally { root.deleteRecursively(); outside.deleteRecursively() }
    }

    @Test fun rejectsAnExistingDirectoryLink(): Unit = runBlocking {
        val root = Files.createTempDirectory("lifety-agent").toFile()
        val outside = Files.createTempDirectory("lifety-outside").toFile()
        try {
            try { Files.createSymbolicLink(root.resolve("scripts").toPath(), outside.toPath()) }
            catch (failure: java.nio.file.FileSystemException) { org.junit.Assume.assumeNoException("Host cannot create symlinks", failure) }
            val workspace = AgentWorkspace(LocalAgentDisk(root.path))
            assertFailsWith<AgentFault> { workspace.write("scripts/escape.js", "bad") }
            assertFalse(outside.resolve("escape.js").exists())
        } finally { root.deleteRecursively(); outside.deleteRecursively() }
    }

    @Test fun busyLoopIsInterruptedAndTheNextScriptStillWorks() = runBlocking {
        val root = Files.createTempDirectory("lifety-agent").toFile()
        try {
            val workspace = AgentWorkspace(LocalAgentDisk(root.path), scriptTimeoutMillis = 100)
            workspace.write("scripts/loop.js", "while (true) {}")
            assertEquals("timeout", workspace.runScript("scripts/loop.js")["status"]!!.jsonPrimitive.content)
            workspace.write("scripts/ok.js", "console.log('alive');")
            assertTrue(workspace.runScript("scripts/ok.js").toString().contains("alive"))
        } finally { root.deleteRecursively() }
    }

    @Test fun scriptHasNoNetworkOrHostRuntimeAndAnOverwriteRequiresRevision() = runBlocking {
        val root = Files.createTempDirectory("lifety-agent").toFile()
        try {
            val workspace = AgentWorkspace(LocalAgentDisk(root.path))
            workspace.write("reports/note.md", "old")
            assertFailsWith<AgentFault> { workspace.write("reports/note.md", "lost edit") }
            val rev = workspace.files("reports").single().revision
            workspace.write("reports/note.md", "new", rev)
            workspace.write("scripts/capabilities.js", "console.log([typeof fetch, typeof require, typeof process, typeof Java].join(','));")
            assertTrue(workspace.runScript("scripts/capabilities.js").toString().contains("undefined,undefined,undefined,undefined"))
            assertEquals("new", workspace.read("reports/note.md"))
        } finally { root.deleteRecursively() }
    }

    @Test fun revokedScopesRemoveDerivedFilesAndMissingHealthIsExplicit(): Unit = runBlocking {
        val root = Files.createTempDirectory("lifety-agent").toFile()
        try {
            val workspace = AgentWorkspace(LocalAgentDisk(root.path))
            val env = LifetyAgentEnvironment(testRepositories(), workspace, { 1000 })
            env.prepare(AgentScopes(finance = true, health = true), AgentDocument())
            assertTrue(workspace.read("data/health/latest.json").contains("null"))
            assertTrue(workspace.read("INDEX.md").contains("healthHistoryAvailable=false"))
            workspace.write("reports/private.md", "derived finance data")
            env.reset(AgentScopes())
            env.prepare(AgentScopes(), AgentDocument())
            assertFailsWith<AgentFault> { workspace.read("data/expenses/records.json") }
            assertFailsWith<AgentFault> { workspace.read("reports/private.md") }
        } finally { root.deleteRecursively() }
    }
}

internal class TestTasks : TaskRepository {
    val rows = MutableStateFlow(emptyList<Task>())
    override fun observeAll() = rows
    override suspend fun upsert(task: Task) { rows.value = rows.value.filterNot { it.id == task.id } + task }
    override suspend fun delete(id: String) { rows.value = rows.value.filterNot { it.id == id } }
}

internal fun testRepositories(tasks: TestTasks = TestTasks()) = Repositories(tasks,
    object : ExpenseRepository {
        override fun observeAll() = flowOf(listOf(
            Expense("a", 9007199254740993L, "RUB", "Shop", "food", 100, "manual"),
            Expense("b", 7L, "USD", "Shop", "food", 100, "manual")))
        override suspend fun upsert(expense: Expense) = Unit
        override suspend fun delete(id: String) = Unit
    }, object : HealthRepository {
        override fun observeLatest() = flowOf(HealthSnapshot(100, null, null))
        override suspend fun update(snapshot: HealthSnapshot) = Unit
    })
