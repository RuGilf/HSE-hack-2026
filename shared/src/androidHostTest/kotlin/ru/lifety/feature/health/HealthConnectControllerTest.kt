package ru.lifety.feature.health

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.lifety.core.model.HealthSnapshot
import ru.lifety.core.repository.HealthRepository

private class ImportFixture {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val local = MutableStateFlow<HealthSnapshot?>(HealthSnapshot(10, 420, 100))
    var writes = 0
    var reads = 0
    var requested = emptySet<HealthMetric>()
    var availability = HealthConnectAvailability.AVAILABLE
    var granted = HealthMetric.entries.toSet()
    var data = HealthSnapshot(100, 450, 8000)
    var failure: Exception? = null
    var writeFailure = false
    var holdRead = false
    var cancelled = false
    val gateway = object : HealthConnectGateway {
        override suspend fun availability(): HealthConnectAvailability = this@ImportFixture.availability
        override suspend fun grantedMetrics(): Set<HealthMetric> = granted
        override suspend fun readSnapshot(metrics: Set<HealthMetric>, capturedAt: Long): HealthSnapshot {
            reads++; requested = metrics
            failure?.let { throw it }
            if (holdRead) try { awaitCancellation() } finally { cancelled = true }
            return data.copy(timestamp = capturedAt)
        }
    }
    val repository = object : HealthRepository {
        override fun observeLatest(): Flow<HealthSnapshot?> = local
        override suspend fun update(snapshot: HealthSnapshot) {
            if (writeFailure) error("private database details")
            writes++; local.value = snapshot
        }
    }
    val controller = HealthConnectController(gateway, repository, scope) { 100 }
}

private fun importTest(block: ImportFixture.() -> Unit) {
    val fixture = ImportFixture()
    try { fixture.block() } finally { fixture.scope.cancel() }
}

class HealthConnectControllerTest {
    @Test fun entryAndResumeNeverReadMeasurementsOrWrite() = importTest {
        assertEquals(0, reads)
        controller.refresh(); controller.refresh()
        assertEquals(HealthConnectAvailability.AVAILABLE, controller.state.value.availability)
        assertEquals(granted, controller.state.value.granted)
        assertEquals(0, reads)
        assertEquals(0, writes)
    }

    @Test fun onlyExplicitConfirmationSavesExactPreview() = importTest {
        controller.readPreview()
        assertEquals(1, reads)
        assertEquals(0, writes)
        assertEquals(data, controller.state.value.preview?.snapshot)
        controller.savePreview()
        assertEquals(1, writes)
        assertEquals(data, local.value)
        assertNull(controller.state.value.preview)
        controller.savePreview()
        assertEquals(1, writes)
    }

    @Test fun deniedPermissionsNeverReachHealthReads() = importTest {
        granted = emptySet()
        controller.readPreview(); controller.savePreview()
        assertEquals(0, reads)
        assertEquals(0, writes)
        assertNull(controller.state.value.preview)
        assertNotNull(controller.state.value.error)
    }

    @Test fun partialGrantOnlyReadsGrantedSelectedMetrics() = importTest {
        granted = setOf(HealthMetric.STEPS)
        controller.readPreview()
        assertEquals(setOf(HealthMetric.STEPS), requested)
        assertNull(controller.state.value.preview?.snapshot?.sleepMinutes)
        assertEquals(8000, controller.state.value.preview?.snapshot?.steps)
    }

    @Test fun deselectedCategoryNeverReachesGatewayAndInvalidatesPreview() = importTest {
        controller.readPreview()
        controller.select(HealthMetric.SLEEP, false)
        assertNull(controller.state.value.preview)
        controller.readPreview()
        assertEquals(setOf(HealthMetric.STEPS), requested)
        controller.select(HealthMetric.STEPS, false)
        controller.readPreview()
        assertEquals(2, reads)
    }

    @Test fun noDataDoesNotReplaceExistingManualSnapshot() = importTest {
        val original = local.value
        data = HealthSnapshot(100, null, null)
        controller.readPreview(); controller.savePreview()
        assertEquals(0, writes)
        assertEquals(original, local.value)
        assertNull(controller.state.value.preview)
        assertNotNull(controller.state.value.message)
    }

    @Test fun explicitZeroRemainsKnownZero() = importTest {
        data = HealthSnapshot(100, 0, 0)
        controller.readPreview(); controller.savePreview()
        assertEquals(1, writes)
        assertEquals(data, local.value)
    }

    @Test fun revocationBeforeSaveOrOnResumeInvalidatesPreview() = importTest {
        controller.readPreview()
        granted = emptySet()
        controller.savePreview()
        assertEquals(0, writes)
        assertNull(controller.state.value.preview)
        granted = HealthMetric.entries.toSet()
        controller.readPreview()
        granted = emptySet()
        controller.refresh()
        assertNull(controller.state.value.preview)
    }

    @Test fun changedLocalRecordRequiresNewPreview() = importTest {
        controller.readPreview()
        val newer = HealthSnapshot(101, 480, 9000)
        local.value = newer
        controller.savePreview()
        assertEquals(0, writes)
        assertEquals(newer, local.value)
        assertNull(controller.state.value.preview)
        assertNotNull(controller.state.value.error)
    }

    @Test fun failedReadsAndWritesAreRecoverableAndSanitized() = importTest {
        failure = IllegalStateException("private provider detail")
        controller.readPreview()
        assertFalse(controller.state.value.busy)
        assertFalse(controller.state.value.error.orEmpty().contains("private provider detail"))
        failure = null
        controller.readPreview()
        writeFailure = true
        controller.savePreview()
        assertFalse(controller.state.value.busy)
        assertEquals(0, writes)
        assertNotNull(controller.state.value.preview)
        assertFalse(controller.state.value.error.orEmpty().contains("private database details"))
        writeFailure = false
        controller.savePreview()
        assertEquals(1, writes)
    }

    @Test fun unavailableProviderDoesNotReadOrSave() = importTest {
        availability = HealthConnectAvailability.INSTALL_REQUIRED
        controller.readPreview(); controller.savePreview()
        assertEquals(0, reads)
        assertEquals(0, writes)
        assertEquals(availability, controller.state.value.availability)
    }

    @Test fun revocationDuringReadClearsPreviewAndPermissionState() = importTest {
        controller.refresh()
        failure = HealthPermissionRevoked()
        controller.readPreview()
        assertEquals(0, writes)
        assertNull(controller.state.value.preview)
        assertTrue(controller.state.value.granted.isEmpty())
        assertNotNull(controller.state.value.error)
    }

    @Test fun clockRollbackCannotCreateAnInvisibleOlderImport() = importTest {
        val original = HealthSnapshot(200, 420, 100)
        local.value = original
        controller.readPreview(); controller.savePreview()
        assertEquals(0, writes)
        assertEquals(original, local.value)
        assertNull(controller.state.value.preview)
        assertNotNull(controller.state.value.error)
    }

    @Test fun cancellationDoesNotBecomeAnErrorOrWrite() = importTest {
        holdRead = true
        controller.readPreview()
        assertTrue(controller.state.value.busy)
        scope.cancel()
        assertTrue(cancelled)
        assertFalse(controller.state.value.busy)
        assertNull(controller.state.value.error)
        assertEquals(0, writes)
    }
}
