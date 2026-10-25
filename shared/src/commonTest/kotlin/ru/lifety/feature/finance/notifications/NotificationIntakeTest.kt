package ru.lifety.feature.finance.notifications

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationIntakeTest {
    private val bank = "ru.sberbankmobile"
    private fun purchase() = BankNotification("purchase-1", bank, "Покупка 100 RUB в SHOP", 1000)

    @Test fun consentAndSelectedSupportedBankAreRequired() {
        val inbox = NotificationInbox()
        val intake = NotificationIntake(inbox)
        assertFalse(intake.accepts(bank, false, setOf(bank)))
        assertEquals(ImportOutcome.IGNORED, intake.receive(purchase(), false, setOf(bank)))
        assertEquals(ImportOutcome.IGNORED, intake.receive(purchase(), true, emptySet()))
        assertFalse(intake.accepts("unknown.app", true, setOf("unknown.app")))
        assertTrue(inbox.pending.value.isEmpty())
        assertEquals(null, intake.lastResult.value)
    }

    @Test fun hiddenOrUnparseableContentProducesNoDraftAndAnActionableStatus() {
        val inbox = NotificationInbox()
        val intake = NotificationIntake(inbox)
        assertEquals(ImportOutcome.UNREADABLE,
            intake.receive(BankNotification("hidden", bank, "Содержимое скрыто", 1000), true, setOf(bank)))
        assertEquals(ImportOutcome.UNREADABLE, intake.lastResult.value)
        assertEquals(ImportOutcome.UNREADABLE,
            intake.receive(BankNotification("hidden-title", bank, "Откройте приложение", 1000,
                title = "Содержимое скрыто"), true, setOf(bank)))
        assertEquals(ImportOutcome.UNREADABLE,
            intake.receive(BankNotification("empty", bank, "", 1000), true, setOf(bank)))
        assertTrue(inbox.pending.value.isEmpty())
        assertEquals(ImportOutcome.UNRECOGNIZED,
            intake.receive(BankNotification("bad", bank, "Покупка: сумма недоступна", 1000), true, setOf(bank)))
        assertTrue(inbox.pending.value.isEmpty())
        intake.clearResult()
        assertEquals(null, intake.lastResult.value)
    }

    @Test fun selectedPurchasesBecomeOneUnconfirmedDraftAndTransfersDoNot() {
        val inbox = NotificationInbox()
        val intake = NotificationIntake(inbox)
        assertEquals(ImportOutcome.AWAITING_CONFIRMATION, intake.receive(purchase(), true, setOf(bank)))
        assertEquals(ImportOutcome.DUPLICATE, intake.receive(purchase(), true, setOf(bank)))
        assertEquals(1, inbox.pending.value.size)
        assertEquals(ImportOutcome.NOT_PURCHASE,
            intake.receive(BankNotification("transfer", bank, "Перевод 100 RUB Иван", 1000), true, setOf(bank)))
        assertEquals(1, inbox.pending.value.size)
    }

    @Test fun demoUsesTheSameDraftPipelineWithoutRequiringBankConsent() {
        val inbox = NotificationInbox()
        val intake = NotificationIntake(inbox)
        assertEquals(ImportOutcome.AWAITING_CONFIRMATION, intake.demo(1000, "demo-event"))
        assertTrue(inbox.pending.value.single().isDemo)
        assertEquals(34990L, inbox.pending.value.single().amountMinor)
        assertEquals(ImportOutcome.DUPLICATE, intake.demo(1000, "demo-event"))
    }
}
