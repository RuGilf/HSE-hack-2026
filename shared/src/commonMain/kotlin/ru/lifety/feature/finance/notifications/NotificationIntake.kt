package ru.lifety.feature.finance.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ImportOutcome {
    IGNORED, UNREADABLE, UNRECOGNIZED, NOT_PURCHASE, DUPLICATE, AWAITING_CONFIRMATION,
}

/** Common, main-thread intake. Only a status enum and parsed drafts survive processing. */
class NotificationIntake(
    private val inbox: NotificationInbox,
    private val parser: BankNotificationParser = BankNotificationParser(),
) {
    private val mutableLastResult = MutableStateFlow<ImportOutcome?>(null)
    val lastResult = mutableLastResult.asStateFlow()
    private val hidden = Regex("содержим[^ ]*\\s+скрыт|content\\s+hidden|unlock to view", RegexOption.IGNORE_CASE)

    fun accepts(packageName: String, consent: Boolean, selectedBanks: Set<String>): Boolean =
        consent && packageName in selectedBanks && packageName in supportedBanks

    fun receive(notification: BankNotification, consent: Boolean, selectedBanks: Set<String>): ImportOutcome {
        if (!accepts(notification.packageName, consent, selectedBanks)) return ImportOutcome.IGNORED
        return process(notification)
    }

    fun demo(timestamp: Long, eventId: String): ImportOutcome = process(DemoNotifications.purchase(timestamp, eventId))

    fun clearResult() { mutableLastResult.value = null }

    private fun process(notification: BankNotification): ImportOutcome {
        val contentHidden = hidden.containsMatchIn("${notification.title} ${notification.text}")
        val suggestion = if (contentHidden) null
            else parser.parse(notification)
        val result = when {
            suggestion == null && (notification.text.isBlank() || contentHidden) -> ImportOutcome.UNREADABLE
            suggestion == null -> ImportOutcome.UNRECOGNIZED
            suggestion.operation != OperationType.PURCHASE -> ImportOutcome.NOT_PURCHASE
            inbox.offer(suggestion) -> ImportOutcome.AWAITING_CONFIRMATION
            else -> ImportOutcome.DUPLICATE
        }
        mutableLastResult.value = result
        return result
    }
}
