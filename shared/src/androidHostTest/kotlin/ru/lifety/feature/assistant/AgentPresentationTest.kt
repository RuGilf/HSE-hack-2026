package ru.lifety.feature.assistant

import ru.lifety.feature.assistant.agent.ToolCall
import kotlin.test.*

class AgentPresentationTest {
    @Test fun malformedToolArgumentsDoNotCrashTheActivityDetails() {
        assertEquals("В рабочей среде Lifety", agentActionDetail(ToolCall("1", "files_read", """{"path":{"unexpected":1}}""")))
        assertEquals("В рабочей среде Lifety", agentActionDetail(ToolCall("2", "files_read", "broken json")))
        assertEquals("Расходы", agentActionDetail(ToolCall("3", "files_read", """{"path":"data/expenses/records.json"}""")))
    }
}
