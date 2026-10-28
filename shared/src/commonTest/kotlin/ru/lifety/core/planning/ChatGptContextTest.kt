package ru.lifety.core.planning

import kotlinx.datetime.*
import kotlinx.serialization.json.*
import ru.lifety.core.model.*
import kotlin.test.*
import kotlin.time.Instant

class ChatGptContextTest {
    private val now = Instant.parse("2026-10-28T12:00:00Z").toEpochMilliseconds()
    private val zone = TimeZone.of("Europe/Moscow")
    private val snapshot = PlanSnapshot(listOf(Task("a", "Доклад", null, null, false), Task("b", "Книга", null, null, false)), listOf(Goal("g", "Цель")), emptyList())
    private fun row(date: String, sleep: Int?, steps: Int? = null) = HealthSnapshot(Instant.parse(date).toEpochMilliseconds(), sleep, steps)
    @Test fun sevenDaysIncludeMissingDaysAndLatestKnownMetricWithoutDoubleCounting() {
        val summary = summarizeSleep(listOf(row("2026-10-27T01:00:00Z", 420), row("2026-10-27T09:00:00Z", 480), row("2026-10-27T10:00:00Z", null, 9000), row("2026-10-28T08:00:00Z", 0), row("2026-10-29T08:00:00Z", 700)), now, zone)
        assertEquals(7, summary.last7Days.size)
        assertEquals("2026-10-22", summary.last7Days.first().date)
        assertNull(summary.last7Days.first().minutes)
        assertEquals(480, summary.last7Days[5].minutes)
        assertEquals(0, summary.last7Days.last().minutes)
        val month = summary.months.single()
        assertEquals(240.0, month.medianMinutes); assertEquals(2, month.recordedDays); assertEquals(31, month.daysInMonth); assertTrue(month.isPartial)
    }
    @Test fun medianUsesDailyValuesNotRowsAndHandlesOddCountsLeapMonthsAndTimezones() {
        val summary = summarizeSleep(listOf(row("2024-02-01T01:00:00Z", 100), row("2024-02-02T01:00:00Z", 400), row("2024-02-03T01:00:00Z", 900), row("2026-10-27T22:30:00Z", 440)), now, zone)
        assertEquals(listOf("2024-02", "2026-10"), summary.months.map { it.month })
        assertEquals(400.0, summary.months.first().medianMinutes); assertEquals(29, summary.months.first().daysInMonth); assertFalse(summary.months.first().isPartial)
        assertEquals(440, summary.last7Days.last().minutes)
    }
    @Test fun yearOfSleepIsReducedToSevenDaysAndMonthlyAggregates() {
        val today = LocalDate(2026, 10, 28)
        val rows = List(365) { i ->
            val date = today.minus(DatePeriod(days = i))
            HealthSnapshot(date.atTime(8, 0).toInstant(zone).toEpochMilliseconds(), 400 + i % 40, 123456)
        }
        val summary = summarizeSleep(rows, now, zone)
        assertEquals(7, summary.last7Days.size); assertEquals(13, summary.months.size)
        assertEquals(365, summary.months.sumOf { it.recordedDays })
        val encoded = Json.encodeToString(summary)
        assertTrue(encoded.length < 3000); assertFalse(encoded.contains("123456")); assertFalse(encoded.contains("timestamp"))
    }
    @Test fun initialAndUpdatePacketsContainFullCurrentSnapshotButOnlyChangedPreferences() {
        val preferences = ChatGptPreferences("Не ставь работу до 10 утра")
        val first = prepareChatGptDraft(snapshot, preferences, null, null, true, "Есть два часа", "r1", now, zone)
        val sent = ChatGptSent(first.id, now, first.baseSnapshot, first.sourceFingerprint, first.profileFingerprint, first.baseline)
        val changed = snapshot.copy(tasks = listOf(snapshot.tasks[0].copy(title = "Новый доклад"), snapshot.tasks[1].copy(isCompleted = true), Task("c", "Новая задача", null, null, false)))
        val next = prepareChatGptDraft(changed, preferences, null, sent, false, "", "r2", now, zone)
        assertTrue(first.prompt.contains(preferences.instructions)); assertFalse(next.prompt.contains(preferences.instructions))
        val context = Json.parseToJsonElement(next.prompt.substringAfter("ДАННЫЕ LIFETY:\n")).jsonObject
        assertEquals(setOf("a", "c"), context.getValue("tasks").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.content }.toSet())
        assertEquals(ContextChanges(1, 1, 1, 0), contextChanges(next.baseline, first.baseline))
        assertFalse(context.containsKey("sleep"))
        val revised = prepareChatGptDraft(changed, preferences.copy(instructions = "Другие часы"), null, sent, false, "", "r3", now, zone)
        assertTrue(revised.prompt.contains("Другие часы"))
    }
    @Test fun sleepIsOptInAndContextFreshnessTracksHealthProfileDayAndKnownRequest() {
        val sleep = summarizeSleep(listOf(row("2026-10-28T08:00:00Z", 420)), now, zone)
        val preferences = ChatGptPreferences(includeSleep = true)
        val draft = prepareChatGptDraft(snapshot, preferences, sleep, null, true, "", "r", now, zone)
        assertTrue(draft.prompt.substringAfter("ДАННЫЕ LIFETY:\n").contains("medianMinutes"))
        val workspace = ChatGptWorkspace(preferences, draft)
        val plan = ImportedPlan("lifety-plan-v2", draft.baseSnapshot, "Очередь", emptyList(), snapshot.tasks.map { DeferredTask(it.id, "Не хватает времени") }, draft.id)
        validateImportedPlan(plan, snapshot, now); validateChatGptResponse(plan, workspace, snapshot, sleep, now, zone)
        assertFailsWith<IllegalArgumentException> { validateChatGptResponse(plan.copy(requestId = "unknown"), workspace, snapshot, sleep, now, zone) }
        assertFailsWith<IllegalArgumentException> { validateChatGptResponse(plan, workspace, snapshot, sleep.copy(months = emptyList()), now, zone) }
        assertFailsWith<IllegalArgumentException> { validateChatGptResponse(plan, workspace, snapshot, sleep, now + 86_400_000, zone) }
        assertFailsWith<IllegalArgumentException> { validateChatGptResponse(plan, workspace.copy(preferences = preferences.copy(instructions = "Изменено")), snapshot, sleep, now, zone) }
    }
    @Test fun v2QueueRequiresCoverageAndReasonWithoutDuplicateIds() {
        val plan = ImportedPlan("lifety-plan-v2", snapshotFingerprint(snapshot), "Всё в очередь", emptyList(), listOf(DeferredTask("a", "Завтра"), DeferredTask("b", "На выходных")), "r")
        validateImportedPlan(plan, snapshot, now)
        for (queue in listOf(plan.deferredTasks.take(1), plan.deferredTasks + plan.deferredTasks[0], plan.deferredTasks.map { it.copy(reason = "") })) {
            assertFailsWith<IllegalArgumentException> { validateImportedPlan(plan.copy(deferredTasks = queue), snapshot, now) }
        }
        assertFailsWith<IllegalArgumentException> { validateImportedPlan(plan.copy(format = "lifety-plan-v1"), snapshot, now) }
    }
    @Test fun conversationLinkRequiresPrivateHttpsConversationOnChatGpt() {
        assertTrue(validConversationUrl("")); assertTrue(validConversationUrl("https://chatgpt.com/c/a-b-c")); assertTrue(validConversationUrl("https://chatgpt.com/g/g-123-lifety/c/a-b-c"))
        listOf("https://chatgpt.com/share/a", "https://chatgpt.com.evil.test/c/a", "http://chatgpt.com/c/a", "javascript:alert(1)", "https://user@chatgpt.com/c/a").forEach { assertFalse(validConversationUrl(it)) }
    }
}
