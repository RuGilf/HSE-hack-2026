package ru.lifety.feature.health

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import ru.lifety.core.model.HealthSnapshot
import ru.lifety.core.repository.HealthRepository

class HealthControllerTest {
    @Test fun savesOnlyOnExplicitActionAndKeepsNulls() {
        val values = MutableStateFlow<HealthSnapshot?>(null)
        var writes = 0
        val repository = object : HealthRepository {
            override fun observeLatest(): Flow<HealthSnapshot?> = values
            override suspend fun update(snapshot: HealthSnapshot) { writes++; values.value = snapshot }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = HealthController(repository, scope) { 100 }
            assertEquals(0, writes)
            controller.save("7", "30", "")
            assertEquals(1, writes)
            assertEquals(HealthSnapshot(100, 450, null), controller.state.value.snapshot)
            assertTrue(controller.state.value.saved)
            controller.save("-2", "", "")
            assertEquals(1, writes)
            assertNotNull(controller.state.value.error)
        } finally { scope.cancel() }
    }

    @Test fun writeFailuresAreRecoverable() {
        val repository = object : HealthRepository {
            override fun observeLatest(): Flow<HealthSnapshot?> = flowOf(null)
            override suspend fun update(snapshot: HealthSnapshot) { error("private database detail") }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val controller = HealthController(repository, scope) { 100 }
            controller.save("", "", "5000")
            assertFalse(controller.state.value.saving)
            assertFalse(controller.state.value.saved)
            val message = assertNotNull(controller.state.value.error)
            assertFalse(message.contains("private database detail"))
        } finally { scope.cancel() }
    }
}
