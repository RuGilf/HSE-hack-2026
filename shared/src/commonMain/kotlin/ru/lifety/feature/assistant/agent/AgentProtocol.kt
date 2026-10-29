package ru.lifety.feature.assistant.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

val agentJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable data class ToolCall(val id: String, val name: String, val arguments: String)
@Serializable data class AgentMessage(val id: String, val role: String, val text: String = "",
    val calls: List<ToolCall> = emptyList(), val callId: String? = null, val turn: Int = 0, val artifact: String? = null,
    val reasoningDetails: JsonArray? = null, val reasoning: String? = null)
@Serializable data class AgentScopes(val finance: Boolean = false, val tasks: Boolean = false,
    val health: Boolean = false, val memory: Boolean = false,
    val healthSleep: Boolean = true, val healthSteps: Boolean = true, val healthMetricIds: Set<String> = emptySet()) {
    fun allows(source: String) = when (source) {
        "expenses" -> finance; "tasks" -> tasks; "health" -> health; "memory" -> memory; else -> false
    }
}
@Serializable data class UserFact(val id: String, val key: String, val value: String, val domain: String,
    val sourceId: String, val sourceQuote: String, val updatedAt: Long)
@Serializable data class AgentProposal(val id: String, val kind: String, val body: JsonObject,
    val sourceId: String, val status: String = "pending", val expected: JsonElement = JsonNull)
@Serializable data class AgentDocument(val version: Int = 1, val messages: List<AgentMessage> = emptyList(),
    val facts: List<UserFact> = emptyList(), val proposals: List<AgentProposal> = emptyList(),
    val summary: String = "", val summaryThrough: Int = 0, val scopes: AgentScopes = AgentScopes())

enum class RunPhase { LOADING, IDLE, RUNNING, LIMIT, ERROR, CANCELLED }
data class AgentViewState(val document: AgentDocument = AgentDocument(), val phase: RunPhase = RunPhase.LOADING,
    val error: String? = null, val modelCalls: Int = 0, val promptTokens: Int? = null, val inputBound: Int = 0)
data class AgentLimits(val maxModelCalls: Int = 16, val maxToolCalls: Int = 48,
    val compactAfterInputBytes: Int = 12000,
    val maxToolResultChars: Int = 8000, val maxRunMillis: Long = 180000, val contextTokens: Int = 32768)
data class ModelReply(val text: String = "", val calls: List<ToolCall> = emptyList(), val truncated: Boolean = false,
    val promptTokens: Int? = null, val reasoningDetails: JsonArray? = null, val reasoning: String? = null)
data class ModelRequest(val system: String, val context: String, val messages: List<AgentMessage>,
    val tools: List<AgentTool>, val outputReserveTokens: Int = 2048, val purpose: String = "answer")
fun interface AgentModel { suspend fun complete(request: ModelRequest): ModelReply }
interface AgentStore { suspend fun read(): AgentDocument?; suspend fun write(document: AgentDocument) }
class AgentFault(val code: String, message: String) : Exception(message)

fun observation(code: String, message: String) = buildJsonObject {
    put("status", code); put("message", message)
}

data class ToolInput(val args: JsonObject, val scopes: AgentScopes, val document: AgentDocument,
    val userMessageId: String, val propose: suspend (String, JsonObject, JsonElement) -> AgentProposal)
data class AgentTool(val name: String, val description: String, val schema: JsonObject,
    val execute: suspend (ToolInput) -> JsonObject)
class AgentTools(val entries: List<AgentTool>,
    val commit: suspend (AgentProposal, AgentDocument) -> AgentDocument = { _, _ -> throw AgentFault("unavailable", "Нет обработчика действия") },
    val prepare: suspend (AgentScopes, AgentDocument) -> Unit = { _, _ -> },
    val reset: suspend (AgentScopes) -> Unit = { },
    val archive: suspend (String, String) -> String? = { _, _ -> null }) {
    init { require(entries.map { it.name }.distinct().size == entries.size) }
    fun find(name: String) = entries.find { it.name == name }
}

/** Small JSON Schema subset used by this registry. Validation also runs before every local tool. */
fun validateArguments(schema: JsonObject, value: JsonElement, path: String = "arguments") {
    val type = schema["type"]?.jsonPrimitive?.content
    val valid = when (type) {
        "object" -> value is JsonObject
        "string" -> value is JsonPrimitive && value.isString
        "integer" -> value is JsonPrimitive && !value.isString && value.longOrNull != null
        "boolean" -> value is JsonPrimitive && !value.isString && value.booleanOrNull != null
        "array" -> value is JsonArray
        else -> false
    }
    if (!valid) throw AgentFault("invalid_arguments", "$path: ожидается $type")
    schema["enum"]?.jsonArray?.let { if (value !in it) throw AgentFault("invalid_arguments", "$path: значение не разрешено") }
    if (value is JsonObject) {
        val props = schema["properties"]?.jsonObject.orEmpty()
        val required = schema["required"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        if (!value.keys.containsAll(required)) throw AgentFault("invalid_arguments", "$path: отсутствуют поля ${required - value.keys}")
        if (value.keys.any { it !in props }) throw AgentFault("invalid_arguments", "$path: неизвестные поля")
        value.forEach { (key, item) -> validateArguments(props.getValue(key).jsonObject, item, "$path.$key") }
    }
    if (type == "string") {
        val max = schema["maxLength"]?.jsonPrimitive?.intOrNull ?: 2000
        if (value.jsonPrimitive.content.length > max) throw AgentFault("invalid_arguments", "$path: слишком длинная строка")
    }
    if (type == "integer") {
        val number = value.jsonPrimitive.long
        val min = schema["minimum"]?.jsonPrimitive?.longOrNull ?: Long.MIN_VALUE
        val max = schema["maximum"]?.jsonPrimitive?.longOrNull ?: Long.MAX_VALUE
        if (number !in min..max) throw AgentFault("invalid_arguments", "$path: вне диапазона $min..$max")
    }
    if (value is JsonArray) {
        if (value.size > 100) throw AgentFault("invalid_arguments", "$path: слишком много элементов")
        val items = schema["items"]?.jsonObject ?: throw AgentFault("invalid_arguments", "$path: схема массива отсутствует")
        value.forEachIndexed { index, item -> validateArguments(items, item, "$path[$index]") }
    }
}

fun objectSchema(properties: JsonObject, required: List<String> = emptyList()) = buildJsonObject {
    put("type", "object"); put("properties", properties); put("required", JsonArray(required.map(::JsonPrimitive)))
    put("additionalProperties", false)
}
fun stringSchema(vararg choices: String, maxLength: Int = 2000) = buildJsonObject {
    put("type", "string"); put("maxLength", maxLength)
    if (choices.isNotEmpty()) put("enum", JsonArray(choices.map(::JsonPrimitive)))
}
fun integerSchema(min: Long = 0, max: Long = Long.MAX_VALUE) = buildJsonObject {
    put("type", "integer"); put("minimum", min); put("maximum", max)
}
