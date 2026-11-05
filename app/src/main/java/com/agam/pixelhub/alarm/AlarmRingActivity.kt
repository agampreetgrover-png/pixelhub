package com.agam.pixelhub.alarm

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.motion.HubShapes
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import com.agam.pixelhub.ui.theme.PixelHubTheme
import com.agam.pixelhub.ui.theme.Sky
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Full-screen alarm UI, shown over the lock screen. */
class AlarmRingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        val activity = this
        setContent {
            PixelHubTheme {
                val ringing by AlarmRingService.state.collectAsStateWithLifecycle()
                LaunchedEffect(ringing) { if (ringing == null) activity.finish() }
                ringing?.let {
                    RingScreen(
                        ringing = it,
                        onSnooze = { AlarmRingService.send(activity, AlarmRingService.ACTION_SNOOZE) },
                        onDismiss = { AlarmRingService.send(activity, AlarmRingService.ACTION_DISMISS) },
                    )
                }
            }
        }
    }
}

/**
 * A night scene: the ground washed with the colour of the sky right now, the alarm time huge
 * over a slowly breathing, turning scallop with soft dawn ripples spreading from it.
 * Dismiss and Snooze are big, and they are there from the first frame: nothing that
 * animates ever delays or covers them.
 */
@Composable
private fun RingScreen(ringing: AlarmRingService.Ringing, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val now = remember { LocalTime.now() }
    val wash = Sky.at(now.hour + now.minute / 60f).copy(alpha = if (c.isDark) 0.20f else 0.26f)
    val date = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()))
    }
    val (digits, period) = splitTime(Fmt.time(context, LocalTime.of(ringing.hour, ringing.minute)))

    // Only the centrepiece makes an entrance; the buttons never wait for it.
    val appear = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(900, easing = Motion.EaseOut)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.ground)
            .background(wash),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                date,
                style = HubType.body,
                color = c.inkMuted,
                modifier = Modifier.padding(top = 16.dp),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .graphicsLayer {
                        val p = appear.value
                        alpha = p
                        val s = 0.94f + 0.06f * p
                        scaleX = s
                        scaleY = s
                    },
                contentAlignment = Alignment.Center,
            ) {
                BreathingHalo(color = c.dawn, modifier = Modifier.size(340.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(digits, style = HubType.clockHuge, color = c.ink)
                        if (period != null) {
                            Text(
                                period,
                                style = HubType.section,
                                color = c.ink,
                                modifier = Modifier.padding(start = 8.dp, bottom = 16.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        ringing.label.ifBlank { "Alarm" },
                        style = HubType.section,
                        color = c.ink,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(260.dp),
                    )
                }
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RingButton(
                    text = "Dismiss",
                    icon = Icons.Outlined.AlarmOff,
                    container = c.dawn,
                    content = c.onDawn,
                    onClick = onDismiss,
                )
                RingButton(
                    text = "Snooze ${ringing.snoozeMinutes} min",
                    icon = Icons.Outlined.Snooze,
                    container = c.raised2,
                    content = c.ink,
                    onClick = onSnooze,
                )
            }
        }
    }
}

/**
 * Behind the time: three soft dawn ripples spreading outward and fading, and a scalloped
 * shape that breathes between round and scalloped while it turns, with a smaller one turning
 * the other way inside it. Still under reduced motion.
 */
@Composable
private fun BreathingHalo(color: Color, modifier: Modifier = Modifier) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val morph = remember { Morph(HubShapes.circle, HubShapes.cookie) }
    val outerAlpha = if (c.isDark) 0.20f else 0.18f
    val innerAlpha = if (c.isDark) 0.14f else 0.12f

    if (reduced) {
        Canvas(modifier.clearAndSetSemantics { }) {
            val base = size.minDimension / 2f * 0.66f
            drawPolygonAt(HubShapes.cookie.toPath(android.graphics.Path()), base, 0f, color.copy(alpha = outerAlpha))
            drawPolygonAt(HubShapes.cookie.toPath(android.graphics.Path()), base * 0.8f, 20f, color.copy(alpha = innerAlpha))
        }
        return
    }

    val t = rememberInfiniteTransition(label = "ring")
    val breath by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2600, easing = Motion.EaseInOut), RepeatMode.Reverse),
        label = "breath",
    )
    val spin by t.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(40_000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )
    val wave by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(3900, easing = LinearEasing), RepeatMode.Restart),
        label = "wave",
    )

    Canvas(modifier.clearAndSetSemantics { }) {
        val maxR = size.minDimension / 2f
        val base = maxR * 0.66f
        // Ripples: filled discs, so they read as light rather than lines.
        repeat(3) { i ->
            val p = (wave + i / 3f) % 1f
            val eased = 1f - (1f - p) * (1f - p) * (1f - p)
            val fadeIn = (p * 8f).coerceAtMost(1f)
            drawCircle(
                color.copy(alpha = 0.16f * fadeIn * (1f - p)),
                radius = base * 0.9f + (maxR - base * 0.9f) * eased,
            )
        }
        val outer = morph.toPath(0.45f + 0.55f * breath, android.graphics.Path())
        drawPolygonAt(outer, base * (0.95f + 0.07f * breath), spin, color.copy(alpha = outerAlpha))
        val inner = HubShapes.cookie.toPath(android.graphics.Path())
        drawPolygonAt(inner, base * (0.80f - 0.03f * breath), -spin * 1.6f + 20f, color.copy(alpha = innerAlpha))
    }
}

/** Draws a unit polygon path centred in the canvas at [radius], turned by [degrees]. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPolygonAt(
    path: android.graphics.Path,
    radius: Float,
    degrees: Float,
    color: Color,
) {
    val m = android.graphics.Matrix()
    m.postRotate(degrees)
    m.postScale(radius, radius)
    m.postTranslate(center.x, center.y)
    path.transform(m)
    drawPath(path.asComposePath(), color)
}

/** A big pill for a just-woken thumb. The whole pill gives under the finger. */
@Composable
private fun RingButton(
    text: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .pressable(onClick = onClick, pressedScale = 0.97f, onClickLabel = text, showRipple = false)
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = HubType.button.copy(fontSize = 20.sp, lineHeight = 24.sp), color = content)
    }
}

/** "7:30 AM" → ("7:30", "AM"); "07:30" → ("07:30", null). */
private fun splitTime(s: String): Pair<String, String?> {
    val i = s.indexOfFirst { it.isWhitespace() || it == ' ' }
    if (i <= 0) return s to null
    val rest = s.substring(i + 1).trim()
    return if (rest.any { it.isLetter() }) s.substring(0, i) to rest else s to null
}
