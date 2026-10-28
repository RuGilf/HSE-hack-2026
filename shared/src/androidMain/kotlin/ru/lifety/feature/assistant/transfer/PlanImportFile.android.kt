package ru.lifety.feature.assistant.transfer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

@Composable
internal actual fun rememberPlanImportFile(onResult: (String) -> Unit, onError: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val result by rememberUpdatedState(onResult)
    val reportError by rememberUpdatedState(onError)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        while (true) { val count = input.read(buffer); if (count < 0) break; require(output.size() + count <= 1_000_000) { "Файл больше 1 МБ" }; output.write(buffer, 0, count) }
                        output.toByteArray().decodeToString(throwOnInvalidSequence = true)
                    } ?: error("Не удалось прочитать файл")
                }
                result(text)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { reportError("Не удалось прочитать JSON. Выберите UTF-8 файл не больше 1 МБ.") }
        }
    }
    return { launcher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
}
