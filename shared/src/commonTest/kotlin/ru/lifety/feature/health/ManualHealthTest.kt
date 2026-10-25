package ru.lifety.feature.health

import kotlin.test.*

class ManualHealthTest {
    @Test fun acceptsPartialSnapshotsAndZero() {
        val sleep = manualSnapshot("7", "30", "", 100)
        assertEquals(450, sleep.sleepMinutes)
        assertNull(sleep.steps)
        assertEquals(100L, sleep.timestamp)
        val activity = manualSnapshot("", "", "0", 101)
        assertNull(activity.sleepMinutes)
        assertEquals(0, activity.steps)
    }

    @Test fun rejectsInvalidInput() {
        listOf(Triple("", "", ""), Triple("-1", "", ""), Triple("24", "1", ""),
            Triple("0", "60", ""), Triple("7.5", "", ""), Triple("", "", "200001"), Triple("", "", "99999999999999999"))
            .forEach { (h, m, s) -> assertFailsWith<IllegalArgumentException> { manualSnapshot(h, m, s, 0) } }
    }
}
