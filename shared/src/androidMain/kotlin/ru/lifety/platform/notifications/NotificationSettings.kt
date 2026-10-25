package ru.lifety.platform.notifications

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import ru.lifety.feature.finance.notifications.supportedBanks

internal class NotificationSettings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("lifety_notification_consent", Context.MODE_PRIVATE)
    var consent: Boolean
        get() = prefs.getBoolean("consent", false)
        set(value) { prefs.edit().putBoolean("consent", value).apply() }
    var selectedBanks: Set<String>
        get() = prefs.getStringSet("selected_banks", emptySet()).orEmpty().intersect(supportedBanks.keys)
        set(value) { prefs.edit().putStringSet("selected_banks", value.intersect(supportedBanks.keys)).apply() }
}

internal fun listenerComponent(context: Context) = ComponentName(context, ExpenseNotificationListener::class.java)

internal fun hasListenerAccess(context: Context): Boolean {
    val component = listenerComponent(context)
    return if (Build.VERSION.SDK_INT >= 27) {
        context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component)
    } else {
        Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
            .split(':').any { ComponentName.unflattenFromString(it) == component }
    }
}

internal fun isListenerRegistered(context: Context): Boolean = try {
    @Suppress("DEPRECATION")
    context.packageManager.getServiceInfo(listenerComponent(context), 0)
    true
} catch (_: android.content.pm.PackageManager.NameNotFoundException) { false }
