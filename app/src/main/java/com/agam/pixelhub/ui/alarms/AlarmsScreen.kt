package com.agam.pixelhub.ui.alarms

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.container
import com.agam.pixelhub.data.Alarm
import com.agam.pixelhub.data.AlarmRepository
import com.agam.pixelhub.scheduling.Days
import com.agam.pixelhub.ui.EmptyState
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.Gutter
import com.agam.pixelhub.ui.HubFab
import com.agam.pixelhub.ui.HubSwitch
import com.agam.pixelhub.ui.ScreenScaffold
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.RollingText
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.motion.rememberStagger
import com.agam.pixelhub.ui.motion.staggerIn
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

@Composable
fun AlarmsScreen() {
    val context = LocalContext.current
    val c = context.container
    // Null until the database answers, so the empty state never flashes on open.
    val loaded by c.alarms.all.collectAsStateWithLifecycle<List<Alarm>?>(initialValue = null)
    val alarms = loaded
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Alarm?>(null) }
    val stagger = rememberStagger()
    // Rows whose strip dot has already swept into place; scrolling back doesn't replay it.
    val swept = remember { mutableSetOf<Long>() }

    // Ticks on the :00 and :30 of every minute so the countdown turns over on time.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000L - System.currentTimeMillis() % 30_000L + 50L)
            value = System.currentTimeMillis()
        }
    }
    val next: Long? = remember(alarms, now) {
        alarms?.filter { it.enabled }?.minOfOrNull { AlarmRepository.nextTrigger(it).toInstant().toEpochMilli() }
    }

    fun save(alarm: Alarm) {
        c.appScope.launch {
            val saved = c.alarms.save(alarm)
            if (saved.enabled) {
                val at = AlarmRepository.nextTrigger(saved).toInstant().toEpochMilli()
                Toast.makeText(context, "Alarm set for ${Fmt.untilText(at)} from now", Toast.LENGTH_SHORT).show()
            }
        }
    }

    ScreenScaffold(
        title = "Alarms",
        floatingActionButton = {
            HubFab(icon = Icons.Outlined.Add, contentDescription = "New alarm", onClick = { creating = true })
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = 4.dp, bottom = 128.dp),
        ) {
            item(key = "next") {
                NextAlarmHeader(
                    state = HeaderState(loaded = alarms != null, any = !alarms.isNullOrEmpty(), next = next, now = now),
                    modifier = Modifier.animateItem().staggerIn(stagger, "next", 0),
                )
            }
            if (alarms != null && alarms.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "No alarms yet",
                        body = "Tap + to set one, or ask the assistant to wake you up.",
                        accent = Hub.colors.dawn,
                        modifier = Modifier.animateItem().staggerIn(stagger, "empty", 1),
                    )
                }
            }
            itemsIndexed(alarms.orEmpty(), key = { _, a -> a.id }) { i, a ->
                AlarmRow(
                    alarm = a,
                    sweep = a.id !in swept,
                    onSwept = { swept += a.id },
                    onToggle = { on -> save(a.copy(enabled = on)) },
                    onClick = { editing = a },
                    modifier = Modifier.animateItem().staggerIn(stagger, a.id, i + 1),
                )
            }
        }
    }

    if (creating) {
        AlarmEditorDialog(
            initial = Alarm(hour = 7, minute = 0),
            isNew = true,
            onDismiss = { creating = false },
            onSave = { creating = false; save(it) },
            onDelete = null,
        )
    }
    editing?.let { a ->
        key(a.id) {
            AlarmEditorDialog(
                initial = a,
                isNew = false,
                onDismiss = { editing = null },
                onSave = { editing = null; save(it) },
                onDelete = {
                    editing = null
                    c.appScope.launch { c.alarms.delete(a) }
                },
            )
        }
    }
}

private data class HeaderState(val loaded: Boolean, val any: Boolean, val next: Long?, val now: Long)

/** What the header is showing: nothing yet, "No alarms on", or the countdown. */
private val HeaderState.kind: Int
    get() = when {
        !loaded || !any -> 0
        next == null -> 1
        else -> 2
    }

/**
 * "Next alarm in 4h 12m" with the countdown rolling like an odometer as minutes pass, and
 * the day and time it rings on the line below. Cross-fades when alarms switch on or off.
 */
@Composable
private fun NextAlarmHeader(state: HeaderState, modifier: Modifier = Modifier) {
    val c = Hub.colors
    val context = LocalContext.current
    val reduced = LocalReducedMotion.current
    AnimatedContent(
        targetState = state,
        contentKey = { it.kind },
        transitionSpec = {
            if (reduced) {
                fadeIn(tween(200)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false)
            } else {
                (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(tween(320, easing = Motion.EaseOut)) { it / 3 }) togetherWith
                    (fadeOut(tween(140)) + slideOutVertically(tween(200)) { -it / 4 }) using
                    SizeTransform(clip = false)
            }
        },
        modifier = modifier.fillMaxWidth().padding(horizontal = Gutter),
        label = "nextAlarm",
    ) { s ->
        when (s.kind) {
            0 -> Spacer(Modifier.fillMaxWidth().height(if (s.loaded) 0.dp else 52.dp))
            1 -> Text(
                "No alarms on",
                style = HubType.section,
                color = c.inkMuted,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            else -> {
                val at = s.next ?: 0L
                Column(Modifier.padding(bottom = 16.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("Next alarm in ", style = HubType.section, color = c.inkMuted)
                        RollingText(Fmt.untilText(at, s.now), style = HubType.section, color = c.dawn)
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(Fmt.whenText(context, at), style = HubType.body, color = c.inkMuted)
                }
            }
        }
    }
}

/**
 * One alarm: the time set large, the label and repeat days beneath, a slim 24-hour strip that
 * echoes the dial on Home, and the switch. Switching off fades the numerals and settles the
 * row back a touch; the switch moves at once and the save follows.
 */
@Composable
private fun AlarmRow(
    alarm: Alarm,
    sweep: Boolean,
    onSwept: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    // Optimistic: the switch answers immediately, the database catches up.
    var on by remember(alarm.enabled) { mutableStateOf(alarm.enabled) }
    val timeColor by animateColorAsState(if (on) c.ink else c.inkMuted, tween(240), label = "time")
    val labelColor by animateColorAsState(if (on) c.ink else c.inkMuted, tween(240), label = "label")
    val settle by animateFloatAsState(if (on || reduced) 1f else 0.96f, Motion.snappy(), label = "settle")
    val (digits, period) = splitTime(Fmt.time(context, LocalTime.of(alarm.hour, alarm.minute)))
    val repeat = repeatCaption(alarm.daysMask)

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(28.dp))
            .pressable(onClick = onClick, pressedScale = 0.98f, onClickLabel = "Edit alarm")
            .padding(start = Gutter - 8.dp, end = Gutter - 12.dp, top = 14.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .graphicsLayer {
                    scaleX = settle
                    scaleY = settle
                    transformOrigin = TransformOrigin(0f, 0.5f)
                },
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(digits, style = HubType.clockLarge, color = timeColor)
                if (period != null) {
                    Text(
                        period,
                        style = HubType.bodyStrong,
                        color = timeColor,
                        modifier = Modifier.padding(start = 6.dp, bottom = 9.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            if (alarm.label.isNotBlank()) {
                Text(
                    alarm.label,
                    style = HubType.bodyStrong,
                    color = labelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(repeat, style = HubType.small, color = c.inkMuted)
            Spacer(Modifier.height(12.dp))
            DayStrip(
                hour = alarm.hour,
                minute = alarm.minute,
                on = on,
                sweep = sweep,
                onSwept = onSwept,
            )
        }
        HubSwitch(
            checked = on,
            onCheckedChange = { on = it; onToggle(it) },
            accent = c.dawn,
            contentDescription = buildString {
                append("Alarm at ").append(digits)
                if (period != null) append(' ').append(period)
                if (alarm.label.isNotBlank()) append(", ").append(alarm.label)
            },
        )
    }
}

/**
 * A short 24-hour track with a dawn dot where the alarm falls in the day. The daylight
 * stretch (6:00 to 18:00) is a faintly lighter tone, as on the Home dial. On first sight
 * the dot sweeps in from midnight; edits glide it to the new time.
 */
@Composable
private fun DayStrip(hour: Int, minute: Int, on: Boolean, sweep: Boolean, onSwept: () -> Unit) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val target = (hour * 60 + minute) / 1440f
    val pos = remember { Animatable(if (sweep && !reduced) 0f else target) }
    LaunchedEffect(target) {
        onSwept()
        if (reduced) pos.snapTo(target) else pos.animateTo(target, Motion.gentle())
    }
    val dot by animateColorAsState(if (on) c.dawn else c.inkMuted, tween(240), label = "dot")
    val dotScale by animateFloatAsState(if (on) 1f else 0.9f, Motion.bouncy(), label = "dotScale")
    val halo by animateFloatAsState(if (on) 1f else 0f, tween(240), label = "halo")
    Canvas(
        Modifier
            .width(148.dp)
            .height(14.dp)
            .clearAndSetSemantics { },
    ) {
        val trackH = 4.dp.toPx()
        val r = 5.dp.toPx()
        val y = size.height / 2f
        val left = r
        val span = size.width - 2 * r
        drawRoundRect(
            c.raised2,
            topLeft = Offset(0f, y - trackH / 2f),
            size = Size(size.width, trackH),
            cornerRadius = CornerRadius(trackH / 2f),
        )
        drawRoundRect(
            c.ink.copy(alpha = if (c.isDark) 0.10f else 0.08f),
            topLeft = Offset(left + span * 0.25f, y - trackH / 2f),
            size = Size(span * 0.5f, trackH),
            cornerRadius = CornerRadius(trackH / 2f),
        )
        val at = Offset(left + span * pos.value, y)
        if (halo > 0f) drawCircle(dot.copy(alpha = 0.22f * halo), radius = r * 1.9f * dotScale, center = at)
        drawCircle(dot, radius = r * dotScale, center = at)
    }
}

private fun repeatCaption(mask: Int): String =
    if ((mask and Days.ALL) == 0) "Rings once" else Days.label(mask)

/** "7:30 AM" → ("7:30", "AM"); "07:30" → ("07:30", null). */
private fun splitTime(s: String): Pair<String, String?> {
    val i = s.indexOfFirst { it.isWhitespace() || it == ' ' }
    if (i <= 0) return s to null
    val rest = s.substring(i + 1).trim()
    return if (rest.any { it.isLetter() }) s.substring(0, i) to rest else s to null
}
