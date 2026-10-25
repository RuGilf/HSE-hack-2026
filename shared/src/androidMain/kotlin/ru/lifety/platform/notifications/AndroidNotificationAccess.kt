package ru.lifety.platform.notifications

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.lifety.feature.finance.notifications.FinanceNotifications
import ru.lifety.feature.finance.notifications.supportedBanks

@Composable
fun AndroidNotificationAccess() {
    val context = LocalContext.current
    val settings = remember(context) { NotificationSettings(context) }
    var consent by remember { mutableStateOf(settings.consent) }
    var selected by remember { mutableStateOf(settings.selectedBanks) }
    var granted by remember { mutableStateOf(hasListenerAccess(context)) }
    var error by remember { mutableStateOf<String?>(null) }
    val registered = remember(context) { isListenerRegistered(context) }
    DisposableEffect(context) {
        val application = context.applicationContext as Application
        val callback = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { granted = hasListenerAccess(context) }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        }
        application.registerActivityLifecycleCallbacks(callback)
        onDispose { application.unregisterActivityLifecycleCallbacks(callback) }
    }
    Column {
        Text("Android предоставляет доступ к уведомлениям. Lifety локально разбирает только выбранные банки; текст не отправляется в сеть.",
            style = MaterialTheme.typography.bodySmall)
        supportedBanks.forEach { (pkg, label) ->
            Row {
                Checkbox(checked = pkg in selected, onCheckedChange = { checked ->
                    selected = if (checked) selected + pkg else selected - pkg
                    settings.selectedBanks = selected
                    if (!checked) FinanceNotifications.inbox.discardBank(pkg)
                })
                Text(label, modifier = Modifier.padding(top = 12.dp))
            }
        }
        if (!registered) Text("Автоимпорт пока недоступен в этой сборке. Можно попробовать демо ниже.")
        Text(when {
            consent && granted && selected.isNotEmpty() -> "Автоимпорт включён. Покупки ожидают подтверждения здесь."
            consent && !granted -> "Разрешите доступ в настройках Android."
            else -> "Автоимпорт выключен."
        })
        OutlinedButton(enabled = registered && selected.isNotEmpty(), onClick = {
            settings.consent = true
            consent = true
            try {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: ActivityNotFoundException) {
                settings.consent = false
                consent = false
                error = "Системные настройки доступа к уведомлениям недоступны."
            }
        }) { Text(if (consent) "Настройки доступа Android" else "Разрешить чтение выбранных банков") }
        if (consent) TextButton(onClick = {
            settings.consent = false
            consent = false
            supportedBanks.keys.forEach { FinanceNotifications.inbox.discardBank(it) }
        }) { Text("Отключить автоимпорт") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
