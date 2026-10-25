package ru.lifety.feature.assistant.agent

import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

/** Opt-in outcome eval. Credentials exist only in the test process environment. */
class LiveAgentEvalTest {
    @Test fun realProviderWorksThroughTheSameConnectAndSendPathAsTheScreen() = runBlocking {
        val key = System.getenv("LIFETY_LLM_API_KEY")
        val endpoint = System.getenv("LIFETY_LLM_BASE_URL")
        val model = System.getenv("LIFETY_LLM_MODEL")
        org.junit.Assume.assumeTrue("Set LIFETY_LLM_* to enable live eval", !key.isNullOrBlank() && !endpoint.isNullOrBlank() && !model.isNullOrBlank())
        val root = Files.createTempDirectory("lifety-live-chat-connect").toFile()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Unconfined)
        val credentials = AgentCredentials(ProviderSettings(endpoint!!, model!!, 65536), key!!)
        val runtime = AgentRuntime(AgentWorkspace(LocalAgentDisk(root.resolve("workspace").path)), JsonAgentStore(LocalAgentDisk(root.resolve("private").path)),
            object : AgentVault {
                override val description = "live fixture memory only"
                override suspend fun load() = credentials
                override suspend fun save(credentials: AgentCredentials) = Unit
            }, System::currentTimeMillis, kotlinx.coroutines.sync.Mutex())
        val controller = AgentChatController(runtime, null, scope)
        try {
            controller.connect(credentials).join()
            assertTrue(controller.state.value.connected, controller.state.value.error)
            controller.send("Это изолированная проверка соединения без личных данных. Не используй инструменты. Ответь только фразой: Соединение работает.")!!.join()
            assertEquals(RunPhase.IDLE, controller.state.value.view.phase, controller.state.value.view.error)
            assertTrue(controller.state.value.view.document.messages.last().text.contains("Соединение работает", ignoreCase = true))
            println("LIVE_CHAT connect=passed send=passed model=$model")
        } finally { controller.close(); scope.coroutineContext[kotlinx.coroutines.Job]?.cancel(); root.deleteRecursively() }
    }

    @Test fun realModelRemembersAndCorrectsAPreferenceAcrossSessions() = runBlocking {
        val key = System.getenv("LIFETY_LLM_API_KEY")
        val endpoint = System.getenv("LIFETY_LLM_BASE_URL")
        val model = System.getenv("LIFETY_LLM_MODEL")
        org.junit.Assume.assumeTrue("Set LIFETY_LLM_* to enable live eval", !key.isNullOrBlank() && !endpoint.isNullOrBlank() && !model.isNullOrBlank())
        val root = Files.createTempDirectory("lifety-agent-live-memory").toFile()
        val transport = OpenAiCompatibleModel(ProviderSettings(endpoint!!, model!!), key!!)
        try {
            val workspace = AgentWorkspace(LocalAgentDisk(root.resolve("workspace").path))
            val store = JsonAgentStore(LocalAgentDisk(root.resolve("private").path))
            val env = LifetyAgentEnvironment(null, workspace, { 1000 })
            val first = AgentRunner(transport, store, env.registry, env::snapshot, { 1000 })
            first.initialize()
            first.send("Я предпочитаю чай. Запомни это как моё предпочтение для будущих разговоров.", AgentScopes(memory = true))
            assertEquals(RunPhase.IDLE, first.state.value.phase)
            val initial = first.state.value.document.proposals.last { it.kind == "memory_set" && it.status == "pending" }
            assertTrue(first.state.value.document.facts.isEmpty())
            first.approve(initial.id)
            assertEquals(1, first.state.value.document.facts.size)
            first.newChat()
            val reopened = AgentRunner(transport, store, env.registry, env::snapshot, { 2000 })
            reopened.initialize()
            reopened.send("Какой напиток я предпочитаю?", AgentScopes(memory = true))
            assertEquals(RunPhase.IDLE, reopened.state.value.phase)
            val answer = reopened.state.value.document.messages.last { it.role == "assistant" }.text.lowercase()
            assertTrue(listOf("чай", "чая", "tea").any { it in answer })
            reopened.send("Теперь я предпочитаю кофе. Исправь именно существующее предпочтение напитка, сохранив его ключ и домен.", AgentScopes(memory = true))
            assertEquals(RunPhase.IDLE, reopened.state.value.phase)
            val correction = reopened.state.value.document.proposals.last { it.kind == "memory_set" && it.status == "pending" }
            reopened.approve(correction.id)
            val fact = reopened.state.value.document.facts.single()
            assertTrue(listOf("кофе", "coffee").any { it in fact.value.lowercase() })
            println("LIVE_MEMORY recall=passed correction=passed facts=${reopened.state.value.document.facts.size}")
        } finally { transport.close(); root.deleteRecursively() }
    }

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
                java.io.File("build/agent-live-last-request.json").apply { requireNotNull(parentFile).mkdirs() }.writeText(buildJsonObject {
                    put("model", model); put("messages", requestMessages(request)); put("tools", requestTools(request))
                }.toString())
                transport.complete(request)
            }
            val runner = AgentRunner(observed, MemoryAgentStore(), env.registry, env::snapshot, { 1000 })
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
