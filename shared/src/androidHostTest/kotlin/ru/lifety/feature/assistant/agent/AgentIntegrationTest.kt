package ru.lifety.feature.assistant.agent

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import ru.lifety.core.model.Task
import kotlin.test.*

class AgentIntegrationTest {
    @Test fun runtimeRejectsASecondSessionUntilTheFirstIsClosed() = runBlocking {
        val root = Files.createTempDirectory("lifety-agent-session").toFile()
        try {
            val vault = object : AgentVault {
                override val description = "test"
                override suspend fun load() = AgentCredentials(ProviderSettings("https://example.com/v1", "test-model"), "test-key")
                override suspend fun save(credentials: AgentCredentials) = Unit
            }
            val runtime = AgentRuntime(AgentWorkspace(LocalAgentDisk(root.resolve("workspace").path)),
                JsonAgentStore(LocalAgentDisk(root.resolve("private").path)), vault, { 1 }, kotlinx.coroutines.sync.Mutex())
            val first = runtime.open()
            try { assertFailsWith<AgentFault> { runtime.open() } } finally { first.close() }
            val second = runtime.open(); second.close()
        } finally { root.deleteRecursively() }
    }

    @Test fun fileSearchReturnsTheMatchAndItsReadableOffsetInALongJsonLine() = runBlocking {
        val root = Files.createTempDirectory("lifety-agent-search").toFile()
        try {
            val workspace = AgentWorkspace(LocalAgentDisk(root.path))
            workspace.write("notes/long.json", "x".repeat(2000) + "needle" + "y".repeat(500))
            val env = LifetyAgentEnvironment(null, workspace, { 1 })
            val tool = env.registry.find("files_search")!!
            val result = tool.execute(ToolInput(buildJsonObject { put("query", "needle") }, AgentScopes(), AgentDocument(), "u") { _, _, _ -> error("not a mutation") })
            val match = result["matches"]!!.jsonArray.single().jsonObject
            assertTrue(match["text"]!!.jsonPrimitive.content.contains("needle"))
            val offset = match["offset"]!!.jsonPrimitive.int
            assertTrue(workspace.read("notes/long.json").drop(offset).take(300).contains("needle"))
        } finally { root.deleteRecursively() }
    }

    @Test fun agentUsesRealWorkspaceAndTaskWaitsForApproval() = runBlocking {
        val root = Files.createTempDirectory("lifety-agent-loop").toFile()
        try {
            val tasks = TestTasks()
            val workspace = AgentWorkspace(LocalAgentDisk(root.resolve("workspace").path))
            val store = JsonAgentStore(LocalAgentDisk(root.resolve("private").path))
            val env = LifetyAgentEnvironment(testRepositories(tasks), workspace, { 1000 })
            var step = 0
            val runner = AgentRunner(AgentModel { request ->
                when (step++) {
                    0 -> ModelReply(calls = listOf(ToolCall("a", "files_read", """{"path":"INDEX.md"}""")))
                    1 -> {
                        assertTrue(request.messages.last { it.role == "tool" }.text.contains("refreshedAt"))
                        ModelReply(calls = listOf(ToolCall("b", "app_propose", """{"kind":"task_upsert","payload":"{\"title\":\"Учиться\"}"}""")))
                    }
                    else -> ModelReply("Предложение готово")
                }
            }, store, env.registry, env::snapshot, { 1000 })
            runner.initialize(); runner.send("Добавь задачу", AgentScopes(tasks = true))
            assertEquals(RunPhase.IDLE, runner.state.value.phase)
            assertTrue(tasks.rows.value.isEmpty())
            val proposal = runner.state.value.document.proposals.single()
            runner.approve(proposal.id); runner.approve(proposal.id)
            assertEquals("Учиться", tasks.rows.value.single().title)
            assertEquals("applied", store.read()!!.proposals.single().status)
            val reopened = AgentRunner(AgentModel { ModelReply("ok") }, store, env.registry, env::snapshot, { 1001 })
            reopened.initialize()
            assertTrue(reopened.state.value.document.messages.any { it.text == "Предложение готово" })
        } finally { root.deleteRecursively() }
    }

    @Test fun approvalRejectsConcurrentTaskEdits() = runBlocking {
        val root = Files.createTempDirectory("lifety-agent-conflict").toFile()
        try {
            val tasks = TestTasks(); tasks.rows.value = listOf(Task("t", "old", null, null, false))
            val env = LifetyAgentEnvironment(testRepositories(tasks), AgentWorkspace(LocalAgentDisk(root.path)), { 1000 })
            var step = 0
            val runner = AgentRunner(AgentModel {
                if (step++ == 0) ModelReply(calls = listOf(ToolCall("a", "app_propose", """{"kind":"task_upsert","payload":"{\"id\":\"t\",\"title\":\"agent edit\"}"}"""))) else ModelReply("ready")
            }, MemoryAgentStore(), env.registry, env::snapshot, { 1000 })
            runner.initialize(); runner.send("Измени", AgentScopes(tasks = true))
            tasks.rows.value = listOf(Task("t", "user edit", null, null, false))
            runner.approve(runner.state.value.document.proposals.single().id)
            assertEquals("user edit", tasks.rows.value.single().title)
            assertTrue(runner.state.value.error.orEmpty().contains("изменилась"))
        } finally { root.deleteRecursively() }
    }

    @Test fun forgetClearsLiveHistoryAndDerivedArtifacts() = runBlocking {
        var resets = 0
        val fact = UserFact("f", "drink", "tea", "general", "u", "tea", 1)
        val store = MemoryAgentStore(AgentDocument(facts = listOf(fact), messages = listOf(AgentMessage("u", "user", "tea")), summary = "tea"))
        val runner = AgentRunner(AgentModel { ModelReply() }, store, AgentTools(emptyList(), reset = { resets++ }), now = { 1 })
        runner.initialize(); runner.forget("f")
        assertTrue(store.document!!.facts.isEmpty())
        assertTrue(store.document!!.messages.isEmpty())
        assertEquals("", store.document!!.summary)
        assertEquals(1, resets)
    }

    @Test fun rejectsGloballyReusedToolIdsBeforeAnySecondAction() = runBlocking {
        val runner = AgentRunner(AgentModel { ModelReply(calls = listOf(ToolCall("same", "missing", "{}"))) },
            MemoryAgentStore(), AgentTools(emptyList()), now = { 1 })
        runner.initialize(); runner.send("go", AgentScopes())
        assertEquals(RunPhase.ERROR, runner.state.value.phase)
        assertEquals(1, runner.state.value.document.messages.count { it.role == "tool" })
    }
}
