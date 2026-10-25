package ru.lifety.core.ui

import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment

/** Brief motion for feedback and orientation; business actions never wait for it. */
object LifetyMotion {
    const val Quick = 120
    const val Standard = 180
    const val Emphasis = 240
    val RevealEnter = fadeIn(tween(Standard)) + expandVertically(tween(Standard), expandFrom = Alignment.Top)
    val RevealExit = fadeOut(tween(Quick)) + shrinkVertically(tween(Standard), shrinkTowards = Alignment.Top)
}
