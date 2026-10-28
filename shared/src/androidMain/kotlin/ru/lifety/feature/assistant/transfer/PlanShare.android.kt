package ru.lifety.feature.assistant.transfer

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberPlanShare(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) { { text ->
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text); putExtra(Intent.EXTRA_SUBJECT, "Мой план Lifety") }
        context.startActivity(Intent.createChooser(send, "Поделиться планом").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } }
}
