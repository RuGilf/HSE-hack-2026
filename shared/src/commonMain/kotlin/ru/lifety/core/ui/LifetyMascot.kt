package ru.lifety.core.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Emotions describe presentation only; the mascot never invents health or AI data. */
enum class LifetyEmotion(val description: String) {
    Happy("радуется"), Calm("спокоен"), Focused("сосредоточен"),
    Celebrating("празднует"), Sleepy("отдыхает");
}

/** Code-native vector mascot, reusable on Android/iOS with no bitmap or platform asset. */
@Composable
fun LifetyMascot(
    modifier: Modifier = Modifier,
    emotion: LifetyEmotion = LifetyEmotion.Happy,
    animated: Boolean = true,
) {
    val accent = MaterialTheme.colorScheme.primary
    // Read animation only in the graphics layer: no composition/drawing at every frame.
    val breath: State<Float> = if (animated) {
        rememberInfiniteTransition(label = "lifetyBreath").animateFloat(0f, 1f,
            infiniteRepeatable(tween(2600), RepeatMode.Reverse), label = "lifetyBreathValue")
    } else remember { mutableStateOf(0.5f) }
    Canvas(modifier.graphicsLayer {
        translationY = -breath.value * 3.dp.toPx()
        scaleX = 0.98f + breath.value * 0.02f
        scaleY = scaleX
    }.semantics { contentDescription = "Lifety, ${emotion.description}" }) {
        val unit = size.minDimension
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = unit * 0.285f
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.18f), Color.Transparent), center, unit * 0.5f), unit * 0.5f, center)
        drawCircle(accent.copy(alpha = 0.06f), radius, center, style = Stroke(unit * 0.10f))
        drawCircle(accent.copy(alpha = 0.16f), radius, center, style = Stroke(unit * 0.062f))
        drawCircle(accent, radius, center, style = Stroke(unit * 0.035f))
        val mouth = Path()
        when (emotion) {
            LifetyEmotion.Focused -> {
                mouth.moveTo(center.x - unit * .09f, center.y + unit * .09f)
                mouth.quadraticTo(center.x, center.y + unit * .07f, center.x + unit * .09f, center.y + unit * .09f)
                for (x in listOf(-.09f, .09f)) drawCircle(accent, unit * .015f, Offset(center.x + unit * x, center.y - unit * .035f))
            }
            LifetyEmotion.Sleepy -> {
                mouth.moveTo(center.x - unit * .055f, center.y + unit * .10f)
                mouth.lineTo(center.x + unit * .055f, center.y + unit * .10f)
                for (x in listOf(-.10f, .10f)) drawLine(accent, Offset(center.x + unit * (x - .025f), center.y), Offset(center.x + unit * (x + .025f), center.y), unit * .02f, StrokeCap.Round)
            }
            else -> {
                val depth = if (emotion == LifetyEmotion.Calm) .105f else .16f
                mouth.moveTo(center.x - unit * .12f, center.y + unit * .035f)
                mouth.cubicTo(center.x - unit * .085f, center.y + unit * depth, center.x + unit * .085f, center.y + unit * depth, center.x + unit * .12f, center.y + unit * .035f)
            }
        }
        drawPath(mouth, accent, style = Stroke(unit * .035f, cap = StrokeCap.Round))
        if (emotion == LifetyEmotion.Celebrating) {
            for (offset in listOf(Offset(-.36f, -.23f), Offset(.37f, -.18f), Offset(.32f, .30f))) {
                val p = center + Offset(offset.x * unit, offset.y * unit)
                drawLine(accent, p - Offset(unit * .025f, 0f), p + Offset(unit * .025f, 0f), unit * .012f, StrokeCap.Round)
                drawLine(accent, p - Offset(0f, unit * .025f), p + Offset(0f, unit * .025f), unit * .012f, StrokeCap.Round)
            }
        }
    }
}
