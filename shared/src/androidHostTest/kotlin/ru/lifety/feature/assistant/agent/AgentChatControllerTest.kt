package ru.lifety.feature.assistant.agent

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlin.test.*

class AgentChatControllerTest {
    @Test fun openingAndConfiguringDoNotSendAndOnlyAnExplicitMessageCallsTheApi() = runBlocking {
        val calls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            calls.incrementAndGet(); exchange.requestBody.readAllBytes()
            val response = """{"choices":[{"finish_reason":"stop","message":{"content":"Готово"}}]}""".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong()); exchange.responseBody.use { it.write(response) }
        }
        server.start()
        val root = Files.createTempDirectory("lifety-chat-ui").toFile()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val credentials = AgentCredentials(ProviderSettings("http://127.0.0.1:${server.address.port}/v1", "fixture"), "fixture-key")
        var saved = 0
        val runtime = AgentRuntime(AgentWorkspace(LocalAgentDisk(root.resolve("workspace").path)),
            JsonAgentStore(LocalAgentDisk(root.resolve("private").path)), object : AgentVault {
                override val description = "test"
                override suspend fun load() = credentials
                override suspend fun save(credentials: AgentCredentials) { saved++ }
            }, { 1 }, Mutex())
        val controller = AgentChatController(runtime, null, scope)
        try {
            controller.initialize().join()
            assertTrue(controller.state.value.connected)
            assertEquals(AgentScopes(), controller.state.value.view.document.scopes)
            assertEquals(0, calls.get()); assertEquals(0, saved)
            controller.connect(credentials).join()
            assertEquals(0, calls.get()); assertEquals(1, saved)
            controller.send("Привет")!!.join()
            assertEquals(1, calls.get())
            assertEquals("Готово", controller.state.value.view.document.messages.last().text)
            assertFalse(controller.state.value.busy)
            controller.newChat().join()
            assertTrue(controller.state.value.view.document.messages.isEmpty())
            assertEquals(1, calls.get())
        } finally { controller.close(); scope.cancel(); server.stop(0); root.deleteRecursively() }
    }
}
