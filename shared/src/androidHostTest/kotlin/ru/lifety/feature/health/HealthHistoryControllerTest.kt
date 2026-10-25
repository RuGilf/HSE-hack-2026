package ru.lifety.feature.health

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.*
import kotlin.time.Instant
import kotlin.test.*
import ru.lifety.core.model.HealthSnapshot
import ru.lifety.core.repository.HealthRepository
import ru.lifety.core.time.localDate

class HealthHistoryControllerTest {
    @Test fun observesOlderRowsAndAllowsDatedManualRecordsWithoutCreatingFutureMeasurements() {
        val now = Instant.parse("2026-10-25T12:00:00Z").toEpochMilliseconds()
        val local = MutableStateFlow(listOf(HealthSnapshot(now, 420, 1000)))
        var writes = 0
        val repo = object : HealthRepository {
            override fun observeAll() = local
            override fun observeLatest() = local.map { it.maxByOrNull { row -> row.timestamp } }
            override suspend fun update(snapshot: HealthSnapshot) { writes++; local.value = local.value + snapshot }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = HealthController(repo, scope) { now }
            local.value = local.value + HealthSnapshot(now - 86400000, 480, 8000)
            assertEquals(2, controller.state.value.history.size)
            assertEquals(now, controller.state.value.snapshot!!.timestamp)
            val today = localDate(now)
            val past = today.plus(DatePeriod(days = -7))
            controller.save("7", "30", "9000", past)
            assertEquals(1, writes)
            assertEquals(past, localDate(local.value.last().timestamp))
            assertEquals(450, local.value.last().sleepMinutes)
            controller.save("8", "", "", today.plus(DatePeriod(days = 1)))
            assertEquals(1, writes)
            assertNotNull(controller.state.value.error)
        } finally { scope.cancel() }
    }
}
