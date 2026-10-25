@file:OptIn(io.ktor.utils.io.InternalAPI::class)

package ru.lifety.feature.assistant.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/** Feature-local recording engine: exercises the real Ktor request without a new Gradle dependency. */
private class RecordingEngine(private val response: String, private val status: HttpStatusCode = HttpStatusCode.OK, private val failure: Exception? = null) : HttpClientEngineBase("lifety-fixture") {
    override val config = HttpClientEngineConfig()
    override val dispatcher = Dispatchers.Unconfined
    var request: HttpRequestData? = null
    var calls = 0
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        calls++; request = data
        failure?.let { throw it }
        val headers = Headers.build {
            append(HttpHeaders.ContentType, "application/json")
            if (status.value in 300..399) append(HttpHeaders.Location, "https://other.example/collect")
        }
        return HttpResponseData(status, GMTDate(), headers, HttpProtocolVersion.HTTP_1_1, ByteReadChannel(response), callContext())
    }
}

private fun providerTest(block: suspend CoroutineScope.() -> Unit) = runBlocking {
    withTimeout(10_000) { block() }
}

class KtorAiProviderTest {
    private val settings = AiSettings(endpoint = "https://fixture.example/v1/chat/completions", model = "fixture-model",
        apiKey = ApiKey("fixture-key"), permissions = ContextPermissions(tasks = true))
    private val context = AssistantContext(100, tasks = TasksContext(0, 0, 0, emptyList()))
    private val success = buildJsonObject {
        putJsonArray("choices") { addJsonObject { putJsonObject("message") {
            put("content", """{"summary":"Начните спокойно","recommendations":[{"title":"Маленький шаг","explanation":"Задач пока нет"}]}""")
        } } }
    }.toString()

    @Test fun postsExactPreviewAndParsesStructuredResult() = providerTest {
        val engine = RecordingEngine(success)
        val result = KtorAiProvider { HttpClient(engine) }.analyze(settings, AnalysisGoal.DAY_PLAN, context)
        assertEquals("Начните спокойно", result.summary)
        assertEquals(1, engine.calls)
        val request = assertNotNull(engine.request)
        assertEquals(HttpMethod.Post, request.method)
        assertEquals(settings.endpoint, request.url.toString())
        assertEquals("Bearer fixture-key", request.headers[HttpHeaders.Authorization])
        val body = (request.body as TextContent).text
        assertFalse("fixture-key" in body)
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("fixture-model", json.getValue("model").jsonPrimitive.content)
        assertEquals("json_object", json.getValue("response_format").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals(1800, json.getValue("max_tokens").jsonPrimitive.int)
        val sentContext = json.getValue("messages").jsonArray[1].jsonObject.getValue("content").jsonPrimitive.content
        assertEquals(context.previewJson(), sentContext)
        assertFalse("\"finances\"" in sentContext)
        assertFalse("\"health\"" in sentContext)
    }

    @Test fun blocksDeniedCategoryBeforeCreatingClient() = providerTest {
        var created = false
        val provider = KtorAiProvider { created = true; error("Must not create client") }
        val badContext = context.copy(health = HealthContext(90, 450, 100))
        try { provider.analyze(settings, AnalysisGoal.DAY_PLAN, badContext); fail("Expected privacy rejection") }
        catch (_: AiFailure) { }
        assertFalse(created)
    }

    @Test fun apiErrorsAreSanitizedAndRedirectIsNotFollowed() = providerTest {
        for (status in listOf(HttpStatusCode.Unauthorized, HttpStatusCode.TooManyRequests, HttpStatusCode.InternalServerError, HttpStatusCode.Found)) {
            val engine = RecordingEngine("PRIVATE RESPONSE fixture-key", status)
            try { KtorAiProvider { HttpClient(engine) }.analyze(settings, AnalysisGoal.DAY_PLAN, context); fail("Expected API error") }
            catch (failure: AiFailure) {
                assertFalse(failure.userMessage.contains("PRIVATE RESPONSE"))
                assertFalse(failure.userMessage.contains("fixture-key"))
            }
            assertEquals(1, engine.calls)
        }
    }

    @Test fun malformedSuccessBecomesRecoverableFailure() = providerTest {
        try { KtorAiProvider { HttpClient(RecordingEngine("{}")) }.analyze(settings, AnalysisGoal.DAY_PLAN, context); fail("Expected invalid response") }
        catch (failure: AiFailure) { assertTrue(failure.userMessage.contains("некорректный")) }
    }

    @Test fun transportFailureDoesNotExposeExceptionDetails() = providerTest {
        val engine = RecordingEngine("", failure = java.io.IOException("PRIVATE TRANSPORT fixture-key"))
        try { KtorAiProvider { HttpClient(engine) }.analyze(settings, AnalysisGoal.DAY_PLAN, context); fail("Expected network failure") }
        catch (failure: AiFailure) {
            assertTrue(failure.userMessage.contains("Не удалось связаться"))
            assertFalse("PRIVATE TRANSPORT" in failure.userMessage)
            assertFalse("fixture-key" in failure.userMessage)
        }
    }
}
