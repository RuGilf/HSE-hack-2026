package ru.lifety.feature.assistant.agent

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import ru.lifety.feature.assistant.agent.playground.PlaygroundData
import kotlin.test.*

/** Live response-quality regressions on an isolated synthetic workspace, never the browser session. */
class PersonalAdviceEvalTest {
    @Test fun bedtimeAdviceUsesOrdinaryLanguageAndOneUsefulQuestion() = runBlocking {
        withAgent("bedtime") { runner, _, scopes ->
            runner.send("во сколько мне стоит ложиться спать", scopes)
            val first = answer(runner)
            assertOrdinaryAnswer(first)
            assertTrue(first.contains('?'), "Ask for the missing wake-up time: $first")
            assertTrue(first.count { it == '?' } <= 1, "Ask one useful question, not a questionnaire: $first")
            assertTrue(runner.state.value.document.proposals.isEmpty(), "Advice must not create app changes")
            runner.send("Завтра нужно встать в 07:00. Обычно хорошо себя чувствую после 8 часов сна, засыпаю минут за 20.", scopes)
            val second = answer(runner)
            assertOrdinaryAnswer(second)
            assertTrue("22:40" in second, "8 hours plus 20 minutes before 07:00 is 22:40: $second")
            assertTrue(runner.state.value.document.proposals.none { it.status == "applied" })
        }
    }

    @Test fun sleepAnalysisDiscoversImportedHistoryAndRecallsSavedReportInANewChat() = runBlocking {
        withAgent("sleep-history") { runner, workspace, scopes ->
            runner.send("Проанализируй мой сон за последние две недели. Посчитай среднюю длительность и сохрани разбор, чтобы мы могли вернуться к нему в другом разговоре. " +
                "Для проверки сохрани также reports/sleep-review.json с числовыми полями recordCount, totalMinutes, averageMinutes и пояснением ограничений данных.", scopes)
            answer(runner)
            val report = agentJson.parseToJsonElement(workspace.read("reports/sleep-review.json")).jsonObject
            assertEquals(14, report["recordCount"]!!.jsonPrimitive.int, "Use imported history rather than the one latest measurement")
            assertEquals(5960, report["totalMinutes"]!!.jsonPrimitive.int)
            assertEquals(425.7142857, report["averageMinutes"]!!.jsonPrimitive.double, 0.01)
            runner.newChat()
            runner.send("Что показал наш предыдущий разбор сна за две недели? Не пересчитывай его, найди сохранённый результат.", scopes)
            assertOrdinaryAnswer(answer(runner))
            assertTrue(runner.state.value.document.messages.flatMap { it.calls }.any {
                it.name in setOf("files_read", "files_search", "script_run")
            }, "Recall must retrieve persisted evidence, not invent a previous analysis")
        }
    }

    private fun answer(runner: AgentRunner): String {
        assertEquals(RunPhase.IDLE, runner.state.value.phase, runner.state.value.error)
        return runner.state.value.document.messages.last { it.role == "assistant" && it.calls.isEmpty() }.text
    }

    private fun assertOrdinaryAnswer(text: String) {
        assertTrue(text.length <= 1400, "An everyday answer should be brief (${text.length} chars): $text")
        assertFalse(Regex("(?:data|memory|schemas|skills|history|scratch|reports|notes)/|INDEX\\.md|LOCAL_STATE|timestamp:|healthHistoryAvailable", RegexOption.IGNORE_CASE).containsMatchIn(text),
            "Internal provenance belongs in the report/trace, not ordinary advice: $text")
    }

    private suspend fun withAgent(name: String, block: suspend (AgentRunner, AgentWorkspace, AgentScopes) -> Unit) {
        val key = System.getenv("LIFETY_LLM_API_KEY")
        val endpoint = System.getenv("LIFETY_LLM_BASE_URL")
        val model = System.getenv("LIFETY_LLM_MODEL")
        org.junit.Assume.assumeTrue("Set LIFETY_LLM_* to enable live eval", !key.isNullOrBlank() && !endpoint.isNullOrBlank() && !model.isNullOrBlank())
        val root = Files.createTempDirectory("lifety-advice-$name").toFile()
        val transport = OpenAiCompatibleModel(ProviderSettings(endpoint!!, model!!), key!!)
        val traces = mutableListOf<JsonObject>()
        val traceFile = java.io.File("build/advice-eval/$name.json").apply { requireNotNull(parentFile).mkdirs() }
        var runner: AgentRunner? = null
        try {
            val data = PlaygroundData()
            val workspace = AgentWorkspace(LocalAgentDisk(root.resolve("workspace").path))
            val env = LifetyAgentEnvironment(data.repositories, workspace, { data.now })
            val scopes = AgentScopes(finance = true, tasks = true, health = true, memory = true)
            val observed = AgentModel { request ->
                val reply = transport.complete(request)
                traces += buildJsonObject {
                    put("purpose", request.purpose); put("request", requestMessages(request))
                    put("text", reply.text); put("calls", agentJson.encodeToJsonElement(reply.calls))
                    put("promptTokens", reply.promptTokens?.let(::JsonPrimitive) ?: JsonNull)
                }
                traceFile.writeText(buildJsonObject { put("model", model); put("traces", JsonArray(traces)) }.toString())
                reply
            }
            runner = AgentRunner(observed, MemoryAgentStore(), env.registry, env::snapshot, { data.now }, AgentLimits(contextTokens = 65536))
            runner.initialize(); runner.changeScopes(scopes)
            workspace.write("notes/demo-profile.md", data.guide)
            workspace.write("scratch/demo-health-history.csv", data.healthCsv)
            block(runner, workspace, scopes)
        } finally {
            // Synthetic fixture and trace only. API key/headers are never captured.
            traceFile.writeText(buildJsonObject {
                put("model", model); put("traces", JsonArray(traces))
                runner?.state?.value?.let { put("state", agentJson.encodeToJsonElement(it.document)); put("phase", it.phase.name); put("error", it.error) }
            }.toString())
            transport.close(); root.deleteRecursively()
        }
    }
}
