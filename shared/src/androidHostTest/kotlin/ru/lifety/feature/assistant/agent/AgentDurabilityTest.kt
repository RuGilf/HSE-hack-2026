package ru.lifety.feature.assistant.agent

import java.nio.file.Files
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.test.*

class AgentDurabilityTest {
    @Test fun oversizedCompactionStopsLocallyWithoutSendingOrLosingHistory() = runBlocking {
        var calls = 0
        val messages = (0..6).flatMap { i -> listOf(
            AgentMessage("u$i", "user", "goal $i"),
            AgentMessage("a$i", "assistant", calls = listOf(ToolCall("c$i", "files_write", "x".repeat(20000)))),
            AgentMessage("t$i", "tool", "done", callId = "c$i"),
        ) }
        val store = MemoryAgentStore(AgentDocument(messages = messages))
        val runner = AgentRunner(AgentModel { calls++; ModelReply("unexpected") }, store, AgentTools(emptyList()), now = { 1 })
        runner.initialize(); runner.send("continue", AgentScopes())
        assertEquals(0, calls)
        assertEquals(RunPhase.ERROR, runner.state.value.phase)
        assertTrue(store.document!!.messages.any { it.id == "a0" && it.calls.single().arguments.length == 20000 })
    }

    @Test fun failedInitializationPreventsAllDocumentMutations() = runBlocking {
        var writes = 0
        val store = object : AgentStore {
            override suspend fun read(): AgentDocument? = error("unreadable existing checkpoint")
            override suspend fun write(document: AgentDocument) { writes++ }
        }
        val runner = AgentRunner(AgentModel { ModelReply() }, store, AgentTools(emptyList()), now = { 1 })
        runner.initialize(); runner.newChat(); runner.forget("f"); runner.reject("p")
        assertEquals(0, writes)
        assertEquals(RunPhase.ERROR, runner.state.value.phase)
    }

    @Test fun nextSendSettlesAResultLostByAnArchiveFailure() = runBlocking {
        var step = 0
        val runner = AgentRunner(AgentModel { request ->
            if (step++ == 0) ModelReply(calls = listOf(ToolCall("lost", "missing", "{}"))) else {
                assertTrue(request.messages.any { it.role == "tool" && it.callId == "lost" })
                ModelReply("recovered")
            }
        }, MemoryAgentStore(), AgentTools(emptyList(), archive = { _, _ -> error("archive unavailable") }), now = { 1 })
        runner.initialize(); runner.send("first", AgentScopes())
        assertEquals(RunPhase.ERROR, runner.state.value.phase)
        runner.send("continue", AgentScopes())
        assertEquals(RunPhase.IDLE, runner.state.value.phase)
    }

    @Test fun compactsLargeToolArgumentsAndPinsTheCurrentUserGoal() = runBlocking {
        val old = listOf(
            AgentMessage("u0", "user", "old goal"),
            AgentMessage("a0", "assistant", calls = listOf(ToolCall("old-call", "files_write", """{"content":"${"x".repeat(13000)}"}"""))),
            AgentMessage("t0", "tool", "{\"status\":\"ok\"}", callId = "old-call"),
        ) + (1..5).flatMap { i -> listOf(AgentMessage("u$i", "user", "previous $i"), AgentMessage("a$i", "assistant", "done $i")) }
        var summaryCalls = 0
        val runner = AgentRunner(AgentModel { request ->
            if (request.purpose == "summary") {
                summaryCalls++
                assertTrue(request.messages.none { it.role == "assistant" || it.role == "tool" || it.calls.isNotEmpty() },
                    "Compaction receives a quoted transcript, not an active tool conversation to continue")
                assertTrue(request.messages.single().text.contains("old-call"), "The quoted transcript must preserve tool evidence")
                ModelReply("""{"goal":"old goal","constraints":"","verified":"done","artifacts":"scripts/result.js","failed_attempts":"none","next":"current task"}""")
            } else {
                assertTrue(request.context.contains("CURRENT GOAL"))
                assertTrue(request.context.contains("scripts/result.js"))
                ModelReply("finished")
            }
        }, MemoryAgentStore(AgentDocument(messages = old)), AgentTools(emptyList()), now = { 1000 })
        runner.initialize(); runner.send("CURRENT GOAL", AgentScopes())
        assertEquals(1, summaryCalls)
        assertEquals(RunPhase.IDLE, runner.state.value.phase)
        assertTrue(runner.state.value.document.summaryThrough > 0)
        assertTrue(runner.state.value.document.messages.any { it.id == "a0" && it.calls.single().arguments.length > 13000 })
    }

    @Test fun cancellationPersistsAnInterruptedReceiptAndNeverReplaysTheTool() = runBlocking {
        val started = CompletableDeferred<Unit>(); var calls = 0
        val tool = AgentTool("wait", "wait", objectSchema(buildJsonObject {})) {
            calls++; started.complete(Unit); awaitCancellation()
        }
        val store = MemoryAgentStore()
        val runner = AgentRunner(AgentModel { ModelReply(calls = listOf(ToolCall("one", "wait", "{}"))) }, store, AgentTools(listOf(tool)), now = { 1000 })
        runner.initialize()
        val job = launch { runner.send("go", AgentScopes()) }
        started.await(); job.cancelAndJoin()
        assertEquals(RunPhase.CANCELLED, runner.state.value.phase)
        assertTrue(store.document!!.messages.last().text.contains("interrupted"))
        val reopened = AgentRunner(AgentModel { ModelReply("ok") }, store, AgentTools(listOf(tool)), now = { 1001 })
        reopened.initialize()
        assertEquals(1, calls)
        assertEquals(1, reopened.state.value.document.messages.count { it.role == "tool" })
    }

    @Test fun failedCheckpointPreventsTheModelAndActionsFromRunning() = runBlocking {
        var modelCalls = 0
        val store = object : AgentStore {
            override suspend fun read(): AgentDocument? = null
            override suspend fun write(document: AgentDocument) { error("disk full") }
        }
        val runner = AgentRunner(AgentModel { modelCalls++; ModelReply("unsafe") }, store, AgentTools(emptyList()), now = { 1 })
        runner.initialize(); runner.send("go", AgentScopes())
        assertEquals(0, modelCalls)
        assertEquals(RunPhase.ERROR, runner.state.value.phase)
    }

    @Test fun memoryCorrectionReplacesTheOldFactAndKeepsItsSource() = runBlocking {
        val root = Files.createTempDirectory("lifety-agent-memory").toFile()
        try {
            val workspace = AgentWorkspace(LocalAgentDisk(root.path))
            val env = LifetyAgentEnvironment(null, workspace, { 1000 })
            var value = "tea"; var quote = "люблю чай"; var next = 0
            val runner = AgentRunner(AgentModel {
                if (next++ % 2 == 0) ModelReply(calls = listOf(ToolCall("c$next", "app_propose", buildJsonObject {
                    put("kind", "memory_set"); put("payload", buildJsonObject { put("key", "drink"); put("value", value); put("domain", "general"); put("quote", quote) }.toString())
                }.toString()))) else ModelReply("ready")
            }, MemoryAgentStore(), env.registry, env::snapshot, { 1000 })
            runner.initialize(); runner.send("Я люблю чай", AgentScopes(memory = true))
            assertTrue(runner.state.value.document.facts.isEmpty())
            runner.approve(runner.state.value.document.proposals.last().id)
            value = "coffee"; quote = "предпочитаю кофе"
            runner.send("Теперь предпочитаю кофе", AgentScopes(memory = true))
            runner.approve(runner.state.value.document.proposals.last().id)
            val fact = runner.state.value.document.facts.single()
            assertEquals("coffee", fact.value)
            assertEquals("предпочитаю кофе", fact.sourceQuote)
            assertTrue(workspace.read("memory/profile.json").contains("coffee"))
            assertFalse(workspace.read("memory/profile.json").contains("tea"))
        } finally { root.deleteRecursively() }
    }

    @Test fun scriptCannotWriteAReadOnlySnapshotAndRecoversFromMemoryExhaustion() = runBlocking {
        val root = Files.createTempDirectory("lifety-agent-restrictions").toFile()
        try {
            val workspace = AgentWorkspace(LocalAgentDisk(root.path))
            workspace.generated("data/example.json", "original")
            workspace.write("scripts/overwrite.js", "await fs.writeText('data/example.json','changed');")
            assertEquals("script_error", workspace.runScript("scripts/overwrite.js")["status"]!!.jsonPrimitive.content)
            assertEquals("original", workspace.read("data/example.json"))
            workspace.write("scripts/memory.js", "const a = new Array(100000000).fill('large'); console.log(a.length);")
            assertTrue(workspace.runScript("scripts/memory.js")["status"]!!.jsonPrimitive.content in setOf("script_error", "timeout"))
            workspace.write("scripts/alive.js", "console.log('alive');")
            assertEquals("ok", workspace.runScript("scripts/alive.js")["status"]!!.jsonPrimitive.content)
        } finally { root.deleteRecursively() }
    }
}
