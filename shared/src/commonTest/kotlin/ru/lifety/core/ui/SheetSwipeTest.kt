package ru.lifety.core.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SheetSwipeTest {
    @Test fun tapOrSmallMovementNeverClosesSheet() {
        assertFalse(shouldDismissSheet(0f, 2000f, 700f))
        assertFalse(shouldDismissSheet(23f, 2000f, 700f))
        assertFalse(shouldDismissSheet(70f, 0f, 700f))
    }
    @Test fun deliberateDistanceOrDownwardFlickClosesSheet() {
        assertTrue(shouldDismissSheet(120f, 0f, 700f))
        assertTrue(shouldDismissSheet(50f, 0f, 200f))
        assertTrue(shouldDismissSheet(24f, 900f, 700f))
    }
    @Test fun reversingDirectionOrInvalidGeometryDoesNotDismiss() {
        assertFalse(shouldDismissSheet(140f, -500f, 700f))
        assertFalse(shouldDismissSheet(140f, 0f, 0f))
        assertFalse(shouldDismissSheet(Float.NaN, 0f, 700f))
    }
}
