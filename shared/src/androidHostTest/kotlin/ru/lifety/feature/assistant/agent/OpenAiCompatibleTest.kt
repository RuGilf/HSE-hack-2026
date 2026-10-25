package ru.lifety.feature.assistant.agent

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class OpenAiCompatibleTest {
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
