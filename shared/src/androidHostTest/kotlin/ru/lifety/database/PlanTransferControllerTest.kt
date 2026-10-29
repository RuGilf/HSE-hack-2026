package ru.lifety.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.datetime.TimeZone
import kotlinx.serialization.json.Json
import ru.lifety.core.di.AppContainer
import ru.lifety.core.model.Task
import ru.lifety.core.planning.*
import ru.lifety.database.generated.LifetyDatabase
import ru.lifety.feature.assistant.transfer.PlanTransferController
import kotlin.time.Instant
import kotlin.test.*

class PlanTransferControllerTest {
    private val now = Instant.parse("2026-10-28T12:00:00Z").toEpochMilliseconds()
    @Test fun preparationHandoffAcknowledgementPreviewAndApplyHaveDistinctPersistedStates() = runBlocking<Unit> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY); LifetyDatabase.Schema.create(driver)
        AppContainer(driver).use { app ->
            app.repositories.tasks.upsert(Task("a", "Доклад", null, null, false))
            val job = SupervisorJob(); val scope = CoroutineScope(coroutineContext + job)
            try {
                val controller = PlanTransferController(app.repositories, scope, { now }, { TimeZone.currentSystemDefault() })
                withTimeout(5000) { controller.state.first { !it.loading && it.snapshot != null } }
                controller.prepare(true, "Два часа")
                val draft = withTimeout(5000) { controller.state.first { !it.busy && (it.workspace.draft != null || it.error != null) } }.also { assertNull(it.error) }.workspace.draft!!
                assertNull(app.repositories.chatGptExchange!!.observe().first().lastSent)
                var sentText: String? = null
                controller.handoff { sentText = it }
                withTimeout(5000) { controller.state.first { !it.busy } }
                assertEquals(draft.prompt, sentText)
                assertNull(app.repositories.chatGptExchange!!.observe().first().lastSent) // Sharing/copying is not acknowledgement.
                controller.markSent()
                withTimeout(5000) { controller.state.first { !it.busy && (it.workspace.lastSent?.awaitingReply == true || it.error != null) } }.also { assertNull(it.error) }
                val plan = ImportedPlan("lifety-plan-v2", draft.baseSnapshot, "Дело оставлено в очереди", emptyList(), listOf(DeferredTask("a", "Нет доступного времени")), draft.id)
                controller.preview(Json.encodeToString(plan))
                val previewState = withTimeout(5000) { controller.state.first { !it.busy && (it.preview != null || it.error != null) } }
                assertNull(previewState.error)
                assertNull(app.repositories.planning!!.observeReport().first())
                controller.apply()
                withTimeout(5000) { controller.state.first { !it.busy && it.applied && it.workspace.receipt != null } }
                assertNull(controller.state.value.workspace.draft)
                assertFalse(controller.state.value.workspace.lastSent!!.awaitingReply)
            } finally { job.cancelAndJoin() }
        }
    }
    @Test fun changingTasksDisablesHandoffAndDoesNotAcknowledgeStaleDraft() = runBlocking<Unit> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY); LifetyDatabase.Schema.create(driver)
        AppContainer(driver).use { app ->
            app.repositories.tasks.upsert(Task("a", "Доклад", null, null, false))
            val job = SupervisorJob(); val scope = CoroutineScope(coroutineContext + job)
            try {
                val controller = PlanTransferController(app.repositories, scope, { now })
                withTimeout(5000) { controller.state.first { !it.loading } }
                controller.prepare(true, "")
                withTimeout(5000) { controller.state.first { !it.busy && (it.workspace.draft != null || it.error != null) } }.also { assertNull(it.error) }
                app.repositories.tasks.upsert(Task("a", "Другой доклад", null, null, false))
                withTimeout(5000) { controller.state.first { it.draftStale } }
                var handedOff = false
                controller.handoff { handedOff = true }
                withTimeout(5000) { controller.state.first { !it.busy } }
                assertFalse(handedOff); assertNotNull(controller.state.value.error)
                assertNull(app.repositories.chatGptExchange!!.observe().first().lastSent)
            } finally { job.cancelAndJoin() }
        }
    }
}
