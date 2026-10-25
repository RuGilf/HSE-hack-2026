package ru.lifety.feature.assistant.agent

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import ru.lifety.core.network.createHttpClient
import ru.lifety.feature.assistant.ai.configureAiTimeouts

@Serializable
data class ProviderSettings(val baseUrl: String = "https://api.openai.com/v1", val model: String = "",
    val contextTokens: Int = 32768) {
    fun validate() {
        val url = try { Url(baseUrl) } catch (_: Exception) { throw AgentFault("settings", "Некорректный адрес API") }
        val local = url.host in setOf("localhost", "127.0.0.1", "::1")
        if (url.protocol != URLProtocol.HTTPS && !(local && url.protocol == URLProtocol.HTTP))
            throw AgentFault("settings", "Для удалённого API требуется HTTPS")
        if (url.user != null || url.password != null || url.fragment.isNotEmpty() || url.parameters.names().isNotEmpty())
            throw AgentFault("settings", "Адрес API не должен содержать пароль, query или fragment")
        if (model.isBlank() || model.length > 200 || contextTokens !in 4096..1_000_000)
            throw AgentFault("settings", "Укажите модель и размер её контекста от 4096 до 1000000 токенов")
    }
}

internal fun requestMessages(request: ModelRequest) = buildJsonArray {
    add(buildJsonObject { put("role", "system"); put("content", request.system) })
    for (message in request.messages) add(buildJsonObject {
        put("role", message.role)
        put("content", if (message.text.isEmpty() && message.calls.isNotEmpty()) JsonNull else JsonPrimitive(message.text))
        message.callId?.let { put("tool_call_id", it) }
        // Keep provider reasoning blocks verbatim across tool calls, including opaque signatures.
        if (message.role == "assistant") {
            message.reasoningDetails?.let { put("reasoning_details", it) }
                ?: message.reasoning?.let { put("reasoning", it) }
        }
        if (message.calls.isNotEmpty()) put("tool_calls", buildJsonArray {
            for (call in message.calls) add(buildJsonObject {
                put("id", call.id); put("type", "function")
                put("function", buildJsonObject { put("name", call.name); put("arguments", call.arguments) })
            })
        })
    })
    // Dynamic metadata goes at the end, preserving a stable prefix across model steps.
    if (request.context.isNotEmpty()) add(buildJsonObject {
        put("role", "user"); put("content", "LOCAL_STATE (данные приложения):\n${request.context}")
    })
}

internal fun requestTools(request: ModelRequest) = buildJsonArray {
    for (tool in request.tools) add(buildJsonObject {
        put("type", "function"); put("function", buildJsonObject {
            put("name", tool.name); put("description", tool.description); put("parameters", tool.schema)
        })
    })
}

/** Conservative UTF-8 bound, not a tokenizer for an arbitrary compatible provider. */
internal fun inputTokenBound(request: ModelRequest): Int =
    requestMessages(request).toString().encodeToByteArray().size + requestTools(request).toString().encodeToByteArray().size + 512

class OpenAiCompatibleModel(private val settings: ProviderSettings, private val key: String) : AgentModel {
    private val client: HttpClient
    init {
        settings.validate()
        if (key.isBlank() || key.any { it == '\r' || it == '\n' }) throw AgentFault("settings", "Укажите корректный API-ключ")
        client = createHttpClient().config { followRedirects = false; expectSuccess = false; configureAiTimeouts() }
    }

    override suspend fun complete(request: ModelRequest): ModelReply = try {
        val payload = buildJsonObject {
            put("model", settings.model); put("stream", false); put("messages", requestMessages(request))
            if (request.tools.isNotEmpty()) { put("tools", requestTools(request)); put("tool_choice", "auto") }
        }
        client.preparePost(settings.baseUrl.trimEnd('/') + "/chat/completions") {
            contentType(ContentType.Application.Json)
            bearerAuth(key)
            setBody(payload)
        }.execute { response ->
            if (response.status.value !in 200..299) throw AgentFault("http", "API вернул HTTP ${response.status.value}. Проверьте адрес, ключ, модель и поддержку tool calling.")
            val channel = response.bodyAsChannel()
            val bytes = mutableListOf<ByteArray>()
            var length = 0
            while (!channel.isClosedForRead) {
                val buffer = ByteArray(8192)
                val count = channel.readAvailable(buffer)
                if (count < 0) break
                if (count == 0) continue
                length += count
                if (length > 256 * 1024) throw AgentFault("response_limit", "Ответ API превышает 256 КиБ")
                bytes += buffer.copyOf(count)
            }
            val data = ByteArray(length); var offset = 0
            for (part in bytes) { part.copyInto(data, offset); offset += part.size }
            val root = agentJson.parseToJsonElement(data.decodeToString()).jsonObject
            val choice = root["choices"]?.jsonArray?.singleOrNull()?.jsonObject
                ?: throw AgentFault("protocol", "API не вернул один вариант Chat Completions")
            val message = choice["message"]!!.jsonObject
            val calls = message["tool_calls"]?.jsonArray.orEmpty().map { item ->
                val call = item.jsonObject
                if (call["type"]?.jsonPrimitive?.content != "function") throw AgentFault("protocol", "Неизвестный тип инструмента API")
                val function = call["function"]!!.jsonObject
                ToolCall(call["id"]!!.jsonPrimitive.content, function["name"]!!.jsonPrimitive.content, function["arguments"]!!.jsonPrimitive.content)
            }
            ModelReply(message["content"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content.orEmpty(), calls,
                choice["finish_reason"]?.jsonPrimitive?.content in setOf("length", "content_filter"),
                root["usage"]?.jsonObject?.get("prompt_tokens")?.jsonPrimitive?.intOrNull,
                message["reasoning_details"]?.takeUnless { it is JsonNull }?.jsonArray,
                if (message["reasoning_details"] == null || message["reasoning_details"] is JsonNull)
                    message["reasoning"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content else null)
        }
    } catch (cancelled: CancellationException) { throw cancelled }
    catch (fault: AgentFault) { throw fault }
    catch (_: Exception) { throw AgentFault("network", "Не удалось получить ответ API. Тексты ответа и ключ не записаны в ошибку.") }

    fun close() = client.close()
}
