package ru.lifety.feature.finance.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Main-thread inbox of parsed suggestions, never a transaction store or raw text log. */
class NotificationInbox {
    private val mutablePending = MutableStateFlow<List<ExpenseSuggestion>>(emptyList())
    val pending = mutablePending.asStateFlow()
    private val seen = linkedSetOf<String>()

    fun offer(suggestion: ExpenseSuggestion): Boolean {
        if (suggestion.operation != OperationType.PURCHASE || !seen.add(suggestion.id)) return false
        if (seen.size > 500) seen.remove(seen.first())
        mutablePending.value = (mutablePending.value + suggestion).takeLast(50)
        return true
    }

    fun dismiss(id: String) {
        mutablePending.value = mutablePending.value.filterNot { it.id == id }
    }

    fun discardBank(packageName: String) {
        mutablePending.value = mutablePending.value.filterNot { !it.isDemo && it.packageName == packageName }
    }
}

object FinanceNotifications {
    val inbox = NotificationInbox()
    val intake = NotificationIntake(inbox)
}
