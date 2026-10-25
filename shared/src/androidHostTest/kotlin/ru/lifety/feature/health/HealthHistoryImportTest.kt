package ru.lifety.feature.health

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.lifety.core.model.HealthSnapshot
import ru.lifety.core.repository.HealthRepository
import java.time.*
import kotlin.test.*

private class DailyApi : HealthConnectApi {
    var granted = HealthMetric.entries.toSet()
    var calls = 0
    var revoke = false
    var requested = emptySet<HealthMetric>()
    var start: LocalDateTime? = null
    var rows = listOf(DailyHealthValue(LocalDate.of(2026, 10, 24), Duration.ofMinutes(450), 8000),
        DailyHealthValue(LocalDate.of(2026, 10, 25), null, 0))
    override suspend fun availability() = HealthConnectAvailability.AVAILABLE
    override suspend fun grantedMetrics() = granted
    override suspend fun steps(start: Instant, end: Instant): Long? = error("Must use grouped query")
    override suspend fun sleep(start: Instant, end: Instant): Duration? = error("Must use grouped query")
    override suspend fun daily(metrics: Set<HealthMetric>, start: LocalDateTime, end: LocalDateTime): List<DailyHealthValue> {
        calls++; requested = metrics; this.start = start
        if (revoke) granted = emptySet()
        return rows
    }
}

class HealthHistoryImportTest {
    private val now = Instant.parse("2026-10-25T12:00:00Z").toEpochMilli()
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test fun groupedQueryUsesLocalCalendarDaysAndStableKeysForFinishedDays(): Unit = runBlocking {
        val api = DailyApi()
        val gateway = AndroidHealthConnectGateway(api) { berlin }
        val rows = gateway.readHistory(HealthMetric.entries.toSet(), now, 30)
        assertEquals(1, api.calls)
        assertEquals(LocalDateTime.of(2026, 9, 26, 0, 0), api.start)
        assertEquals(Instant.parse("2026-10-23T22:00:00Z").toEpochMilli(), rows.first().timestamp)
        assertEquals(450, rows.first().sleepMinutes)
        assertEquals(0, rows.last().steps)
        assertNull(rows.last().sleepMinutes)
        assertEquals(now, rows.last().timestamp)
        assertEquals(rows.first(), gateway.readHistory(HealthMetric.entries.toSet(), now + 1000, 30).first())
    }

    @Test fun onlyGrantedMetricsAreReadAndRevocationInvalidatesTheWholePreview(): Unit = runBlocking {
        val api = DailyApi()
        api.granted = setOf(HealthMetric.STEPS)
        val gateway = AndroidHealthConnectGateway(api) { berlin }
        assertTrue(gateway.readHistory(setOf(HealthMetric.STEPS), now, 30).all { it.sleepMinutes == null })
        assertEquals(setOf(HealthMetric.STEPS), api.requested)
        api.revoke = true
        assertFailsWith<HealthPermissionRevoked> { gateway.readHistory(setOf(HealthMetric.STEPS), now, 30) }
    }

    @Test fun absentBucketsStayAbsentAndInvalidValuesNeverBecomeZero(): Unit = runBlocking {
        val api = DailyApi()
        val gateway = AndroidHealthConnectGateway(api) { berlin }
        api.rows = emptyList()
        assertTrue(gateway.readHistory(HealthMetric.entries.toSet(), now, 30).isEmpty())
        api.rows = listOf(DailyHealthValue(LocalDate.of(2026, 10, 24), Duration.ofMinutes(450), -1))
        assertFailsWith<IllegalArgumentException> { gateway.readHistory(HealthMetric.entries.toSet(), now, 30) }
        api.rows = listOf(DailyHealthValue(LocalDate.of(2026, 10, 24), Duration.ofMinutes(450), Int.MAX_VALUE.toLong() + 1))
        assertFailsWith<IllegalArgumentException> { gateway.readHistory(HealthMetric.entries.toSet(), now, 30) }
    }

    @Test fun historyRequiresConfirmationAndPreservesUnselectedValuesAndOlderLocalRows(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val original = listOf(HealthSnapshot(1, 400, 100), HealthSnapshot(10, 450, 200))
        val local = MutableStateFlow(original)
        var batches = 0; var reads = 0
        val repo = object : HealthRepository {
            override fun observeLatest() = local.map { it.maxByOrNull { row -> row.timestamp } }
            override fun observeAll() = local
            override suspend fun update(snapshot: HealthSnapshot) { error("Use atomic batch") }
            override suspend fun updateAll(snapshots: List<HealthSnapshot>) {
                batches++; local.value = local.value.filterNot { old -> snapshots.any { it.timestamp == old.timestamp } } + snapshots
            }
        }
        var rows = listOf(HealthSnapshot(10, null, 8000), HealthSnapshot(20, 480, 0))
        val gateway = object : HealthConnectGateway {
            override suspend fun availability() = HealthConnectAvailability.AVAILABLE
            override suspend fun grantedMetrics() = HealthMetric.entries.toSet()
            override suspend fun readSnapshot(metrics: Set<HealthMetric>, capturedAt: Long): HealthSnapshot = error("Use history")
            override suspend fun readHistory(metrics: Set<HealthMetric>, capturedAt: Long, days: Int): List<HealthSnapshot> { reads++; return rows }
        }
        try {
            val controller = HealthConnectController(gateway, repo, scope) { now }
            controller.refresh()
            assertEquals(0, reads)
            controller.readHistoryPreview()
            assertEquals(0, batches)
            assertEquals(2, controller.state.value.preview!!.snapshots.size)
            controller.savePreview()
            assertEquals(1, batches)
            assertEquals(3, local.value.size)
            assertEquals(450, local.value.first { it.timestamp == 10L }.sleepMinutes)
            assertEquals(original.first(), local.value.first { it.timestamp == 1L })
            controller.readHistoryPreview(); controller.savePreview()
            assertEquals(3, local.value.size)
            rows = emptyList()
            controller.readHistoryPreview(); controller.savePreview()
            assertNull(controller.state.value.preview)
            assertEquals(2, batches)
            rows = listOf(HealthSnapshot(30, 500, 100))
            controller.readHistoryPreview()
            local.value = local.value.map { if (it.timestamp == 1L) it.copy(steps = 999) else it }
            controller.savePreview()
            assertEquals(2, batches) // Detect changes in older rows, even if latest is unchanged.
            assertNull(controller.state.value.preview)
            assertNotNull(controller.state.value.error)
        } finally { scope.cancel() }
    }
}
