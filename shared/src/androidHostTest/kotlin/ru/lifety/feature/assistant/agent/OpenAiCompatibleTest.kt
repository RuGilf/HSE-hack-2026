package ru.lifety.feature.assistant.agent

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class OpenAiCompatibleTest {
    @Test fun runtimeLeavesGenerationLimitsToTheProvider() = runBlocking {
        val captured = AtomicReference<JsonObject?>(null)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            val payload = agentJson.parseToJsonElement(exchange.requestBody.bufferedReader().readText()).jsonObject
            captured.set(payload)
            val response = """{"choices":[{"finish_reason":"stop","message":{"content":"hello"}}]}""".toByteArray()
            exchange.sendResponseHeaders(200, response.size.toLong()); exchange.responseBody.use { it.write(response) }
        }
        server.start()
        val root = java.nio.file.Files.createTempDirectory("lifety-budget").toFile()
        try {
            val settings = ProviderSettings("http://127.0.0.1:${server.address.port}/v1", "test-model")
            val credentials = AgentCredentials(settings, "test-key")
            val vault = object : AgentVault {
                override val description = "test"
                override suspend fun load() = credentials
                override suspend fun save(credentials: AgentCredentials) = Unit
            }
            val runtime = AgentRuntime(AgentWorkspace(LocalAgentDisk(root.resolve("workspace").path)),
                JsonAgentStore(LocalAgentDisk(root.resolve("private").path)), vault, { 1 }, kotlinx.coroutines.sync.Mutex())
            val session = runtime.open()
            try {
                session.start("hello").join()
                assertEquals(RunPhase.IDLE, session.runner.state.value.phase)
                val payload = assertNotNull(captured.get())
                assertFalse("max_tokens" in payload)
                assertFalse("max_completion_tokens" in payload)
            }
            finally { session.close() }
        } finally { server.stop(0); root.deleteRecursively() }
    }

    @Test fun roundTripsFunctionCallsWithRealHttpAndDoesNotFollowCredentialRedirects() = runBlocking {
        val captured = AtomicReference("")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            captured.set(exchange.requestBody.bufferedReader().readText())
            val response = """{"choices":[{"finish_reason":"tool_calls","message":{"content":null,"tool_calls":[{"id":"tool-1","type":"function","function":{"name":"data_describe","arguments":"{}"}}]}}]}""".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong()); exchange.responseBody.use { it.write(response) }
        }
        server.createContext("/redirect/chat/completions") { exchange ->
            exchange.responseHeaders.add("Location", "http://127.0.0.1:${server.address.port}/leak")
            exchange.sendResponseHeaders(307, -1); exchange.close()
        }
        var leaked = false
        server.createContext("/leak") { exchange -> leaked = true; exchange.sendResponseHeaders(500, -1); exchange.close() }
        server.start()
        try {
            val transport = OpenAiCompatibleModel(ProviderSettings("http://127.0.0.1:${server.address.port}/v1", "test-model"), "test-secret")
            try {
                val reply = transport.complete(ModelRequest("policy", "data", listOf(AgentMessage("u", "user", "hello")), emptyList()))
                assertEquals("tool-1", reply.calls.single().id)
                assertEquals("test-model", agentJson.parseToJsonElement(captured.get()).jsonObject["model"]!!.jsonPrimitive.content)
                assertFalse(captured.get().contains("test-secret"))
            } finally { transport.close() }
            val redirect = OpenAiCompatibleModel(ProviderSettings("http://127.0.0.1:${server.address.port}/redirect", "test-model"), "test-secret")
            try {
                assertFailsWith<AgentFault> { redirect.complete(ModelRequest("", "", emptyList(), emptyList())) }
                assertFalse(leaked)
            } finally { redirect.close() }
        } finally { server.stop(0) }
    }

    @Test fun rejectsUnencryptedRemoteEndpoints() {
        assertFailsWith<AgentFault> { ProviderSettings("http://example.com/v1", "model").validate() }
        assertFailsWith<AgentFault> { ProviderSettings("https://user:password@example.com/v1", "model").validate() }
    }
}
