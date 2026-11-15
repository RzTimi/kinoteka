package com.kinoteka.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

private val LeafDark = Color(0xFF1E4D24)
private val LeafMid = Color(0xFF2F7A35)
private val LeafLight = Color(0xFF4E9F45)
private val Vein = Color(0x66C8E6A0)
private val Vine = Color(0xFF3B5E2B)

/**
 * Листья джунглей для шапки: свисающая лиана с листочками справа и пара листьев слева.
 * Рисуется только в теме «Джунглі», полупрозрачно, чтобы не мешать тексту.
 */
@Composable
fun JungleLeaves(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val u = h / 4f // базовая единица размера листа

        // Справа: лиана сверху с тремя листьями.
        val vine = Path().apply {
            moveTo(w * 0.62f, 0f)
            cubicTo(w * 0.70f, h * 0.35f, w * 0.80f, h * 0.10f, w * 0.88f, h * 0.45f)
            cubicTo(w * 0.92f, h * 0.65f, w * 0.97f, h * 0.55f, w, h * 0.7f)
        }
        drawPath(vine, Vine.copy(alpha = 0.8f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        leaf(Offset(w * 0.68f, h * 0.22f), u * 1.1f, 35f, LeafMid)
        leaf(Offset(w * 0.80f, h * 0.18f), u * 1.3f, -25f, LeafLight)
        leaf(Offset(w * 0.90f, h * 0.55f), u * 1.2f, 60f, LeafDark)
        leaf(Offset(w * 0.97f, h * 0.15f), u * 1.6f, 160f, LeafMid)

        // Слева: два больших листа выглядывают из угла.
        leaf(Offset(w * 0.02f, h * 0.10f), u * 1.8f, 40f, LeafDark)
        leaf(Offset(w * 0.07f, -h * 0.05f), u * 1.4f, 75f, LeafMid)
    }
}

/** Один лист: заострённый овал с прожилкой, повёрнутый на [angle] градусов от основания. */
private fun DrawScope.leaf(base: Offset, length: Float, angle: Float, color: Color) {
    rotate(angle, pivot = base) {
        val tip = Offset(base.x + length * 2.2f, base.y)
        val width = length * 0.55f
        val path = Path().apply {
            moveTo(base.x, base.y)
            cubicTo(base.x + length * 0.6f, base.y - width, tip.x - length * 0.6f, base.y - width * 0.9f, tip.x, tip.y)
            cubicTo(tip.x - length * 0.6f, base.y + width * 0.9f, base.x + length * 0.6f, base.y + width, base.x, base.y)
            close()
        }
        drawPath(path, color.copy(alpha = 0.85f))
        // Центральная прожилка.
        drawLine(Vein, base, Offset(tip.x - length * 0.2f, tip.y), strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Round)
    }
}
