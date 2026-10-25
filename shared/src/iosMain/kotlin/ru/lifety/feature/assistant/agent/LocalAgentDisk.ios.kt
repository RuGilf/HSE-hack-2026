@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
package ru.lifety.feature.assistant.agent

import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.*

internal actual class LocalAgentDisk actual constructor(private val root: String) : AgentDisk {
    private val manager = NSFileManager.defaultManager
    init { if (!manager.createDirectoryAtPath(root, withIntermediateDirectories = true, attributes = null, error = null)) throw AgentFault("storage", "Не удалось создать папку") }
    private fun target(path: String): String {
        checkedPath(path)
        var current = root
        for (part in path.split('/')) {
            current += "/$part"
            if (manager.attributesOfItemAtPath(current, error = null)?.get(NSFileType) == NSFileTypeSymbolicLink)
                throw AgentFault("invalid_path", "Символические ссылки запрещены")
        }
        return current
    }
    actual override suspend fun paths(): List<String> = withContext(Dispatchers.Default) {
        val result = mutableListOf<String>()
        val enumerator = manager.enumeratorAtPath(root) ?: throw AgentFault("storage", "Не удалось прочитать папку")
        while (true) {
            val path = enumerator.nextObject() as? String ?: break
            val full = target(path)
            if (manager.attributesOfItemAtPath(full, error = null)?.get(NSFileType) == NSFileTypeRegular) result += path
            if (result.size > 2048) throw AgentFault("disk_limit", "Слишком много файлов")
        }
        result
    }
    actual override suspend fun read(path: String): String? = withContext(Dispatchers.Default) {
        val full = target(path)
        if (!manager.fileExistsAtPath(full)) return@withContext null
        val data = manager.contentsAtPath(full) ?: throw AgentFault("storage", "Не удалось прочитать файл")
        if (data.length > (4 * 1024 * 1024).toULong()) throw AgentFault("file_limit", "Слишком большой файл")
        if (data.length == 0UL) "" else data.bytes!!.reinterpret<ByteVar>().readBytes(data.length.toInt()).decodeToString()
    }
    actual override suspend fun write(path: String, text: String) = withContext(Dispatchers.Default) {
        val full = target(path)
        if (!manager.createDirectoryAtPath(full.substringBeforeLast('/'), withIntermediateDirectories = true, attributes = null, error = null)) throw AgentFault("storage", "Не удалось создать каталог")
        val bytes = text.encodeToByteArray()
        val data = if (bytes.isEmpty()) NSData() else bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        if (!data.writeToFile(full, atomically = true)) throw AgentFault("storage", "Не удалось сохранить файл")
        Unit
    }
    actual override suspend fun delete(path: String) = withContext(Dispatchers.Default) {
        val full = target(path)
        if (manager.fileExistsAtPath(full) && !manager.removeItemAtPath(full, error = null)) throw AgentFault("storage", "Не удалось удалить файл")
        Unit
    }
}
