package ru.lifety.feature.assistant.agent

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class OpenAiCompatibleTest {
    @Test fun localMetadataDoesNotInterruptTheToolResultOrReplaceTheLatestUserRequest() {
        val messages = listOf(
            AgentMessage("u", "user", "Analyse my sleep"),
            AgentMessage("a", "assistant", calls = listOf(ToolCall("read-1", "files_read", """{"path":"scratch/sleep.csv"}"""))),
            AgentMessage("t", "tool", """{"status":"ok","text":"sleepMinutes\\n440"}""", callId = "read-1"),
        )
        val payload = requestMessages(ModelRequest("policy", "source index", messages, emptyList()))
        assertEquals("tool", payload.last().jsonObject["role"]!!.jsonPrimitive.content,
            "The model must continue after the tool receipt, not respond to an invented new user turn")
        assertEquals("read-1", payload.last().jsonObject["tool_call_id"]!!.jsonPrimitive.content)
        val question = requestMessages(ModelRequest("policy", "source index", messages.take(1), emptyList()))
        assertEquals("Analyse my sleep", question.last().jsonObject["content"]!!.jsonPrimitive.content)
    }

    @Test fun preservesOpaqueReasoningAcrossHttpToolCallsAndDiskCheckpoint() = runBlocking {
        val details = agentJson.parseToJsonElement("""[{"type":"reasoning.encrypted","data":"opaque-signature","format":"provider-v1","index":0},{"type":"reasoning.text","text":"synthetic reasoning","index":1}]""").jsonArray
        val received = AtomicReference<JsonObject?>(null)
        var calls = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { exchange ->
            received.set(agentJson.parseToJsonElement(exchange.requestBody.bufferedReader().readText()).jsonObject)
            val response = if (calls++ == 0) buildJsonObject {
                putJsonArray("choices") { addJsonObject {
                    put("finish_reason", "tool_calls")
                    putJsonObject("message") {
                        put("content", JsonNull)
                        put("reasoning_details", details)
                        putJsonArray("tool_calls") { addJsonObject {
                            put("id", "reasoned-call"); put("type", "function")
                            putJsonObject("function") { put("name", "missing"); put("arguments", "{}") }
                        } }
                    }
                } }
            }.toString() else """{"choices":[{"finish_reason":"stop","message":{"content":"done"}}]}"""
            val bytes = response.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val root = java.nio.file.Files.createTempDirectory("lifety-reasoning").toFile()
        val transport = OpenAiCompatibleModel(ProviderSettings("http://127.0.0.1:${server.address.port}/v1", "fixture"), "fixture-key")
        try {
            val store = JsonAgentStore(LocalAgentDisk(root.path))
            val runner = AgentRunner(transport, store, AgentTools(emptyList()), now = { 1 })
            runner.initialize(); runner.send("test", AgentScopes())
            assertEquals(RunPhase.IDLE, runner.state.value.phase)
            assertEquals(2, calls)
            val sentAssistant = received.get()!!.getValue("messages").jsonArray.map { it.jsonObject }
                .single { it["role"]?.jsonPrimitive?.content == "assistant" }
            assertEquals(details, sentAssistant["reasoning_details"])
            assertEquals(JsonNull, sentAssistant["content"])
            assertFalse("reasoning" in sentAssistant)
            assertEquals(details, store.read()!!.messages.first { it.role == "assistant" }.reasoningDetails)
            assertFalse(root.resolve("checkpoint.json").readText().contains("fixture-key"))
        } finally { transport.close(); server.stop(0); root.deleteRecursively() }
    }

    @Test fun preservesPlainReasoningAndReadsCheckpointsFromBeforeTheExtension() {
        val legacy = agentJson.decodeFromString<AgentDocument>("""{"messages":[{"id":"old","role":"assistant","text":"hello"}]}""")
        assertNull(legacy.messages.single().reasoningDetails)
        val message = AgentMessage("a", "assistant", "answer", reasoning = "synthetic provider metadata")
        val request = ModelRequest("policy", "", listOf(message), emptyList())
        val sent = requestMessages(request).last().jsonObject
        assertEquals("synthetic provider metadata", sent["reasoning"]?.jsonPrimitive?.content)
        assertFalse("reasoning_details" in sent)
        assertTrue(inputTokenBound(request) > inputTokenBound(request.copy(messages = listOf(message.copy(reasoning = null)))))
    }

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
