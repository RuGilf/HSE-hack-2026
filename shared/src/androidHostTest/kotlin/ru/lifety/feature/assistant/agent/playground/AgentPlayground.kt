package ru.lifety.feature.assistant.agent.playground

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import ru.lifety.feature.assistant.agent.*

/** Browser test fixture over the actual Kotlin harness. Binds loopback only. */
class AgentPlayground private constructor(private val root: File, private val credentials: AgentCredentials, private val html: String, port: Int) : AutoCloseable {
    val token = ByteArray(24).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", port), 0)
    private val executor = Executors.newFixedThreadPool(4)
    private val closed = AtomicBoolean(false)
    private val stopped = CountDownLatch(1)
    private val actions = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val workspace = AgentWorkspace(LocalAgentDisk(File(root, "workspace").path))
    private val store = JsonAgentStore(LocalAgentDisk(File(root, "private").path))
    private val data = PlaygroundData()
    private val runtime = AgentRuntime(workspace, store, object : AgentVault {
        override val description = "Credentials only in this process environment"
        override suspend fun load() = credentials
        override suspend fun save(credentials: AgentCredentials) = error("Use launcher environment")
    }, System::currentTimeMillis, Mutex())
    private lateinit var session: AgentSession
    private var active: Job? = null
    val url get() = "http://127.0.0.1:${server.address.port}"

    private suspend fun open() {
        session = runtime.open(data.repositories)
        session.runner.changeScopes(AgentScopes(finance = true, tasks = true, health = true, memory = true))
        seedSyntheticInputs()
    }

    private suspend fun seedSyntheticInputs() {
        if (workspace.files("notes").none { it.path == "notes/demo-profile.md" }) workspace.write("notes/demo-profile.md", data.guide)
        if (workspace.files("scratch").none { it.path == "scratch/demo-health-history.csv" }) workspace.write("scratch/demo-health-history.csv", data.healthCsv)
    }

    private fun state() = buildJsonObject {
        val state = session.runner.state.value
        put("synthetic", true); put("model", credentials.settings.model); put("phase", state.phase.name)
        put("busy", active?.isActive == true); put("error", state.error?.let(::JsonPrimitive) ?: JsonNull)
        put("modelCalls", state.modelCalls); put("promptTokens", state.promptTokens?.let(::JsonPrimitive) ?: JsonNull)
        put("document", agentJson.encodeToJsonElement(state.document))
        put("expenses", JsonArray(data.expenses.value.map(LifetyAgentEnvironment::expenseJson)))
        put("tasks", agentJson.encodeToJsonElement(data.tasks.value)); put("health", agentJson.encodeToJsonElement(data.health.value))
    }

    private fun HttpExchange.send(status: Int, text: String, contentType: String = "application/json; charset=utf-8") {
        responseHeaders.set("Content-Type", contentType); responseHeaders.set("Cache-Control", "no-store")
        responseHeaders.set("X-Content-Type-Options", "nosniff")
        val bytes = text.toByteArray(Charsets.UTF_8)
        sendResponseHeaders(status, bytes.size.toLong()); responseBody.use { it.write(bytes) }
    }
    private fun HttpExchange.json(status: Int, value: JsonElement) = send(status, value.toString())
    private fun HttpExchange.body(): JsonObject {
        val bytes = requestBody.readNBytes(32769)
        if (bytes.size > 32768) throw AgentFault("size", "Слишком большой запрос")
        return try { agentJson.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject }
        catch (_: Exception) { throw AgentFault("json", "Нужен JSON-объект") }
    }
    private fun HttpExchange.authorized(): Boolean {
        val host = requestHeaders.getFirst("Host")
        if (host != "127.0.0.1:${server.address.port}") return false
        val origin = requestHeaders.getFirst("Origin")
        return (origin == null || origin == url) && requestHeaders.getFirst("X-Playground-Token") == token
    }

    private fun handle(exchange: HttpExchange) {
        try {
            if (exchange.requestHeaders.getFirst("Host") != "127.0.0.1:${server.address.port}") { exchange.send(403, "{}"); return }
            val path = exchange.requestURI.path
            if (path == "/" && exchange.requestMethod == "GET") {
                exchange.responseHeaders.set("Content-Security-Policy", "default-src 'self'; script-src 'nonce-$token'; style-src 'nonce-$token'; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")
                exchange.send(200, html.replace("__TOKEN__", token), "text/html; charset=utf-8"); return
            }
            if (path == "/health" && exchange.requestMethod == "GET") { exchange.json(200, buildJsonObject { put("ready", true); put("synthetic", true) }); return }
            if (!exchange.authorized()) { exchange.send(403, "{}"); return }
            runBlocking {
                if (exchange.requestMethod == "GET") when (path) {
                    "/api/state" -> exchange.json(200, state())
                    "/api/files" -> exchange.json(200, agentJson.encodeToJsonElement(workspace.files()))
                    "/api/file" -> {
                        val raw = exchange.requestURI.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("path=") }?.substringAfter('=') ?: ""
                        val name = URLDecoder.decode(raw, "UTF-8"); val text = workspace.read(name)
                        exchange.json(200, buildJsonObject { put("path", name); put("text", text); put("length", text.length) })
                    }
                    else -> exchange.send(404, "{}")
                } else if (exchange.requestMethod == "POST") actions.withLock {
                    val body = exchange.body()
                    when (path) {
                        "/api/send" -> {
                            if (active?.isActive == true) { exchange.json(409, observation("busy", "Агент уже работает")); return@withLock }
                            val text = body["message"]?.jsonPrimitive?.content.orEmpty()
                            if (text.isBlank() || text.length > 8000) throw AgentFault("message", "Сообщение: 1–8000 символов")
                            active = session.start(text)
                        }
                        "/api/cancel" -> { session.cancel(); active?.join() }
                        "/api/approve", "/api/reject", "/api/forget" -> {
                            if (active?.isActive == true) { exchange.json(409, observation("busy", "Сначала остановите запуск")); return@withLock }
                            val id = body["id"]?.jsonPrimitive?.content ?: throw AgentFault("id", "Не указан ID")
                            when (path) {
                                "/api/approve" -> session.runner.approve(id)
                                "/api/reject" -> session.runner.reject(id)
                                else -> { session.runner.forget(id); seedSyntheticInputs() }
                            }
                        }
                        "/api/new-chat" -> { session.cancel(); active?.join(); session.runner.newChat() }
                        "/api/reset" -> {
                            session.cancel(); active?.join(); session.close(); data.reset()
                            workspace.clear(); store.write(AgentDocument()); open()
                        }
                        "/api/shutdown" -> scope.launch { delay(200); close() }
                        else -> { exchange.send(404, "{}"); return@withLock }
                    }
                    exchange.json(200, observation("ok", "Готово"))
                } else exchange.send(405, "{}")
            }
        } catch (fault: AgentFault) { exchange.json(400, observation(fault.code, fault.message.orEmpty())) }
        catch (_: Exception) { runCatching { exchange.json(500, observation("server_error", "Операция не завершена. Проверьте состояние агента.")) } }
        finally { exchange.close() }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runBlocking { if (::session.isInitialized) { session.cancel(); active?.join(); session.close() } }
        server.stop(0); executor.shutdownNow(); scope.cancel(); stopped.countDown()
    }
    fun awaitShutdown() = stopped.await()

    companion object {
        suspend fun start(root: File, credentials: AgentCredentials, html: String, port: Int = 0): AgentPlayground {
            val site = AgentPlayground(root, credentials, html, port)
            try { site.open(); site.server.executor = site.executor; site.server.createContext("/", site::handle); site.server.start(); return site }
            catch (failure: Exception) { site.close(); throw failure }
        }
    }
}

fun main(args: Array<String>) = runBlocking {
    val key = System.getenv("LIFETY_LLM_API_KEY") ?: error("Set LIFETY_LLM_API_KEY in the process environment")
    val settings = ProviderSettings(System.getenv("LIFETY_LLM_BASE_URL") ?: "http://localhost:20128/v1",
        System.getenv("LIFETY_LLM_MODEL") ?: "antigravity/gemini-3.8-flash-high", contextTokens = 65536)
    val directory = File(args[0]); val html = File(args[1]).readText(Charsets.UTF_8); val info = File(args[2])
    val site = AgentPlayground.start(directory, AgentCredentials(settings, key), html, args.getOrNull(3)?.toInt() ?: 0)
    Runtime.getRuntime().addShutdownHook(Thread { site.close() })
    info.writeText(buildJsonObject { put("url", site.url); put("token", site.token); put("pid", ProcessHandle.current().pid()) }.toString())
    println("Lifety synthetic agent playground: ${site.url}")
    site.awaitShutdown()
}
