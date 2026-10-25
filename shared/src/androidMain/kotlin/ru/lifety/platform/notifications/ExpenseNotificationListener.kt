package ru.lifety.platform.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.security.MessageDigest
import ru.lifety.feature.finance.notifications.BankNotification
import ru.lifety.feature.finance.notifications.BankNotificationParser
import ru.lifety.feature.finance.notifications.FinanceNotifications

/** Receives only after Android's explicit user grant; never saves an expense itself. */
class ExpenseNotificationListener : NotificationListenerService() {
    private val parser = BankNotificationParser()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val settings = NotificationSettings(applicationContext)
        if (!settings.consent || sbn.packageName !in settings.selectedBanks) return
        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val transactionTime = notification.`when`.takeIf { it > 0 } ?: sbn.postTime
        // Android key + original notification time survives re-delivery and process restarts.
        val identity = "${sbn.packageName}|${sbn.key}|$transactionTime"
        val eventId = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        parser.parse(BankNotification(eventId, sbn.packageName, body, transactionTime, title = title))?.let {
            FinanceNotifications.inbox.offer(it)
        }
    }
}
