package ru.lifety.feature.finance.notifications

import ru.lifety.core.model.Expense
import ru.lifety.feature.finance.parseMinorAmount
import ru.lifety.feature.finance.inferFinanceCategory

enum class OperationType { PURCHASE, INCOME, TRANSFER }

data class BankNotification(
    val eventId: String,
    val packageName: String,
    val text: String,
    val timestamp: Long,
    val isDemo: Boolean = false,
    val title: String = "",
)

data class ExpenseSuggestion(
    val id: String,
    val packageName: String,
    val amountMinor: Long,
    val currency: String,
    val merchant: String,
    val timestamp: Long,
    val operation: OperationType,
    val isDemo: Boolean,
) {
    fun toExpense(): Expense {
        require(operation == OperationType.PURCHASE)
        return Expense(id, amountMinor, currency,
            (if (isDemo) "[ДЕМО] " else "") + merchant,
            inferFinanceCategory(merchant), timestamp,
            if (isDemo) "demo-notification" else "notification:$packageName")
    }
}

val supportedBanks = linkedMapOf(
    "ru.sberbankmobile" to "СберБанк",
    "com.idamob.tinkoff.android" to "Т-Банк",
    "ru.alfabank.mobile.android" to "Альфа-Банк",
)

fun interface BankMessageParser {
    fun parse(notification: BankNotification): ExpenseSuggestion?
}

/** Add a format parser here; the package allowlist is enforced before invoking it. */
class BankNotificationParser(
    private val packages: Set<String> = supportedBanks.keys,
    private val parsers: List<BankMessageParser> = listOf(KeywordBankMessageParser()),
) {
    fun parse(notification: BankNotification): ExpenseSuggestion? {
        if (notification.packageName !in packages || notification.text.length > 4000 || notification.title.length > 4000 ||
            notification.eventId.isBlank()) return null
        return parsers.firstNotNullOfOrNull { it.parse(notification) }
    }
}

/** Conservative templates, not a promise to recognize every bank/app version. No I/O. */
private class KeywordBankMessageParser : BankMessageParser {
    private val income = Regex("пополн|зачисл|возврат|refund|cashback|кэшбэк", RegexOption.IGNORE_CASE)
    private val transfer = Regex("перевод|перевели|transfer|сбп", RegexOption.IGNORE_CASE)
    private val failed = Regex("отмен|отклон|недостаточно|не выполн|неуспеш|отказ|declin|cancel|не прош|не провед|не заверш|ошибк|не удалось|код|парол|подтверд|подтвержд|ожида|запрос|предстоящ|напомин", RegexOption.IGNORE_CASE)
    private val purchase = Regex("покупка|оплата|purchase|payment", RegexOption.IGNORE_CASE)
    private val balance = Regex("баланс|доступно|остаток|balance|available", RegexOption.IGNORE_CASE)
    private val amount = Regex(
        "(?<![0-9.,+\\-])([0-9][0-9 \\u00a0\\u202f]*(?:[.,][0-9]+)?)\\s*" +
            "(RUB|RUR|USD|EUR|₽|руб(?:лей|ля|\\.)?|р\\.?|\\$|€)(?![a-zа-я])",
        RegexOption.IGNORE_CASE,
    )

    override fun parse(notification: BankNotification): ExpenseSuggestion? {
        val body = notification.text.replace(Regex("[\\r\\n\\t]+"), " ").trim()
        val context = "${notification.title} $body"
        if (failed.containsMatchIn(context)) return null
        val operation = when {
            income.containsMatchIn(context) -> OperationType.INCOME
            transfer.containsMatchIn(context) -> OperationType.TRANSFER
            purchase.containsMatchIn(context) -> OperationType.PURCHASE
            else -> return null
        }
        val operationPattern = when (operation) {
            OperationType.PURCHASE -> purchase
            OperationType.INCOME -> income
            OperationType.TRANSFER -> transfer
        }
        // The title still rejects/classifies the event, but a repeated label is not a second purchase.
        val text = if (operationPattern.containsMatchIn(body)) body else context
        val operationMatch = operationPattern.find(text) ?: return null
        if (operation == OperationType.PURCHASE && purchase.findAll(text).count() != 1) return null
        val transaction = text.substring(operationMatch.range.last + 1)
            .let { body -> balance.find(body)?.let { body.substring(0, it.range.first) } ?: body }
        val matches = amount.findAll(transaction).toList()
        if (matches.size != 1) return null
        val money = matches.single()
        if (Regex("[+\\-−]\\s*$").containsMatchIn(transaction.substring(0, money.range.first))) return null
        val minor = parseMinorAmount(money.groupValues[1]) ?: return null
        val currency = when (money.groupValues[2].uppercase()) {
            "USD", "$" -> "USD"
            "EUR", "€" -> "EUR"
            else -> "RUB"
        }
        val before = transaction.substring(0, money.range.first).trim().removePrefix("в ")
            .replace(Regex("(^|\\s+)(на сумму|на|сумма)$", RegexOption.IGNORE_CASE), "").trim()
        val after = transaction.substring(money.range.last + 1).trim(' ', '.', ',', ':', ';', '-')
            .removePrefix("в ").trim(' ', '.', ',', ':', ';', '-')
        val merchant = (before.takeIf { it.isNotBlank() } ?: after.takeIf { it.isNotBlank() }
            ?: "Неизвестный магазин").take(100)
        return ExpenseSuggestion("notification:${notification.eventId}", notification.packageName,
            minor, currency, merchant, notification.timestamp, operation, notification.isDemo)
    }
}

object DemoNotifications {
    fun purchase(timestamp: Long, eventId: String = "demo-$timestamp") = BankNotification(
        eventId, "ru.sberbankmobile", "Покупка 349,90 RUB в COFFEE POINT. Баланс 12 000 RUB",
        timestamp, isDemo = true,
    )
}
