package ru.lifety.core.ui

/** Values are density-independent; an upward release cancels a downward drag. */
internal fun shouldDismissSheet(distance: Float, velocity: Float, height: Float): Boolean {
    if (!distance.isFinite() || !velocity.isFinite() || !height.isFinite() || height <= 0f) return false
    if (velocity < -300f) return false
    return distance >= minOf(120f, height * .25f) || (distance >= 24f && velocity >= 900f)
}
