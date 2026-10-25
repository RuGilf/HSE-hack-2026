package ru.lifety.feature.assistant.agent

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

/** Opt-in outcome eval. Credentials exist only in the test process environment. */
class LiveAgentEvalTest {
    @Test fun realModelWritesAndExecutesAnExactMoneyReport() = runBlocking {
        val key = System.getenv("LIFETY_LLM_API_KEY")
        val endpoint = System.getenv("LIFETY_LLM_BASE_URL")
        val model = System.getenv("LIFETY_LLM_MODEL")
        org.junit.Assume.assumeTrue("Set LIFETY_LLM_* to enable live eval", !key.isNullOrBlank() && !endpoint.isNullOrBlank() && !model.isNullOrBlank())
        val root = Files.createTempDirectory("lifety-agent-live-eval").toFile()
        val transport = OpenAiCompatibleModel(ProviderSettings(endpoint!!, model!!), key!!)
        try {
            val workspace = AgentWorkspace(LocalAgentDisk(root.path))
            val env = LifetyAgentEnvironment(testRepositories(), workspace, { 1000 })
            val observed = AgentModel { request ->
                // Synthetic fixture only; never persist real app requests in production logs.
                java.io.File("build/agent-live-last-request.json").apply { parentFile.mkdirs() }.writeText(buildJsonObject {
                    put("model", model); put("messages", requestMessages(request)); put("tools", requestTools(request)); put("max_tokens", request.maxOutputTokens)
                }.toString())
                transport.complete(request)
            }
            val runner = AgentRunner(observed, MemoryAgentStore(), env.registry, env::snapshot, { 1000 }, AgentLimits(maxModelCalls = 12, maxRunMillis = 240000))
            runner.initialize()
            runner.send("Это изолированный тест на искусственных данных. Найди все доступные расходы и посчитай точную сумму по каждой валюте. " +
                "Сам создай JavaScript-файл в scripts/ и выполни его через script_run. Сохрани результат в reports/live-totals.json " +
                "как JSON-объект, где ключи — коды валют, значения — десятичные строки сумм в минимальных единицах. " +
                "Суммы не считай в тексте. Убедись, что отчёт действительно создан. Записи приложения не изменяй.", AgentScopes(finance = true))
            println("LIVE_EVAL phase=${runner.state.value.phase} calls=${runner.state.value.modelCalls} error=${runner.state.value.error}")
            println("LIVE_EVAL tools=" + runner.state.value.document.messages.flatMap { it.calls }.joinToString { it.name })
            assertEquals(RunPhase.IDLE, runner.state.value.phase)
            val executed = runner.state.value.document.messages.filter { it.role == "tool" }.any { it.text.contains("\"log\":\"history/runs/") && it.text.contains("\"status\":\"ok\"") }
            assertTrue(executed, "A real script must execute successfully")
            val reopened = AgentWorkspace(LocalAgentDisk(root.path))
            val report = agentJson.parseToJsonElement(reopened.read("reports/live-totals.json")).jsonObject
            assertEquals("9007199254740993", report["RUB"]!!.jsonPrimitive.content)
            assertEquals("7", report["USD"]!!.jsonPrimitive.content)
        } finally { transport.close(); root.deleteRecursively() }
    }
}
