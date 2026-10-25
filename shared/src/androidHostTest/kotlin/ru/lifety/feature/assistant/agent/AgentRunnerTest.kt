package ru.lifety.feature.assistant.agent

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class AgentRunnerTest {
    @Test fun toolResultsReturnToTheModelAndAnUnknownToolIsAnObservation() = runBlocking {
        val store = MemoryAgentStore()
        var step = 0
        val model = AgentModel { request ->
            if (step++ == 0) ModelReply(calls = listOf(ToolCall("c1", "missing", "{}")))
            else {
                assertTrue(request.messages.last().text.contains("unknown_tool"))
                ModelReply(text = "Готово")
            }
        }
        val runner = AgentRunner(model, store, AgentTools(emptyList()), now = { 1000 })
        runner.initialize()
        runner.send("Привет", AgentScopes())
        assertEquals("Готово", runner.state.value.document.messages.last().text)
        assertEquals(RunPhase.IDLE, runner.state.value.phase)
        assertEquals(1, store.document!!.messages.count { it.role == "tool" })
    }

    @Test fun budgetStopsARepeatedToolLoopAndEveryCallGetsAResult() = runBlocking {
        var counter = 0
        val runner = AgentRunner(AgentModel { ModelReply(calls = listOf(ToolCall("loop-${counter++}", "missing", "{}"))) },
            MemoryAgentStore(), AgentTools(emptyList()), now = { 1000 }, limits = AgentLimits(maxModelCalls = 2))
        runner.initialize()
        runner.send("Исследуй", AgentScopes())
        assertEquals(RunPhase.LIMIT, runner.state.value.phase)
        val messages = runner.state.value.document.messages
        assertEquals(2, messages.count { it.role == "tool" })
        assertTrue(runner.state.value.error != null)
    }

    @Test fun recoversUnfinishedToolCallsWithoutExecutingThemAgain() = runBlocking {
        val store = MemoryAgentStore(AgentDocument(messages = listOf(
            AgentMessage("m1", "user", "Выполни"),
            AgentMessage("m2", "assistant", calls = listOf(ToolCall("interrupted", "danger", "{}"))),
        )))
        val runner = AgentRunner(AgentModel { error("not needed") }, store, AgentTools(emptyList()), now = { 1000 })
        runner.initialize()
        assertEquals("interrupted", runner.state.value.document.messages.last().callId)
        assertTrue(runner.state.value.document.messages.last().text.contains("interrupted"))
    }
}

internal class MemoryAgentStore(var document: AgentDocument? = null) : AgentStore {
    override suspend fun read() = document
    override suspend fun write(document: AgentDocument) { this.document = document }
}
