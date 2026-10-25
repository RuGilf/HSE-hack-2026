package ru.lifety.feature.assistant.agent.playground

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import ru.lifety.feature.assistant.agent.*
import kotlin.test.*

class PlaygroundTest {
    @Test fun datasetIsExplicitlySyntheticAndWritableThroughExistingRepositories() = runBlocking {
        val data = PlaygroundData(1792922400000)
        assertTrue(data.expenses.value.size >= 25)
        assertTrue(data.expenses.value.all { it.source == "synthetic-playground" && it.merchant.startsWith("[ДЕМО]") })
        val row = data.tasks.value.first()
        data.repositories.tasks.delete(row.id)
        assertFalse(data.tasks.value.any { it.id == row.id })
        data.reset()
        assertTrue(data.tasks.value.any { it.id == row.id })
    }

    @Test fun localApiRequiresSessionTokenAndRejectsForeignOriginsAndPathEscapes() = runBlocking {
        val root = Files.createTempDirectory("lifety-playground-test").toFile()
        val site = AgentPlayground.start(root, AgentCredentials(ProviderSettings("http://localhost:20128/v1", "test-model"), "test-key"), "<html>__TOKEN__</html>")
        try {
            root.resolve("workspace/reports").mkdirs()
            root.resolve("workspace/reports/large.txt").writeText("x".repeat(65000))
            val client = HttpClient.newHttpClient()
            fun request(path: String, token: Boolean = false, foreign: Boolean = false): HttpResponse<String> {
                val builder = HttpRequest.newBuilder(URI(site.url + path))
                if (token) builder.header("X-Playground-Token", site.token)
                if (foreign) builder.header("Origin", "https://unrelated.example")
                return client.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString())
            }
            assertEquals(403, request("/api/state").statusCode())
            assertEquals(403, request("/api/state", token = true, foreign = true).statusCode())
            val state = request("/api/state", token = true)
            assertEquals(200, state.statusCode())
            assertTrue(state.body().contains("synthetic-playground"))
            assertFalse(state.body().contains("test-key"))
            assertEquals(400, request("/api/file?path=..%2Fprivate%2Fcheckpoint.json", token = true).statusCode())
            val download = agentJson.parseToJsonElement(request("/api/file?path=reports%2Flarge.txt", token = true).body()).jsonObject
            assertEquals(65000, download["text"]!!.jsonPrimitive.content.length, "A download must contain the full file, not just its preview")
        } finally { site.close(); root.deleteRecursively() }
    }

    @Test fun forgettingPersonalMemoryKeepsOnlyTheIndependentSyntheticInputs() = runBlocking {
        val root = Files.createTempDirectory("lifety-playground-forget").toFile()
        val scopes = AgentScopes(finance = true, tasks = true, health = true, memory = true)
        val workspace = AgentWorkspace(LocalAgentDisk(root.resolve("workspace").path))
        workspace.write("notes/derived-private.md", "a personal analysis that must be erased")
        JsonAgentStore(LocalAgentDisk(root.resolve("private").path)).write(AgentDocument(scopes = scopes,
            facts = listOf(UserFact("f", "drink", "tea", "general", "u", "tea", 1))))
        val site = AgentPlayground.start(root, AgentCredentials(ProviderSettings("http://localhost:20128/v1", "test-model"), "test-key"), "<html>__TOKEN__</html>")
        try {
            val request = HttpRequest.newBuilder(URI(site.url + "/api/forget"))
                .header("X-Playground-Token", site.token).POST(HttpRequest.BodyPublishers.ofString("""{"id":"f"}""")).build()
            assertEquals(200, HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).statusCode())
            val paths = workspace.files().map { it.path }
            assertFalse("notes/derived-private.md" in paths)
            assertTrue("scratch/demo-health-history.csv" in paths, "The test fixture must remain usable after forgetting a fact")
            assertTrue("notes/demo-profile.md" in paths)
            assertEquals("[]", workspace.read("memory/profile.json"))
        } finally { site.close(); root.deleteRecursively() }
    }
}
