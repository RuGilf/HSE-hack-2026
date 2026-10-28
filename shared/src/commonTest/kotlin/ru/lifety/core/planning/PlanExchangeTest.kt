package ru.lifety.core.planning

import kotlinx.datetime.TimeZone
import ru.lifety.core.model.*
import kotlin.time.Instant
import kotlin.test.*

class PlanExchangeTest {
    private val now = Instant.parse("2026-10-27T09:00:00Z").toEpochMilliseconds()
    private val task = Task("t1", "Подготовить доклад", null, now + 86_400_000, false)
    private val snapshot = PlanSnapshot(listOf(task), listOf(Goal("g1", "Защитить проект")), emptyList())
    private fun plan(s: PlanSnapshot = snapshot) = ImportedPlan("lifety-plan-v1", snapshotFingerprint(s), "Начать с тезисов",
        s.tasks.filterNot { it.isCompleted }.mapIndexed { i, t -> PlannedTask(t.id, Instant.fromEpochMilliseconds(now + (i + 1) * 3_600_000L).toString(), 45, "Составить структуру, затем черновик", "g1") })
    @Test fun validPlanPreservesOriginalTaskAndParsesFencedResponse() {
        val p = plan()
        validateImportedPlan(p, snapshot, now)
        val json = planExchangeJson.encodeToString(p)
        assertEquals(p, parseImportedPlan("```json\n$json\n```"))
        assertNull(task.scheduledAt)
        assertEquals(now + 86_400_000, task.deadlineAt)
    }
    @Test fun changedTitlesDeadlinesCompletionGoalsOrEstimatesInvalidateSnapshot() {
        val variants = listOf(snapshot.copy(tasks = listOf(task.copy(title = "Другое"))), snapshot.copy(tasks = listOf(task.copy(deadlineAt = now))),
            snapshot.copy(tasks = listOf(task.copy(isCompleted = true))), snapshot.copy(goals = emptyList()), snapshot.copy(details = listOf(TaskPlan("t1", 90))))
        variants.forEach { assertFailsWith<IllegalArgumentException> { validateImportedPlan(plan(), it, now) } }
    }
    @Test fun orderDoesNotChangeSnapshotFingerprint() {
        val s = snapshot.copy(tasks = listOf(task, task.copy(id = "t2")), goals = listOf(Goal("g1", "A"), Goal("g2", "B")))
        assertEquals(snapshotFingerprint(s), snapshotFingerprint(s.copy(tasks = s.tasks.reversed(), goals = s.goals.reversed())))
    }
    @Test fun unknownDuplicateMissingAndCompletedIdsAreRejected() {
        val p = plan()
        listOf(p.copy(tasks = emptyList()), p.copy(tasks = p.tasks + p.tasks), p.copy(tasks = listOf(p.tasks.single().copy(taskId = "unknown")))).forEach {
            assertFailsWith<IllegalArgumentException> { validateImportedPlan(it, snapshot, now) }
        }
        val complete = snapshot.copy(tasks = listOf(task.copy(isCompleted = true)))
        assertFailsWith<IllegalArgumentException> { validateImportedPlan(plan(complete), complete, now) }
    }
    @Test fun durationApproachAndUnknownGoalsAreValidated() {
        val p = plan(); val item = p.tasks.single()
        listOf(item.copy(durationMinutes = 0), item.copy(durationMinutes = 1441), item.copy(approach = ""), item.copy(goalId = "missing"), item.copy(scheduledAt = "27.10.2026 10:00")).forEach {
            assertFailsWith<IllegalArgumentException> { validateImportedPlan(p.copy(tasks = listOf(it)), snapshot, now) }
        }
    }
    @Test fun pastSchedulesAndFinishingAfterFutureDeadlineAreRejected() {
        val p = plan()
        assertFailsWith<IllegalArgumentException> { validateImportedPlan(p.copy(tasks = listOf(p.tasks.single().copy(scheduledAt = "2026-10-27T08:00:00Z"))), snapshot, now) }
        val near = snapshot.copy(tasks = listOf(task.copy(deadlineAt = now + 3_600_001)))
        assertFailsWith<IllegalArgumentException> { validateImportedPlan(plan(near), near, now) }
    }
    @Test fun overdueTaskCanBeRescheduledKeepingExpiredDeadline() {
        val overdue = snapshot.copy(tasks = listOf(task.copy(deadlineAt = now - 1)))
        validateImportedPlan(plan(overdue), overdue, now)
    }
    @Test fun overlapsAreRejectedButAdjacentSlotsAreAllowed() {
        val s = snapshot.copy(tasks = listOf(task, task.copy(id = "t2")))
        val p = plan(s)
        assertFailsWith<IllegalArgumentException> { validateImportedPlan(p.copy(tasks = listOf(p.tasks[0], p.tasks[1].copy(scheduledAt = p.tasks[0].scheduledAt))), s, now) }
        validateImportedPlan(p.copy(tasks = listOf(p.tasks[0], p.tasks[1].copy(scheduledAt = Instant.fromEpochMilliseconds(now + 3_600_000 + 45 * 60_000).toString()))), s, now)
    }
    @Test fun exportHasTimezoneExactIdsAndNoUnrelatedData() {
        val prompt = exportChatGptPrompt(snapshot, now, TimeZone.of("Europe/Moscow"))
        assertTrue(prompt.contains("Europe/Moscow")); assertTrue(prompt.contains(task.title)); assertTrue(prompt.contains("g1")); assertTrue(prompt.contains(snapshotFingerprint(snapshot)))
        assertFalse(prompt.contains("API-ключ")); assertFalse(prompt.contains("healthSnapshot"))
    }
    @Test fun malformedAndUnsupportedFormatsAreRejected() {
        assertFailsWith<IllegalArgumentException> { parseImportedPlan("не JSON") }
        assertFailsWith<IllegalArgumentException> { parseImportedPlan("x".repeat(1_000_001)) }
        assertFailsWith<IllegalArgumentException> { validateImportedPlan(plan().copy(format = "v2"), snapshot, now) }
    }
}
