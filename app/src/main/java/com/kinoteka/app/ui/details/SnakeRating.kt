package com.kinoteka.app.ui.details

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

private val SnakeGreen = Color(0xFF43A047)
private val SnakeDark = Color(0xFF2E7D32)
private val SnakeBelly = Color(0xFFA5D6A7)
private val SnakeSpot = Color(0xFF1B5E20)
private val Tongue = Color(0xFFE53935)

private const val SLOTS = 10
/** Запас справа (в долях деления), чтобы язык не упирался в край. */
private const val RIGHT_SPACE = 0.45f

/**
 * Оценка-змейка: 10 делений, змейка растёт до выбранной оценки, голова — на конце.
 * Нажатие или проведение пальцем ставит оценку; повторное нажатие на голову снимает её.
 */
@Composable
fun SnakeRating(rating: Int?, onRate: (Int) -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val currentRating by rememberUpdatedState(rating)
    val ghost = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(60.dp)
            .semantics { contentDescription = rating?.let { "Моя оцінка: $it з 10" } ?: "Оцінка не поставлена" }
            .pointerInput(Unit) {
                detectTapGestures { pos ->
                    val slot = slotAt(pos.x, size.width.toFloat())
                    if (slot == currentRating) onClear() else onRate(slot)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    val slot = slotAt(change.position.x, size.width.toFloat())
                    if (slot != currentRating) onRate(slot)
                }
            }
    ) {
        val slotW = size.width / (SLOTS + RIGHT_SPACE)
        val centers = (1..SLOTS).map { i -> Offset(slotW * (i - 0.5f), size.height / 2 + waveY(i, size.height)) }

        // Призрачные точки: показывают, куда змейка может вырасти.
        for (i in 1..SLOTS) {
            if (rating == null || i > rating) drawCircle(ghost, radius = slotW * 0.13f, center = centers[i - 1])
        }
        if (rating == null) {
            drawHead(centers[0], slotW, ghost, faded = true)
            return@Canvas
        }

        val n = rating
        if (n >= 2) {
            // Тело — плавная кривая через центры делений, утолщается от хвоста к голове.
            val samples = sampleSpline(centers.subList(0, n), stepsPerSegment = 14)
            val maxR = slotW * 0.27f
            val minR = slotW * 0.09f
            val radii = samples.indices.map { k -> minR + (maxR - minR) * (k.toFloat() / (samples.size - 1)) }
            val outline = 1.6.dp.toPx()
            samples.forEachIndexed { k, p -> drawCircle(SnakeDark, radii[k] + outline, p) }
            samples.forEachIndexed { k, p -> drawCircle(SnakeGreen, radii[k], p) }
            // Пятнышки на спине — по одному на деление.
            for (i in 1 until n) {
                val k = ((i - 1) * 14).coerceAtMost(samples.size - 1)
                drawCircle(SnakeSpot.copy(alpha = 0.5f), radii[k] * 0.38f, samples[k] - Offset(0f, radii[k] * 0.15f))
            }
        }
        drawHead(centers[n - 1], slotW, SnakeGreen, faded = false)
    }
}

private fun slotAt(x: Float, width: Float): Int =
    ((x / (width / (SLOTS + RIGHT_SPACE))).toInt() + 1).coerceIn(1, SLOTS)

/** Волна тела: змейка извивается. */
private fun waveY(i: Int, height: Float): Float = sin(i * 1.05 + PI / 3).toFloat() * height * 0.17f

/** Точки сплайна Катмулла–Рома через [points] — для плавного тела без изломов. */
private fun sampleSpline(points: List<Offset>, stepsPerSegment: Int): List<Offset> {
    if (points.size < 2) return points
    val out = ArrayList<Offset>()
    for (i in 0 until points.size - 1) {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[(i + 2).coerceAtMost(points.size - 1)]
        for (s in 0 until stepsPerSegment) {
            val t = s.toFloat() / stepsPerSegment
            val t2 = t * t
            val t3 = t2 * t
            fun c(a: Float, b: Float, cc: Float, d: Float) =
                0.5f * (2 * b + (-a + cc) * t + (2 * a - 5 * b + 4 * cc - d) * t2 + (-a + 3 * b - 3 * cc + d) * t3)
            out += Offset(c(p0.x, p1.x, p2.x, p3.x), c(p0.y, p1.y, p2.y, p3.y))
        }
    }
    out += points.last()
    return out
}

private fun DrawScope.drawHead(center: Offset, slotW: Float, color: Color, faded: Boolean) {
    val headW = slotW * 0.8f
    val headH = slotW * 0.62f
    val topLeft = Offset(center.x - headW * 0.42f, center.y - headH / 2)

    if (!faded) {
        // Раздвоенный язык торчит вперёд.
        val start = Offset(topLeft.x + headW, center.y + headH * 0.05f)
        val fork = Offset(start.x + slotW * 0.2f, start.y)
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        drawPath(Path().apply { moveTo(start.x, start.y); lineTo(fork.x, fork.y) }, Tongue, style = stroke)
        drawPath(Path().apply { moveTo(fork.x, fork.y); lineTo(fork.x + slotW * 0.08f, fork.y - slotW * 0.07f) }, Tongue, style = stroke)
        drawPath(Path().apply { moveTo(fork.x, fork.y); lineTo(fork.x + slotW * 0.08f, fork.y + slotW * 0.07f) }, Tongue, style = stroke)
        val o = 1.6.dp.toPx()
        drawOval(SnakeDark, topLeft - Offset(o, o), Size(headW + 2 * o, headH + 2 * o))
    }
    drawOval(color, topLeft, Size(headW, headH))
    if (faded) return

    // Светлая щёчка и глазки.
    drawOval(SnakeBelly.copy(alpha = 0.6f), Offset(topLeft.x + headW * 0.3f, center.y + headH * 0.06f), Size(headW * 0.5f, headH * 0.3f))
    val eyeR = headH * 0.16f
    for (dy in listOf(-0.2f, 0.16f)) {
        val eye = Offset(topLeft.x + headW * 0.62f, center.y + headH * dy - headH * 0.04f)
        drawCircle(Color.White, eyeR, eye)
        drawCircle(Color.Black, eyeR * 0.55f, eye + Offset(eyeR * 0.28f, 0f))
    }
}
