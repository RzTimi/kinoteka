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
import kotlin.math.floor
import kotlin.math.sin

// Палитра змейки
private val Outline = Color(0xFF1F5F24)
private val Body = Color(0xFF4CAF50)
private val BodyLight = Color(0xFF8BD48F)
/** Непрозрачный блик (смесь Body и BodyLight), чтобы кружки не накладывались пятнами. */
private val BodyHighlight = Color(0xFF6CC270)
private val Belly = Color(0xFFD4E99A)
private val Diamond = Color(0xFF2E7D32)
private val DiamondCenter = Color(0xFFB9E07C)
private val Blush = Color(0xFFFF8A80)
private val Tongue = Color(0xFFE53935)
private val Shadow = Color(0x22000000)

private const val SLOTS = 10
/** Запас справа (в долях деления), чтобы язык не упирался в край. */
private const val RIGHT_SPACE = 0.5f
private const val STEPS = 14

/**
 * Оценка-змейка: 10 делений, змейка растёт до оценки (в том числе дробной, например 5.2),
 * голова — на конце. Нажатие ставит целую оценку, проведение пальцем — с шагом 0,1.
 * [interactive] = false — просто картинка (компактный вид, «Поділитися», питомец).
 */
@Composable
fun SnakeRating(
    rating: Double?,
    onRate: (Double) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    height: androidx.compose.ui.unit.Dp = 68.dp,
) {
    val currentRating by rememberUpdatedState(rating)
    val ghost = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

    val gestures = if (!interactive) Modifier else Modifier
        .pointerInput(Unit) {
            detectTapGestures { pos ->
                // Нажатие — целая оценка; повторное нажатие на ту же — снять.
                val slot = slotAt(pos.x, size.width.toFloat()).toDouble()
                if (slot == currentRating) onClear() else onRate(slot)
            }
        }
        .pointerInput(Unit) {
            detectHorizontalDragGestures { change, _ ->
                val v = valueAt(change.position.x, size.width.toFloat())
                if (v != currentRating) onRate(v)
            }
        }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = rating?.let { "Моя оцінка: ${formatScoreText(it)} з 10" } ?: "Оцінка не поставлена" }
            .then(gestures)
    ) {
        val slotW = size.width / (SLOTS + RIGHT_SPACE)
        val centers = (1..SLOTS).map { i -> Offset(slotW * (i - 0.5f), size.height * 0.46f + waveY(i, size.height)) }

        // Призрачные точки: показывают, куда змейка может вырасти.
        for (i in 1..SLOTS) {
            if (rating == null || i > rating + 0.35) drawCircle(ghost, radius = slotW * 0.13f, center = centers[i - 1])
        }
        if (rating == null) {
            drawSleepingHead(centers[0], slotW, ghost)
            return@Canvas
        }

        // Дробная длина: голова — между делениями (5.2 → чуть дальше пятого).
        val n = rating.coerceIn(1.0, SLOTS.toDouble()).toFloat()
        val all = sampleSpline(centers, STEPS)
        val visible = (n - 1f) * STEPS
        val last = floor(visible).toInt().coerceAtMost(all.size - 1)
        val frac = visible - last
        val head = if (last + 1 < all.size) lerp(all[last], all[last + 1], frac) else all[last]
        if (n > 1.05f) {
            val body = all.subList(0, last + 1) + head
            val maxR = slotW * 0.28f
            val minR = slotW * 0.05f // острый кончик хвоста
            val radii = body.indices.map { k ->
                val t = k.toFloat() / (body.size - 1)
                minR + (maxR - minR) * (1f - (1f - t) * (1f - t)) // быстро толстеет от кончика
            }
            val o = 1.6.dp.toPx()

            // 1. Мягкая тень под телом — одной линией, чтобы полупрозрачность не накладывалась.
            val shadowShift = Offset(0f, maxR * 0.55f + 2.dp.toPx())
            val shadowPath = Path().apply {
                moveTo(body[body.size / 6].x + shadowShift.x, body[body.size / 6].y + shadowShift.y)
                for (idx in body.size / 6 until body.size) lineTo(body[idx].x + shadowShift.x, body[idx].y + shadowShift.y)
            }
            drawPath(shadowPath, Shadow, style = Stroke(width = maxR * 1.5f, cap = StrokeCap.Round))
            // 2. Контур и основной цвет.
            body.forEachIndexed { k, p -> drawCircle(Outline, radii[k] + o, p) }
            body.forEachIndexed { k, p -> drawCircle(Body, radii[k], p) }
            // 3. Брюшко снизу.
            body.forEachIndexed { k, p -> drawCircle(Belly, radii[k] * 0.48f, p + Offset(0f, radii[k] * 0.48f)) }
            // 4. Узор-ромбики вдоль спины.
            var k = STEPS / 2
            while (k < body.size - STEPS / 3) {
                drawDiamond(body[k] - Offset(0f, radii[k] * 0.12f), radii[k] * 0.62f)
                k += STEPS / 2
            }
            // 5. Блик сверху — даёт объём.
            body.forEachIndexed { k2, p -> drawCircle(BodyHighlight, radii[k2] * 0.2f, p - Offset(0f, radii[k2] * 0.6f)) }
        }
        drawHead(head, slotW)
    }
}

private fun slotAt(x: Float, width: Float): Int =
    ((x / (width / (SLOTS + RIGHT_SPACE))).toInt() + 1).coerceIn(1, SLOTS)

/** Позиция пальца → оценка с шагом 0,1 (центр деления i = оценка i). */
private fun valueAt(x: Float, width: Float): Double {
    val v = x / (width / (SLOTS + RIGHT_SPACE)) + 0.5
    return (Math.round(v * 10) / 10.0).coerceIn(1.0, SLOTS.toDouble())
}

private fun lerp(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

private fun formatScoreText(v: Double) = com.kinoteka.app.data.library.formatScore(v)

/** Волна тела: змейка извивается. */
private fun waveY(i: Int, height: Float): Float = sin(i * 1.05 + PI / 3).toFloat() * height * 0.15f

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

/** Ромбик узора с светлой серединкой. */
private fun DrawScope.drawDiamond(c: Offset, size: Float) {
    fun diamond(s: Float) = Path().apply {
        moveTo(c.x, c.y - s * 0.6f)
        lineTo(c.x + s * 0.5f, c.y)
        lineTo(c.x, c.y + s * 0.6f)
        lineTo(c.x - s * 0.5f, c.y)
        close()
    }
    drawPath(diamond(size), Diamond)
    drawPath(diamond(size * 0.45f), DiamondCenter.copy(alpha = 0.8f))
}

/** Голова: округлая, с мордочкой, большими глазками с бликами, щёчками и язычком. */
private fun DrawScope.drawHead(center: Offset, slotW: Float) {
    val w = slotW * 0.95f
    val h = slotW * 0.72f
    val left = center.x - w * 0.42f
    val top = center.y - h / 2
    val o = 1.6.dp.toPx()

    // Форма головы: шире у затылка, сужается к мордочке.
    fun headPath(grow: Float) = Path().apply {
        val l = left - grow
        val t = top - grow
        val r = left + w + grow
        val b = top + h + grow
        moveTo(l + w * 0.15f, t + h * 0.1f)
        cubicTo(l + w * 0.45f, t - h * 0.08f, r - w * 0.12f, t + h * 0.05f, r, t + (b - t) * 0.48f)
        cubicTo(r - w * 0.1f, b - h * 0.02f, l + w * 0.45f, b + h * 0.08f, l + w * 0.15f, b - h * 0.1f)
        cubicTo(l - w * 0.05f, b - h * 0.3f, l - w * 0.05f, t + h * 0.3f, l + w * 0.15f, t + h * 0.1f)
        close()
    }

    // Язычок (высунут).
    val mouth = Offset(left + w, center.y + h * 0.08f)
    val fork = Offset(mouth.x + slotW * 0.2f, mouth.y + slotW * 0.02f)
    val tongueStroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
    drawPath(Path().apply { moveTo(mouth.x - w * 0.05f, mouth.y); lineTo(fork.x, fork.y) }, Tongue, style = tongueStroke)
    drawPath(Path().apply { moveTo(fork.x, fork.y); lineTo(fork.x + slotW * 0.08f, fork.y - slotW * 0.07f) }, Tongue, style = tongueStroke)
    drawPath(Path().apply { moveTo(fork.x, fork.y); lineTo(fork.x + slotW * 0.08f, fork.y + slotW * 0.06f) }, Tongue, style = tongueStroke)

    // Тень, контур, голова.
    drawOval(Shadow, Offset(left, top + h * 0.75f), Size(w, h * 0.45f))
    drawPath(headPath(o), Outline)
    drawPath(headPath(0f), Body)
    // Нижняя челюсть светлее.
    drawOval(Belly, Offset(left + w * 0.25f, center.y + h * 0.12f), Size(w * 0.62f, h * 0.3f))
    // Блик на макушке.
    drawOval(BodyLight.copy(alpha = 0.6f), Offset(left + w * 0.2f, top + h * 0.1f), Size(w * 0.45f, h * 0.16f))
    // Ромбик на затылке.
    drawDiamond(Offset(left + w * 0.2f, center.y - h * 0.05f), h * 0.35f)

    // Щёчка.
    drawOval(Blush.copy(alpha = 0.55f), Offset(left + w * 0.5f, center.y + h * 0.08f), Size(w * 0.2f, h * 0.14f))
    // Ноздри.
    drawCircle(Outline, h * 0.035f, Offset(left + w * 0.9f, center.y - h * 0.12f))
    // Улыбка.
    drawArc(
        color = Outline,
        startAngle = 20f,
        sweepAngle = 70f,
        useCenter = false,
        topLeft = Offset(left + w * 0.62f, center.y - h * 0.12f),
        size = Size(w * 0.3f, h * 0.3f),
        style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round),
    )
    // Большой глаз с бликами (змейка в профиль — один глаз).
    val eye = Offset(left + w * 0.6f, center.y - h * 0.12f)
    val eyeR = h * 0.22f
    drawCircle(Outline, eyeR + o * 0.6f, eye)
    drawCircle(Color.White, eyeR, eye)
    drawCircle(Color(0xFF212121), eyeR * 0.68f, eye + Offset(eyeR * 0.18f, eyeR * 0.05f))
    drawCircle(Color.White, eyeR * 0.26f, eye + Offset(eyeR * 0.32f, -eyeR * 0.25f))
    drawCircle(Color.White.copy(alpha = 0.8f), eyeR * 0.12f, eye + Offset(-eyeR * 0.05f, eyeR * 0.3f))
}

/** Спящая серая голова, когда оценки нет. */
private fun DrawScope.drawSleepingHead(center: Offset, slotW: Float, color: Color) {
    val w = slotW * 0.9f
    val h = slotW * 0.66f
    drawOval(color, Offset(center.x - w * 0.42f, center.y - h / 2), Size(w, h))
    // Закрытый глазик-дуга.
    drawArc(
        color = color.copy(alpha = (color.alpha * 2.5f).coerceAtMost(1f)),
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(center.x + w * 0.05f, center.y - h * 0.3f),
        size = Size(w * 0.25f, h * 0.25f),
        style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
    )
}
