package ru.lifety.feature.health

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.test.*
import kotlinx.coroutines.runBlocking

private class AggregateFixture : HealthConnectApi {
    var granted = HealthMetric.entries.toSet()
    var stepValue: Long? = 8000
    var sleepValue: Duration? = Duration.ofMinutes(450)
    var stepReads = 0
    var sleepReads = 0
    var revokeSleepAfterSteps = false
    var denyInProvider = false
    val ranges = mutableListOf<Pair<Instant, Instant>>()
    override suspend fun availability(): HealthConnectAvailability = HealthConnectAvailability.AVAILABLE
    override suspend fun grantedMetrics(): Set<HealthMetric> = granted
    override suspend fun steps(start: Instant, end: Instant): Long? {
        stepReads++; ranges += start to end
        if (denyInProvider) throw SecurityException("private provider message")
        if (revokeSleepAfterSteps) granted = setOf(HealthMetric.STEPS)
        return stepValue
    }
    override suspend fun sleep(start: Instant, end: Instant): Duration? {
        sleepReads++; ranges += start to end
        return sleepValue
    }
}

class AndroidHealthConnectGatewayTest {
    private val now = Instant.parse("2026-10-25T12:00:00Z").toEpochMilli()
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test fun dayWindowRespectsLocalTimezoneAndDst() {
        val range = healthReadWindow(now, berlin)
        assertEquals(Instant.parse("2026-10-24T22:00:00Z"), range.stepsStart)
        assertEquals(Duration.ofHours(14), Duration.between(range.stepsStart, range.capturedAt))
        assertEquals(Duration.ofHours(24), Duration.between(range.sleepStart, range.capturedAt))
    }

    @Test fun mapsRealUnitsAndPreservesUnknownValues() = runBlocking {
        val api = AggregateFixture()
        val gateway = AndroidHealthConnectGateway(api) { berlin }
        val result = gateway.readSnapshot(HealthMetric.entries.toSet(), now)
        assertEquals(450, result.sleepMinutes)
        assertEquals(8000, result.steps)
        assertEquals(now, result.timestamp)
        assertEquals(1, api.stepReads)
        assertEquals(1, api.sleepReads)
        api.stepValue = null; api.sleepValue = null
        val empty = gateway.readSnapshot(HealthMetric.entries.toSet(), now)
        assertNull(empty.steps)
        assertNull(empty.sleepMinutes)
    }

    @Test fun onlyRequestedMetricsAreAggregated() = runBlocking {
        val api = AggregateFixture()
        val result = AndroidHealthConnectGateway(api) { berlin }.readSnapshot(setOf(HealthMetric.STEPS), now)
        assertNull(result.sleepMinutes)
        assertEquals(1, api.stepReads)
        assertEquals(0, api.sleepReads)
    }

    @Test fun checksPermissionImmediatelyBeforeEachAggregate() = runBlocking {
        val api = AggregateFixture()
        api.revokeSleepAfterSteps = true
        try { AndroidHealthConnectGateway(api) { berlin }.readSnapshot(HealthMetric.entries.toSet(), now); fail("Expected revoked sleep permission") }
        catch (_: HealthPermissionRevoked) { }
        assertEquals(1, api.stepReads)
        assertEquals(0, api.sleepReads)
    }

    @Test fun deniedMetricsAreNotReadAndProviderSecurityErrorsAreMapped() = runBlocking {
        val api = AggregateFixture()
        val gateway = AndroidHealthConnectGateway(api) { berlin }
        api.granted = emptySet()
        try { gateway.readSnapshot(setOf(HealthMetric.STEPS), now); fail("Expected denied access") }
        catch (_: HealthPermissionRevoked) { }
        assertEquals(0, api.stepReads)
        api.granted = setOf(HealthMetric.STEPS); api.denyInProvider = true
        try { gateway.readSnapshot(setOf(HealthMetric.STEPS), now); fail("Expected revoked access") }
        catch (failure: HealthPermissionRevoked) { assertFalse(failure.toString().contains("private provider message")) }
    }

    @Test fun midnightDoesNotRequestAnEmptyStepRange() = runBlocking {
        val api = AggregateFixture()
        val midnight = Instant.parse("2026-10-24T22:00:00Z").toEpochMilli()
        val result = AndroidHealthConnectGateway(api) { berlin }.readSnapshot(setOf(HealthMetric.STEPS), midnight)
        assertNull(result.steps)
        assertEquals(0, api.stepReads)
    }

    @Test fun invalidSourceValuesNeverOverflowOrBecomeFakeZeros() = runBlocking {
        val api = AggregateFixture()
        val gateway = AndroidHealthConnectGateway(api) { berlin }
        for (value in listOf(-1L, Int.MAX_VALUE.toLong() + 1)) {
            api.stepValue = value
            try { gateway.readSnapshot(setOf(HealthMetric.STEPS), now); fail("Expected invalid steps") }
            catch (_: IllegalArgumentException) { }
        }
        for (value in listOf(Duration.ofMinutes(-1), Duration.ofHours(25))) {
            api.sleepValue = value
            try { gateway.readSnapshot(setOf(HealthMetric.SLEEP), now); fail("Expected invalid sleep") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
