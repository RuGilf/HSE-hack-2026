package ru.lifety.core.security

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlin.test.*

class SectionLockControllerTest {
    private class Platform : SectionLockPlatform {
        var value: String? = null
        var unreadable = false
        var unwritable = false
        var derivations = 0
        var onDerive: (() -> Unit)? = null
        override fun read(): String? { check(!unreadable); return value }
        override fun write(value: String) { check(!unwritable); this.value = value }
        override fun salt() = "test-salt"
        // Test double only. Production uses native salted PBKDF2 and protected storage.
        override fun derive(pin: String, salt: String): String { derivations++; onDerive?.invoke(); return (pin + salt).hashCode().toString() }
    }
    private suspend fun loaded(platform: Platform, scope: CoroutineScope, now: () -> Long = { 1000L }): SectionLockController =
        SectionLockController(platform, scope, Dispatchers.Unconfined, now).also { it.state.first { state -> !state.busy && (state.ready || state.error != null) } }

    @Test fun setupPersistsAndFreshProcessRequiresCodeIncludingAggregatedRoutes() = runBlocking {
        val platform = Platform(); val c = loaded(platform, this)
        c.configure("", "135790", setOf("finance", "tasks"))!!.join()
        assertTrue(c.state.value.unlocked)
        assertFalse(platform.value!!.contains("135790"))
        val restored = loaded(platform, this)
        listOf("finance", "tasks", "calendar", "people", "dashboard", "assistant").forEach { assertTrue(restored.state.value.needsUnlock(it), it) }
        assertFalse(restored.state.value.needsUnlock("health"))
        restored.unlock("135790")!!.join()
        assertFalse(restored.state.value.needsUnlock("finance"))
        restored.lock()
        assertTrue(restored.state.value.needsUnlock("finance"))
    }

    @Test fun incorrectAttemptsAndCooldownSurviveReopeningAndDoNotRunKdfDuringCooldown() = runBlocking {
        var now = 1000L
        val platform = Platform(); var c = loaded(platform, this) { now }
        c.configure("", "135790", setOf("health"))!!.join(); c.lock()
        repeat(5) { c.unlock("000000")!!.join() }
        assertEquals(5, c.state.value.configuration.failures)
        c = loaded(platform, this) { now }
        val calls = platform.derivations
        c.unlock("135790")!!.join()
        assertEquals(calls, platform.derivations)
        assertFalse(c.state.value.unlocked)
        now += 30_001
        c.unlock("135790")!!.join()
        assertTrue(c.state.value.unlocked)
        assertEquals(0, c.state.value.configuration.failures)
    }

    @Test fun changingOrDisablingAlwaysRequiresCurrentCodeEvenInUnlockedSession() = runBlocking {
        val platform = Platform(); val c = loaded(platform, this)
        c.configure("", "135790", setOf("finance"))!!.join()
        c.configure("000000", null, emptySet())!!.join()
        assertTrue(c.state.value.configuration.enabled)
        c.configure("135790", "246802", setOf("tasks"))!!.join()
        assertEquals(setOf("tasks"), c.state.value.configuration.sections)
        c.lock(); c.unlock("135790")!!.join(); assertFalse(c.state.value.unlocked)
        c.unlock("246802")!!.join(); assertTrue(c.state.value.unlocked)
        c.configure("246802", null, emptySet())!!.join()
        assertFalse(c.state.value.configuration.enabled)
        assertFalse(loaded(platform, this).state.value.needsUnlock("tasks"))
    }

    @Test fun backgroundDuringVerificationCannotUnlockTheNextSession() = runBlocking {
        val platform = Platform(); val c = loaded(platform, this)
        c.configure("", "135790", setOf("finance"))!!.join(); c.lock()
        platform.onDerive = { c.lock() }
        c.unlock("135790")!!.join()
        assertFalse(c.state.value.unlocked)
        assertTrue(c.state.value.needsUnlock("finance"))
    }

    @Test fun unavailableOrCorruptStorageFailsClosedAndCanBeRetried() = runBlocking {
        val platform = Platform().apply { unreadable = true }
        val c = loaded(platform, this)
        assertFalse(c.state.value.ready)
        assertTrue(c.state.value.needsUnlock("dashboard"))
        assertNull(c.unlock("135790"))
        platform.unreadable = false; platform.value = "broken"
        c.reload().join(); assertFalse(c.state.value.ready)
        platform.value = Json.encodeToString(LockConfiguration(sections = setOf("unknown"), salt = "s", verifier = "v"))
        c.reload().join(); assertFalse(c.state.value.ready)
        platform.value = null; c.reload().join(); assertTrue(c.state.value.ready)
    }

    @Test fun failedWriteCannotOpenASectionOrRemoveTheExistingProtection() = runBlocking {
        val platform = Platform(); val c = loaded(platform, this)
        c.configure("", "135790", setOf("finance"))!!.join(); c.lock()
        platform.unwritable = true
        c.unlock("135790")!!.join()
        assertFalse(c.state.value.ready); assertFalse(c.state.value.unlocked)
        platform.unwritable = false; c.reload().join()
        assertTrue(c.state.value.needsUnlock("finance"))
        assertEquals(setOf("finance"), c.state.value.configuration.sections)
    }

    @Test fun onlyAssistantProtectionDoesNotBlockOtherSectionsAndNoCodeMeansNoBackgroundReset() = runBlocking {
        val c = loaded(Platform(), this)
        val epoch = c.state.value.epoch; c.lock(); assertEquals(epoch, c.state.value.epoch)
        c.configure("", "135790", setOf("assistant"))!!.join(); c.lock()
        assertTrue(c.state.value.needsUnlock("assistant"))
        assertFalse(c.state.value.needsUnlock("dashboard"))
        assertFalse(c.state.value.needsUnlock("tasks"))
    }
}
