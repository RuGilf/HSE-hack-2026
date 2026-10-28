@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package ru.lifety.feature.assistant.transfer

import androidx.compose.runtime.*
import androidx.compose.ui.uikit.LocalUIViewController
import kotlinx.coroutines.*
import kotlinx.cinterop.*
import platform.darwin.NSObject
import platform.Foundation.*
import platform.UIKit.*
import platform.UniformTypeIdentifiers.*

@Composable
internal actual fun rememberPlanImportFile(onResult: (String) -> Unit, onError: (String) -> Unit): () -> Unit {
    val host = LocalUIViewController.current
    val scope = rememberCoroutineScope()
    val result by rememberUpdatedState(onResult)
    val reportError by rememberUpdatedState(onError)
    val delegate = remember { object : NSObject(), UIDocumentPickerDelegateProtocol {
        override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
            val url = didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
            scope.launch {
                try {
                    val text = withContext(Dispatchers.IO) {
                        val access = url.startAccessingSecurityScopedResource()
                        try {
                            val path = url.path ?: error("No file")
                            val size = NSFileManager.defaultManager.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? NSNumber
                            require((size?.longLongValue ?: 0L) <= 1_000_000)
                            val data = NSFileManager.defaultManager.contentsAtPath(path) ?: error("No data")
                            require(data.length <= 1_000_000UL)
                            if (data.length == 0UL) "" else data.bytes!!.reinterpret<ByteVar>().readBytes(data.length.toInt()).decodeToString(throwOnInvalidSequence = true)
                        } finally { if (access) url.stopAccessingSecurityScopedResource() }
                    }
                    result(text)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { reportError("Не удалось прочитать JSON. Выберите UTF-8 файл не больше 1 МБ.") }
            }
        }
    } }
    return remember(host, delegate) { {
        val presenter = planPresenter(host)
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeJSON, UTTypePlainText), asCopy = true)
        picker.delegate = delegate
        picker.allowsMultipleSelection = false
        presenter.presentViewController(picker, animated = true, completion = null)
    } }
}
