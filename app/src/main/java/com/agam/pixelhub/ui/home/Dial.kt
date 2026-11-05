package com.agam.pixelhub.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.easeOutBack
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import com.agam.pixelhub.ui.theme.Sky
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.cos
import kotlin.math.sin

enum class MarkKind { ALARM, REMINDER }

data class DialMark(val atMillis: Long, val kind: MarkKind)

/** The intro sweep plays in full once per app start; afterwards Home returns with a short version. */
private object DialIntro { var played = false }

/** Midnight sits at the bottom and noon at the top, so the sun is overhead at midday. */
private fun angleFor(hour: Float): Float = 90f + hour * 15f

private fun hourOf(z: ZonedDateTime): Float = z.hour + z.minute / 60f + z.second / 3600f

/**
 * The 24-hour dial. The ring is coloured by where daylight falls; the part of today that has
 * passed is dimmed; alarms (dawn) and reminders (sky) in the next 24 hours sit on the ring;
 * a knob marks now. [content] is laid over the middle.
 */
@Composable
fun DayDial(
    now: ZonedDateTime,
    marks: List<DialMark>,
    modifier: Modifier = Modifier,
    description: String,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val measurer = rememberTextMeasurer()
    val labelStyle = HubType.small.copy(color = c.inkMuted)

    val intro = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (intro.value < 1f) {
            val ms = if (DialIntro.played) 650 else 1700
            DialIntro.played = true
            intro.animateTo(1f, tween(ms, easing = LinearEasing))
        }
    }
    val pulse = rememberInfiniteTransition(label = "nowPulse")
    val halo by pulse.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400, easing = Motion.EaseOut), RepeatMode.Restart), label = "halo",
    )
    // The knob glides when the minute ticks over instead of jumping.
    val nowHour by animateFloatAsState(hourOf(now), Motion.gentle(), label = "nowHour")

    // Sweep gradient stops sampled from the sky, aligned so each hour sits at its angle.
    val skyBrushStops = remember(c.isDark) {
        (0..96).map { i ->
            val f = i / 96f
            val angle = f * 360f
            val hour = (((angle - 90f) % 360f + 360f) % 360f) / 15f
            val sky = Sky.at(hour)
            f to if (c.isDark) sky else sky.copy(alpha = 0.92f)
        }.toTypedArray()
    }

    val upcoming = remember(marks, now) {
        val end = now.plusHours(24).toInstant().toEpochMilli()
        val start = now.toInstant().toEpochMilli()
        marks.filter { it.atMillis in start..end }.sortedBy { it.atMillis }
    }

    Box(modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val t = intro.value
            val ringP = Motion.EaseOut.transform((t / 0.55f).coerceIn(0f, 1f))
            val detailA = ((t - 0.35f) / 0.3f).coerceIn(0f, 1f)

            val stroke = 18.dp.toPx()
            val r = size.minDimension / 2f - stroke / 2f - 6.dp.toPx()
            val topLeft = Offset(center.x - r, center.y - r)
            val arcSize = Size(r * 2, r * 2)

            // Track under the sky so the unswept part reads as a groove.
            drawCircle(c.raised2, radius = r, center = center, style = Stroke(stroke))
            drawArc(
                brush = Brush.sweepGradient(*skyBrushStops, center = center),
                startAngle = angleFor(0f), sweepAngle = 360f * ringP, useCenter = false,
                topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Butt),
            )
            // The part of today that has passed, dimmed.
            if (ringP > 0f) {
                drawArc(
                    color = c.ground.copy(alpha = 0.62f),
                    startAngle = angleFor(0f), sweepAngle = (nowHour * 15f).coerceAtMost(360f * ringP),
                    useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke + 1f),
                )
            }

            // Hour dots inside the ring; the four quarters are larger and labelled.
            val tickR = r - stroke / 2f - 10.dp.toPx()
            for (h in 0 until 24) {
                val major = h % 6 == 0
                val p = point(center, tickR, angleFor(h.toFloat()))
                drawCircle(
                    (if (major) c.inkMuted else c.raised2).copy(alpha = detailA),
                    radius = if (major) 2.6.dp.toPx() else 1.6.dp.toPx(), center = p,
                )
                if (major && detailA > 0f) {
                    val label = measurer.measure(h.toString(), labelStyle)
                    val lp = point(center, tickR - 18.dp.toPx(), angleFor(h.toFloat()))
                    drawText(
                        label, topLeft = Offset(lp.x - label.size.width / 2f, lp.y - label.size.height / 2f),
                        alpha = detailA,
                    )
                }
            }

            // Upcoming alarms and reminders pop onto the ring one after another.
            upcoming.forEachIndexed { i, m ->
                val start = 0.5f + i * 0.05f
                val local = ((t - start) / 0.28f).coerceIn(0f, 1f)
                if (local <= 0f) return@forEachIndexed
                val s = if (reduced) 1f else easeOutBack(local).coerceAtLeast(0f)
                val z = Instant.ofEpochMilli(m.atMillis).atZone(ZoneId.systemDefault())
                val p = point(center, r, angleFor(hourOf(z)))
                val color = if (m.kind == MarkKind.ALARM) c.dawn else c.sky
                drawCircle(c.ground, radius = 11.dp.toPx() * s, center = p)
                drawCircle(color, radius = 7.5.dp.toPx() * s, center = p)
            }

            // Now: a knob with a slow halo.
            val knobLocal = ((t - 0.45f) / 0.3f).coerceIn(0f, 1f)
            if (knobLocal > 0f) {
                val s = if (reduced) 1f else easeOutBack(knobLocal)
                val p = point(center, r, angleFor(nowHour))
                if (!reduced) {
                    drawCircle(c.ink.copy(alpha = 0.22f * (1f - halo)), radius = (12.dp.toPx() + 16.dp.toPx() * halo), center = p)
                }
                drawCircle(c.ground, radius = 13.dp.toPx() * s, center = p)
                drawCircle(c.ink, radius = 9.dp.toPx() * s, center = p)
            }
        }
        content()
    }
}

private fun DrawScope.point(center: Offset, radius: Float, angleDeg: Float): Offset {
    val a = Math.toRadians(angleDeg.toDouble())
    return Offset(center.x + radius * cos(a).toFloat(), center.y + radius * sin(a).toFloat())
}

/** A small colour dot used in the legend and the timeline. */
@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 10.dp) {
    Canvas(modifier.size(size)) { drawCircle(color) }
}
