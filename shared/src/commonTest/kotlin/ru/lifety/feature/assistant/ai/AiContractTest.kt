package ru.lifety.feature.assistant.ai

import kotlin.test.*
import kotlinx.serialization.json.*

class AiContractTest {
    @Test fun disabledCategoriesAreAbsentFromPayload() {
        val json = contextJson.parseToJsonElement(AssistantContext(100).previewJson()).jsonObject
        assertFalse("tasks" in json)
        assertFalse("finances" in json)
        assertFalse("health" in json)
        assertFalse(ContextPermissions().any)
    }

    @Test fun requiresHttpsWithoutEmbeddedSecrets() {
        assertTrue(endpointIsValid("https://provider.example/v1/chat/completions"))
        listOf("http://provider.example/v1", "https://user:pass@provider.example/v1", "https://provider.example/v1?key=secret", "https://provider.example/#fragment", "garbage")
            .forEach { assertFalse(endpointIsValid(it), it) }
        assertFalse(AiSettings().toString().contains("secret-value"))
        assertFalse(AiSettings(apiKey = ApiKey("secret-value")).toString().contains("secret-value"))
    }

    private fun envelope(content: String): String = buildJsonObject {
        putJsonArray("choices") { addJsonObject { putJsonObject("message") { put("content", content) } } }
    }.toString()

    @Test fun parsesStructuredRecommendationsWithExplanation() {
        val result = parseAnalysis(envelope("""{"summary":"Можно двигаться спокойно","recommendations":[{"title":"Начните с малого","explanation":"Данных пока немного"}]}"""), AssistantContext(100))
        assertEquals("Начните с малого", result.recommendations.single().title)
        assertNull(result.recommendations.single().taskRef)
    }

    @Test fun rejectsMalformedAndInventedTaskReferences() {
        listOf("not json", "{}", envelope("{}"),
            envelope("""{"summary":"ok","recommendations":[]}"""),
            envelope("""{"summary":"ok","recommendations":[{"title":"x","explanation":"why","taskRef":"invented"}]}"""),
            envelope("""{"summary":"ok","recommendations":[{"title":"x","explanation":""}]}"""))
            .forEach { assertFails { parseAnalysis(it, AssistantContext(100)) } }
    }

    @Test fun totalsRejectOverflow() {
        assertEquals(9_007_199_254_740_993, checkedTotal(listOf(9_007_199_254_740_992, 1)))
        assertFailsWith<IllegalArgumentException> { checkedTotal(listOf(Long.MAX_VALUE, 1)) }
    }
}
