package ru.lifety.feature.finance.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BankNotificationParserTest {
    private val parser = BankNotificationParser()
    private fun parse(text: String, bank: String = "ru.sberbankmobile") =
        parser.parse(BankNotification("event-1", bank, text, 1000))

    @Test fun recognizesSberPurchaseAndDoesNotUseTheBalance() {
        val result = parse("VISA1234 25.10.26 13:10 Покупка 1 234,56р. PYATEROCHKA Баланс: 45 000р")!!
        assertEquals(123456L, result.amountMinor)
        assertEquals("RUB", result.currency)
        assertEquals("PYATEROCHKA", result.merchant)
        assertEquals(OperationType.PURCHASE, result.operation)
    }

    @Test fun recognizesTBankFormatAndForeignCurrency() {
        val result = parse("Покупка в COFFEE POINT на 4.50 USD. Доступно 100 USD", "com.idamob.tinkoff.android")!!
        assertEquals(450L, result.amountMinor)
        assertEquals("USD", result.currency)
        assertEquals("COFFEE POINT", result.merchant)
    }

    @Test fun recognizesAlfaPayment() {
        val result = parse("Оплата 780,00 RUB в METRO. Баланс 12000 RUB", "ru.alfabank.mobile.android")!!
        assertEquals(78000L, result.amountMinor)
        assertEquals("METRO", result.merchant)
    }

    @Test fun classifiesTransfersAndIncomeWithoutMakingPurchaseSuggestions() {
        assertEquals(OperationType.TRANSFER, parse("Перевод 500 RUB в пользу Ивана")!!.operation)
        assertEquals(OperationType.INCOME, parse("Пополнение 500 RUB")!!.operation)
        assertEquals(OperationType.INCOME, parse("Возврат покупки 500 RUB SHOP")!!.operation)
        val inbox = NotificationInbox()
        assertFalse(inbox.offer(parse("Перевод 500 RUB Иван")!!))
        assertTrue(inbox.pending.value.isEmpty())
    }

    @Test fun ignoresUnknownAppsAndMalformedAmbiguousOrFailedOperations() {
        assertNull(parse("Покупка 50 RUB SHOP", "org.telegram.messenger"))
        listOf("Код покупки 123456", "Баланс 123 RUB", "Покупка -100 RUB SHOP",
            "Покупка 1.234 RUB SHOP", "Покупка 92233720368547758.08 RUB SHOP",
            "Покупка 0 RUB SHOP", "Покупка 100 RUB SHOP отменена",
            "Недостаточно средств. Покупка 100 RUB SHOP", "Покупка 100 RUB SHOP; Оплата 200 RUB CAFE")
            .forEach { assertNull(parse(it), it) }
    }

    @Test fun deduplicatesPendingDismissedAndConfirmedNotifications() {
        val inbox = NotificationInbox()
        val item = parse("Покупка 100 RUB SHOP")!!
        assertTrue(inbox.offer(item))
        assertFalse(inbox.offer(item))
        assertEquals(1, inbox.pending.value.size)
        inbox.dismiss(item.id)
        assertFalse(inbox.offer(item))
        assertTrue(inbox.pending.value.isEmpty())
    }

    @Test fun supportsAdditionalFormatsAndStillEnforcesTheBankAllowlist() {
        val extra = BankMessageParser { event ->
            if (event.text == "CUSTOM:25") ExpenseSuggestion("notification:${event.eventId}", event.packageName,
                2500, "RUB", "CUSTOM SHOP", event.timestamp, OperationType.PURCHASE, false) else null
        }
        val extensible = BankNotificationParser(parsers = listOf(extra))
        assertEquals(2500L, extensible.parse(BankNotification("extra", "ru.sberbankmobile", "CUSTOM:25", 1000))!!.amountMinor)
        assertNull(extensible.parse(BankNotification("extra", "unselected.app", "CUSTOM:25", 1000)))
    }

    @Test fun handlesPurchaseOnAmountWordingAndRejectsCurrencyPrefixes() {
        assertEquals("SHOP", parse("Покупка на 100 RUB в SHOP")!!.merchant)
        assertNull(parse("Покупка 100 RUBCOIN SHOP"))
        assertNull(parse("Покупка +100 RUB SHOP"))
    }
    @Test fun rejectsAuthorizationPromptsUnfinishedPaymentsAndSpacedSigns() {
        listOf("Оплата 100 RUB в SHOP не прошла", "Оплата 100 RUB в SHOP не проведена",
            "Код для подтверждения: Покупка 100 RUB в SHOP. Код 123456",
            "Подтвердите: Покупка 100 RUB SHOP", "Покупка - 100 RUB SHOP",
            "Покупка + 100 RUB SHOP", "Покупка −100 RUB SHOP",
            "Запрос на оплату: Покупка 100 RUB SHOP", "Покупка 100 RUB SHOP ожидает подтверждения")
            .forEach { assertNull(parse(it), it) }
    }
    @Test fun usesTheTitleForRejectionsWithoutDuplicatingPurchaseLabels() {
        assertNull(parser.parse(BankNotification("x", "ru.sberbankmobile", "Покупка 100 RUB в SHOP", 1000,
            title = "Оплата отклонена")))
        assertNull(parser.parse(BankNotification("x", "ru.sberbankmobile", "Покупка 100 RUB в SHOP", 1000,
            title = "Подтвердите операцию")))
        val valid = parser.parse(BankNotification("x", "ru.sberbankmobile", "Покупка 100 RUB в SHOP", 1000,
            title = "Покупка"))!!
        assertEquals(10000L, valid.amountMinor)
        assertEquals("SHOP", valid.merchant)
        val transfer = parser.parse(BankNotification("x", "ru.sberbankmobile", "Оплата 100 RUB SHOP", 1000,
            title = "Перевод"))
        assertTrue(transfer == null || transfer.operation == OperationType.TRANSFER)
    }
    @Test fun demoIsExplicitAndTravelsThroughTheRealParser() {
        val inbox = NotificationInbox()
        val demo = DemoNotifications.purchase(1234)
        assertTrue(demo.isDemo)
        val candidate = parser.parse(demo)!!
        assertTrue(inbox.offer(candidate))
        val expense = candidate.toExpense()
        assertEquals("demo-notification", expense.source)
        assertTrue(expense.merchant.startsWith("[ДЕМО]"))
        assertEquals(34990L, expense.amountMinor)
    }
}
