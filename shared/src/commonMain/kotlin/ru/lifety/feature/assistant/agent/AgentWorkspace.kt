package ru.lifety.feature.assistant.agent

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsInterruptedException
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.binding.asyncFunction
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.random.Random

@Serializable data class WorkspaceFile(val path: String, val bytes: Int, val revision: String)
interface AgentDisk {
    suspend fun paths(): List<String>
    suspend fun read(path: String): String?
    suspend fun write(path: String, text: String)
    suspend fun delete(path: String)
}
internal expect class LocalAgentDisk(root: String) : AgentDisk {
    override suspend fun paths(): List<String>
    override suspend fun read(path: String): String?
    override suspend fun write(path: String, text: String)
    override suspend fun delete(path: String)
}

internal fun checkedPath(path: String): String {
    if (path.isEmpty() || path.length > 240 || path.startsWith('/') || path.contains('\\') || path.contains(':') ||
        path.split('/').any { it.isBlank() || it == "." || it == ".." || it.startsWith('.') } || path.any { it.code < 32 })
        throw AgentFault("invalid_path", "Нужен относительный путь без .., скрытых файлов и ссылок, например scripts/analysis.js")
    return path
}

internal fun revision(text: String): String {
    var hash = -3750763034362895579L
    for (byte in text.encodeToByteArray()) hash = (hash xor (byte.toLong() and 255)) * 1099511628211L
    return hash.toULong().toString(16)
}

class AgentWorkspace(private val disk: AgentDisk, private val scriptTimeoutMillis: Long = 2000) {
    private val lock = Mutex()
    private val writable = setOf("scripts", "scratch", "reports", "notes")
    suspend fun files(prefix: String = ""): List<WorkspaceFile> = lock.withLock {
        val directory = if (prefix.length > 1) prefix.removeSuffix("/") else prefix
        if (directory.isNotEmpty()) checkedPath(directory)
        disk.paths().filter { directory.isEmpty() || it == directory || it.startsWith("$directory/") }.sorted().map { path ->
            val text = disk.read(path) ?: ""
            WorkspaceFile(path, text.encodeToByteArray().size, revision(text))
        }
    }
    suspend fun read(path: String): String = lock.withLock {
        disk.read(checkedPath(path)) ?: throw AgentFault("not_found", "$path отсутствует. Используйте files_list для поиска.")
    }
    suspend fun write(path: String, text: String, expectedRevision: String = "") = lock.withLock {
        checkedPath(path)
        if (path.substringBefore('/') !in writable || '/' !in path) throw AgentFault("read_only", "Запись разрешена в scripts/, scratch/, reports/ и notes/.")
        val old = disk.read(path)
        if (old != null && expectedRevision != revision(old)) throw AgentFault("conflict", "Файл существует или изменён. Прочитайте его revision через files_list и повторите запись с expectedRevision.")
        if (old == null && expectedRevision.isNotEmpty()) throw AgentFault("conflict", "Файл удалён; повторно проверьте каталог.")
        writeBounded(path, text)
    }
    suspend fun generated(path: String, text: String) = lock.withLock { checkedPath(path); writeBounded(path, text) }
    private suspend fun writeBounded(path: String, text: String) {
        val size = text.encodeToByteArray().size
        if (size > 1024 * 1024) throw AgentFault("file_limit", "Файл превышает 1 МиБ; разбейте данные на части.")
        val paths = disk.paths()
        if (path !in paths && paths.size >= 1024) throw AgentFault("disk_limit", "Достигнут лимит 1024 файлов; удалите ненужные scratch-файлы.")
        val total = paths.filter { it != path }.sumOf { (disk.read(it) ?: "").encodeToByteArray().size.toLong() }
        if (total + size > 20L * 1024 * 1024) throw AgentFault("disk_limit", "Рабочая папка превышает 20 МиБ; удалите ненужные файлы.")
        disk.write(path, text)
    }
    suspend fun delete(path: String, expectedRevision: String) = lock.withLock {
        checkedPath(path)
        if (path.substringBefore('/') !in writable) throw AgentFault("read_only", "Источник нельзя удалить через файловый инструмент.")
        val old = disk.read(path) ?: throw AgentFault("not_found", "Файл уже отсутствует")
        if (revision(old) != expectedRevision) throw AgentFault("conflict", "Файл изменён; сначала обновите revision.")
        disk.delete(path)
    }
    suspend fun clear() = lock.withLock { disk.paths().forEach { disk.delete(it) } }
    suspend fun removeGenerated(prefix: String) = lock.withLock { disk.paths().filter { it.startsWith(prefix) }.forEach { disk.delete(it) } }

    suspend fun runScript(path: String, content: String? = null, expectedRevision: String = ""): JsonObject {
        if (!path.startsWith("scripts/") || !path.endsWith(".js")) throw AgentFault("invalid_path", "Сохраните JavaScript в scripts/*.js перед запуском.")
        if (content != null) write(path, content, expectedRevision)
        val code = read(path)
        if (code.length > 64000) throw AgentFault("file_limit", "Скрипт превышает 64000 символов")
        val output = StringBuilder()
        var operations = 0
        fun operation() { if (++operations > 200) throw AgentFault("operation_limit", "Более 200 файловых операций за запуск; разбейте скрипт.") }
        var status = "ok"
        var error = ""
        val runtime = QuickJs.create(Dispatchers.Default)
        try {
            runtime.memoryLimit = 32L * 1024 * 1024
            runtime.maxStackSize = 256L * 1024
            runtime.evaluationTimeoutMillis = scriptTimeoutMillis
            runtime.define("console") {
                function("log") { args ->
                    val line = args.joinToString(" ")
                    if (output.length + line.length > 16000) throw AgentFault("output_limit", "Вывод превышает 16000 символов; сохраните большие результаты в файл.")
                    output.appendLine(line); Unit
                }
            }
            runtime.define("fs") {
                asyncFunction("readText") { args -> operation(); read(args.single() as String) }
                asyncFunction("writeText") { args -> operation(); write(args[0] as String, args[1] as String, args.getOrNull(2) as? String ?: ""); Unit }
                asyncFunction("list") { args -> operation(); agentJson.encodeToString(files(args.firstOrNull() as? String ?: "")) }
            }
            withTimeout(scriptTimeoutMillis + 3000) { runtime.evaluate<Any?>(code, filename = path, asModule = true) }
        } catch (_: QuickJsInterruptedException) { status = "timeout"; error = "Скрипт остановлен по времени. Уменьшите объём работы." }
        catch (timeout: TimeoutCancellationException) { currentCoroutineContext().ensureActive(); status = "timeout"; error = "Превышено время ожидания файловых операций." }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { status = "script_error"; error = failure.message.orEmpty().take(2000) }
        finally { runtime.close() }
        val log = "history/runs/${Random.nextLong().toULong()}.json"
        val result = buildJsonObject { put("status", status); put("stdout", output.toString()); put("error", error); put("script", path) }
        generated(log, result.toString())
        return buildJsonObject {
            put("status", status); put("stdout", output.toString().take(4000)); put("error", error); put("log", log)
            put("outputTruncated", output.length > 4000)
        }
    }
}

class JsonAgentStore(private val disk: AgentDisk) : AgentStore {
    override suspend fun read(): AgentDocument? = disk.read("checkpoint.json")?.let { agentJson.decodeFromString<AgentDocument>(it) }
    override suspend fun write(document: AgentDocument) {
        val text = agentJson.encodeToString(document)
        if (text.encodeToByteArray().size > 4 * 1024 * 1024) throw AgentFault("history_limit", "История достигла 4 МиБ. Сохраните отчёт и начните новый чат.")
        disk.write("checkpoint.json", text)
    }
}
