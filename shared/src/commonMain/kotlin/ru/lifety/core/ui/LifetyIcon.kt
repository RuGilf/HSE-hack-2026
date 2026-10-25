package ru.lifety.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

enum class LifetySymbol { Home, Plan, Wallet, Heart, Assistant, Settings, Calendar, Plus, Close, Back }

@Composable
fun LifetyIcon(symbol: LifetySymbol, modifier: Modifier = Modifier, color: Color = LocalContentColor.current) {
    Canvas(modifier.size(24.dp)) {
        val pen = Stroke(width = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        withTransform({ scale(size.width / 24f, size.height / 24f, Offset.Zero) }) {
            val path = Path()
            when (symbol) {
                LifetySymbol.Home -> {
                    path.moveTo(3f, 10f); path.lineTo(12f, 3f); path.lineTo(21f, 10f); path.lineTo(21f, 21f)
                    path.lineTo(15f, 21f); path.lineTo(15f, 14f); path.lineTo(9f, 14f); path.lineTo(9f, 21f); path.lineTo(3f, 21f); path.close()
                }
                LifetySymbol.Plan, LifetySymbol.Calendar -> {
                    drawRoundRect(color, Offset(4f, 5f), Size(16f, 16f), CornerRadius(3f), style = pen)
                    drawLine(color, Offset(8f, 3f), Offset(8f, 7f), 1.7f, StrokeCap.Round)
                    drawLine(color, Offset(16f, 3f), Offset(16f, 7f), 1.7f, StrokeCap.Round)
                    drawLine(color, Offset(4f, 10f), Offset(20f, 10f), 1.7f)
                    if (symbol == LifetySymbol.Plan) { path.moveTo(8f, 15f); path.lineTo(11f, 18f); path.lineTo(16f, 13f) }
                }
                LifetySymbol.Wallet -> {
                    drawRoundRect(color, Offset(3f, 5f), Size(18f, 15f), CornerRadius(3f), style = pen)
                    drawRoundRect(color, Offset(15f, 10f), Size(7f, 6f), CornerRadius(2f), style = pen)
                    drawCircle(color, 1f, Offset(18f, 13f))
                    drawLine(color, Offset(7f, 3f), Offset(18f, 3f), 1.7f, StrokeCap.Round)
                }
                LifetySymbol.Heart -> {
                    path.moveTo(12f, 21f)
                    path.cubicTo(9f, 18f, 2f, 13f, 3f, 8f)
                    path.cubicTo(4f, 2f, 10f, 2f, 12f, 6f)
                    path.cubicTo(14f, 2f, 20f, 2f, 21f, 8f)
                    path.cubicTo(22f, 13f, 15f, 18f, 12f, 21f)
                }
                LifetySymbol.Assistant -> {
                    path.moveTo(5f, 4f); path.lineTo(19f, 4f); path.quadraticTo(22f, 4f, 22f, 7f)
                    path.lineTo(22f, 16f); path.quadraticTo(22f, 19f, 19f, 19f)
                    path.lineTo(10f, 19f); path.lineTo(5f, 22f); path.lineTo(5f, 19f)
                    path.quadraticTo(2f, 19f, 2f, 16f); path.lineTo(2f, 7f); path.quadraticTo(2f, 4f, 5f, 4f)
                    drawCircle(color, 1f, Offset(8f, 11f)); drawCircle(color, 1f, Offset(16f, 11f))
                    drawLine(color, Offset(9f, 15f), Offset(15f, 15f), 1.7f, StrokeCap.Round)
                }
                LifetySymbol.Settings -> {
                    drawCircle(color, 6f, Offset(12f, 12f), style = pen)
                    drawCircle(color, 2f, Offset(12f, 12f), style = pen)
                    repeat(8) { i ->
                        val angle = i * kotlin.math.PI / 4
                        drawLine(color, Offset(12f + cos(angle).toFloat() * 8f, 12f + sin(angle).toFloat() * 8f), Offset(12f + cos(angle).toFloat() * 10f, 12f + sin(angle).toFloat() * 10f), 1.7f, StrokeCap.Round)
                    }
                }
                LifetySymbol.Plus -> { path.moveTo(12f, 5f); path.lineTo(12f, 19f); path.moveTo(5f, 12f); path.lineTo(19f, 12f) }
                LifetySymbol.Close -> { path.moveTo(6f, 6f); path.lineTo(18f, 18f); path.moveTo(18f, 6f); path.lineTo(6f, 18f) }
                LifetySymbol.Back -> { path.moveTo(15f, 5f); path.lineTo(8f, 12f); path.lineTo(15f, 19f) }
            }
            drawPath(path, color, style = pen)
        }
    }
}
