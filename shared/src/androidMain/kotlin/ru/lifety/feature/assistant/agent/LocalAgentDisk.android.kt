package ru.lifety.feature.assistant.agent

import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal actual class LocalAgentDisk actual constructor(root: String) : AgentDisk {
    private val base = File(root).canonicalFile
    init { if (!base.exists() && !base.mkdirs()) throw AgentFault("storage", "Не удалось создать рабочую папку") }
    private fun target(path: String): File {
        checkedPath(path)
        var node = base
        for (part in path.split('/')) {
            node = File(node, part)
            if (node.canonicalFile != node.absoluteFile) throw AgentFault("invalid_path", "Символические ссылки запрещены")
        }
        return node
    }
    actual override suspend fun paths(): List<String> = withContext(Dispatchers.IO) {
        val result = mutableListOf<String>()
        fun walk(dir: File) {
            for (file in dir.listFiles() ?: throw AgentFault("storage", "Не удалось прочитать каталог")) {
                if (file.name.startsWith('.')) continue
                val path = file.relativeTo(base).invariantSeparatorsPath
                target(path)
                if (file.isDirectory) walk(file) else result += path
                if (result.size > 2048) throw AgentFault("disk_limit", "Слишком много файлов в рабочей папке")
            }
        }
        walk(base); result
    }
    actual override suspend fun read(path: String): String? = withContext(Dispatchers.IO) {
        val file = target(path)
        val backup = File(file.parentFile, ".${file.name}.bak")
        if (backup.exists()) { if (file.exists() && !file.delete()) throw AgentFault("storage", "Ошибка восстановления файла"); if (!backup.renameTo(file)) throw AgentFault("storage", "Ошибка восстановления файла") }
        if (!file.exists()) null else {
            if (!file.isFile || file.length() > 4 * 1024 * 1024) throw AgentFault("file_limit", "Файл недоступен или слишком велик")
            file.readText(Charsets.UTF_8)
        }
    }
    actual override suspend fun write(path: String, text: String) = withContext(Dispatchers.IO) {
        val file = target(path)
        val parent = requireNotNull(file.parentFile)
        if (!parent.exists() && !parent.mkdirs()) throw AgentFault("storage", "Не удалось создать каталог")
        val temp = File(file.parentFile, ".${file.name}.tmp")
        val backup = File(file.parentFile, ".${file.name}.bak")
        if (backup.exists()) read(path)
        FileOutputStream(temp).use { stream -> stream.write(text.toByteArray(Charsets.UTF_8)); stream.fd.sync() }
        if (file.exists() && !file.renameTo(backup)) throw AgentFault("storage", "Не удалось сохранить предыдущую версию")
        if (!temp.renameTo(file)) { backup.renameTo(file); throw AgentFault("storage", "Не удалось сохранить файл") }
        if (backup.exists() && !backup.delete()) throw AgentFault("storage", "Не удалось завершить запись")
        Unit
    }
    actual override suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        val file = target(path)
        if (file.exists() && (!file.isFile || !file.delete())) throw AgentFault("storage", "Не удалось удалить файл")
        Unit
    }
}
