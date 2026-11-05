package com.agam.pixelhub.alarm

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.outlined.Alarm
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.alarms.dawnCard
import com.agam.pixelhub.ui.alarms.onDawnSoft
import com.agam.pixelhub.ui.hubCard
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
        // Back would only hide this screen while the alarm keeps ringing; Snooze or Dismiss it instead.
        onBackPressedDispatcher.addCallback(this) { }
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
 * The page washed with the colour of the sky right now (data: where daylight falls), the
 * alarm on the violet card with its time large, and two big pills under it. Dismiss and
 * Snooze are there from the first frame: only the card makes a short entrance, and nothing
 * that animates ever delays or covers the buttons.
 */
@Composable
private fun RingScreen(ringing: AlarmRingService.Ringing, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val now = remember { LocalTime.now() }
    val sky = Sky.at(now.hour + now.minute / 60f)
    val wash = sky.copy(alpha = if (c.isDark) 0.20f else 0.26f)
    val date = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
    }
    val (digits, period) = splitTime(Fmt.time(context, LocalTime.of(ringing.hour, ringing.minute)))
    val on = c.onDawn
    val soft = c.onDawnSoft

    val appear = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(280, easing = Motion.EaseOut)) }

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
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            // The date sits on its own chip: muted text straight on the sky wash would be too faint.
            Row(
                Modifier
                    .padding(top = 8.dp)
                    .clip(CircleShape)
                    .background(c.raised)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(sky))
                Spacer(Modifier.width(10.dp))
                Text(date, style = HubType.button, color = c.ink, maxLines = 1)
            }

            Spacer(Modifier.weight(1f))

            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = appear.value
                        alpha = p
                        val s = 0.96f + 0.04f * p
                        scaleX = s
                        scaleY = s
                        if (!reduced) translationY = (1f - p) * 16.dp.toPx()
                    }
                    .hubCard(c.dawnCard, 32.dp)
                    .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RingingBell(disc = on, tint = c.dawnCard)
                    Spacer(Modifier.width(12.dp))
                    Text("Alarm", style = HubType.bodyStrong.copy(fontSize = 14.sp), color = on, modifier = Modifier.weight(1f))
                    Text("Ringing now", style = HubType.small, color = soft, maxLines = 1)
                }
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        digits,
                        style = HubType.clockHuge.copy(fontSize = 76.sp, lineHeight = 80.sp),
                        color = on,
                        maxLines = 1,
                    )
                    if (period != null) {
                        Text(
                            period,
                            style = HubType.section,
                            color = on,
                            modifier = Modifier.padding(start = 8.dp, bottom = 14.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    ringing.label.ifBlank { "Alarm" },
                    style = HubType.title.copy(fontSize = 22.sp, lineHeight = 30.sp),
                    color = on,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.weight(1f))

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RingButton(
                    text = "Dismiss",
                    icon = Icons.Outlined.AlarmOff,
                    container = c.ink,
                    content = c.ground,
                    disc = c.ground,
                    discIcon = c.ink,
                    onClick = onDismiss,
                )
                RingButton(
                    text = "Snooze ${ringing.snoozeMinutes} min",
                    icon = Icons.Outlined.Snooze,
                    container = c.raised,
                    content = c.ink,
                    disc = c.well,
                    discIcon = c.ink,
                    onClick = onSnooze,
                )
            }
        }
    }
}

/** An alarm icon in a round disc that gives a short shake every second or so. Still under reduced motion. */
@Composable
private fun RingingBell(disc: Color, tint: Color) {
    val reduced = LocalReducedMotion.current
    val turn = if (reduced) {
        0f
    } else {
        val t = rememberInfiniteTransition(label = "bell")
        val shake by t.animateFloat(
            initialValue = 0f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                keyframes<Float> {
                    durationMillis = 1300
                    0f at 0
                    -14f at 70
                    12f at 140
                    -8f at 210
                    5f at 270
                    0f at 320
                },
                RepeatMode.Restart,
            ),
            label = "shake",
        )
        shake
    }
    Box(Modifier.size(40.dp).clip(CircleShape).background(disc), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Outlined.Alarm,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = turn },
        )
    }
}

/**
 * A big pill for a just-woken thumb, in the reference's language: a round disc holding the
 * icon, then the action's name. The whole pill gives under the finger.
 */
@Composable
private fun RingButton(
    text: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    disc: Color,
    discIcon: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .pressable(onClick = onClick, pressedScale = 0.97f, onClickLabel = text, showRipple = false)
            .clip(CircleShape)
            .background(container)
            .padding(start = 10.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(disc), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = discIcon, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text,
            style = HubType.button.copy(fontSize = 20.sp, lineHeight = 26.sp),
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "7:30 AM" → ("7:30", "AM"); "07:30" → ("07:30", null). */
private fun splitTime(s: String): Pair<String, String?> {
    val i = s.indexOfFirst { it.isWhitespace() || it == ' ' }
    if (i <= 0) return s to null
    val rest = s.substring(i + 1).trim()
    return if (rest.any { it.isLetter() }) s.substring(0, i) to rest else s to null
}
