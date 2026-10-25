package ru.lifety.feature.assistant.agent

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import ru.lifety.core.model.Task
import kotlin.test.*

class AgentIntegrationTest {
    @Test fun directoryDiscoveryAcceptsATrailingSeparatorWithoutAllowingEscapes(): Unit = runBlocking {
        val root = Files.createTempDirectory("lifety-agent-directory").toFile()
        try {
            val workspace = AgentWorkspace(LocalAgentDisk(root.path))
            workspace.write("notes/source.md", "data")
            assertEquals(listOf("notes/source.md"), workspace.files("notes/").map { it.path })
            assertFailsWith<AgentFault> { workspace.files("../") }
            assertFailsWith<AgentFault> { workspace.files("/") }
        } finally { root.deleteRecursively() }
    }

    @Test fun scriptToolSavesAndExecutesCodeInOneCallAndRejectsAStaleOverwriteBeforeRunning() = runBlocking {
        val root = Files.createTempDirectory("lifety-agent-inline-script").toFile()
        try {
            val workspace = AgentWorkspace(LocalAgentDisk(root.path))
            workspace.write("scratch/input.json", "[10,20,30]")
            val tool = LifetyAgentEnvironment(null, workspace, { 1 }).registry.find("script_run")!!
            val code = """const rows=JSON.parse(await fs.readText('scratch/input.json')); const total=rows.reduce((a,b)=>a+b,0); await fs.writeText('reports/total.txt',String(total)); console.log(total);"""
            suspend fun run(content: String) = buildJsonObject { put("path", "scripts/sum.js"); put("content", content) }.let { args ->
                validateArguments(tool.schema, args)
                tool.execute(ToolInput(args, AgentScopes(), AgentDocument(), "u") { _, _, _ -> error("no app mutation") })
            }
            assertEquals("ok", run(code)["status"]!!.jsonPrimitive.content)
            assertEquals(code, workspace.read("scripts/sum.js"), "The executed code must remain inspectable")
            assertEquals("60", workspace.read("reports/total.txt"))
            workspace.delete("reports/total.txt", revision("60"))
            val fault = assertFailsWith<AgentFault> { run("console.log('replacement')") }
            assertEquals("conflict", fault.code)
            assertFalse(workspace.files("reports").any { it.path == "reports/total.txt" }, "A failed write must not execute the old script")
        } finally { root.deleteRecursively() }
    }

    @Test fun aSmallSourceFitsInOneDefaultReadWhileExplicitSlicesStayBounded() = runBlocking {
        val root = Files.createTempDirectory("lifety-agent-read-size").toFile()
        try {
            val workspace = AgentWorkspace(LocalAgentDisk(root.path))
            val source = "x".repeat(5400) + "END_OF_SOURCE"
            workspace.write("scratch/source.csv", source)
            val tool = LifetyAgentEnvironment(null, workspace, { 1 }).registry.find("files_read")!!
            suspend fun read(limit: Int? = null) = tool.execute(ToolInput(buildJsonObject {
                put("path", "scratch/source.csv"); limit?.let { put("limit", it) }
            }, AgentScopes(), AgentDocument(), "u") { _, _, _ -> error("read only") })
            val complete = read()
            assertEquals(source, complete["text"]!!.jsonPrimitive.content, "A small source must not need three provider round trips")
            assertEquals(JsonNull, complete["nextOffset"])
            val sliced = read(2000)
            assertEquals(2000, sliced["text"]!!.jsonPrimitive.content.length)
            assertEquals(2000, sliced["nextOffset"]!!.jsonPrimitive.int)
        } finally { root.deleteRecursively() }
    }

    @Test fun currentTaskEvidenceSurvivesBatchedToolsAndOldTaskEvidenceCanBeOffloaded() = runBlocking {
        val root = Files.createTempDirectory("lifety-agent-working-evidence").toFile()
        try {
            val workspace = AgentWorkspace(LocalAgentDisk(root.path))
            workspace.write("notes/source.md", "verified observation ".repeat(100))
            val env = LifetyAgentEnvironment(null, workspace, { 1 })
            var step = 0
            var currentEvidence = ""
            var olderEvidence = ""
            val model = AgentModel { request ->
                when (step++) {
                    0 -> ModelReply(calls = listOf(ToolCall("body", "files_read", """{"path":"notes/source.md","limit":6000}""")))
                    1 -> ModelReply(calls = (1..6).map { ToolCall("list-$it", "files_list", """{"path":"notes"}""") })
                    2 -> { currentEvidence = request.messages.single { it.callId == "body" }.text; ModelReply("Done") }
                    else -> { olderEvidence = request.messages.single { it.callId == "body" }.text; ModelReply("Next answer") }
                }
            }
            val runner = AgentRunner(model, MemoryAgentStore(), env.registry, env::snapshot, { 1 },
                AgentLimits(compactAfterInputBytes = 100000, contextTokens = 65536))
            runner.initialize(); runner.send("Analyse", AgentScopes())
            assertEquals(RunPhase.IDLE, runner.state.value.phase)
            assertTrue("verified observation" in currentEvidence && currentEvidence.length > 1000,
                "A batch of tool calls must not evict evidence while its task is still running")
            runner.send("Another question", AgentScopes())
            assertTrue("\"status\":\"archived\"" in olderEvidence, "Completed-task evidence should remain retrievable by its archive path")
        } finally { root.deleteRecursively() }
    }

    @Test fun manifestDiscoversImportsAndSavedAnalysesWithoutLoadingTheirContents() = runBlocking {
        val root = Files.createTempDirectory("lifety-agent-discovery").toFile()
        try {
            val workspace = AgentWorkspace(LocalAgentDisk(root.path))
            workspace.write("notes/profile.md", "private content need not enter every prompt")
            workspace.write("scratch/imported-sleep.csv", "date,sleepMinutes\n2026-10-24,440")
            workspace.write("reports/sleep-review.md", "an older analysis, not an authoritative personal fact")
            val env = LifetyAgentEnvironment(testRepositories(), workspace, { 1000 })
            env.prepare(AgentScopes(health = true), AgentDocument())
            val index = env.snapshot(AgentScopes(health = true))["workspace"]!!.jsonPrimitive.content
            assertTrue("scratch/imported-sleep.csv" in index, "Discover supplemental sources before declaring history unavailable")
            assertTrue("reports/sleep-review.md" in index, "A new session needs pointers to past analyses")
            assertTrue("notes/profile.md" in index)
            assertFalse("private content" in index, "Discover names first; retrieve bodies only when relevant")
            assertTrue("healthRepositoryHistoryAvailable=false" in index, "A CSV must not claim repository/cloud history support")
        } finally { root.deleteRecursively() }
    }

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
