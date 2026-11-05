package com.agam.pixelhub.ui.home

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.container
import com.agam.pixelhub.data.AlarmRepository
import com.agam.pixelhub.launcher.ShortcutLauncher
import com.agam.pixelhub.ui.AppIcon
import com.agam.pixelhub.ui.CountBadge
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.Gutter
import com.agam.pixelhub.ui.Tab
import com.agam.pixelhub.ui.assistant.AssistantViewModel
import com.agam.pixelhub.ui.expenses.ExpenseHomeCard
import com.agam.pixelhub.ui.health.HealthHomeCard
import com.agam.pixelhub.ui.motion.MorphShape
import com.agam.pixelhub.ui.motion.HubShapes
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.RollingText
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.motion.rememberStagger
import com.agam.pixelhub.ui.motion.staggerIn
import com.agam.pixelhub.ui.settings.rememberSetupChecks
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import androidx.graphics.shapes.Morph
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One thing happening in the next 24 hours, for the timeline under the dial. */
private data class Upcoming(
    val key: String,
    val atMillis: Long,
    val title: String,
    val kind: MarkKind,
    val overdue: Boolean,
)

@Composable
fun HomeScreen(onNavigate: (Tab) -> Unit, assistant: AssistantViewModel) {
    val context = LocalContext.current
    val c = context.container
    val colors = Hub.colors
    val reminders by c.reminders.all.collectAsStateWithLifecycle(emptyList())
    val alarms by c.alarms.all.collectAsStateWithLifecycle(emptyList())
    val shortcuts by c.shortcuts.all.collectAsStateWithLifecycle(emptyList())
    val checks = rememberSetupChecks()
    val pendingSetup = checks.count { !it.ok }
    var ask by rememberSaveable { mutableStateOf("") }
    val stagger = rememberStagger()

    // Ticks on the minute so the knob, countdowns and greeting stay current.
    val now by produceState(ZonedDateTime.now()) {
        while (true) {
            val n = ZonedDateTime.now()
            value = n
            delay((60 - n.second) * 1000L + 50)
        }
    }
    val nowMillis = now.toInstant().toEpochMilli()

    val nextAlarm = alarms.filter { it.enabled }
        .map { it to AlarmRepository.nextTrigger(it).toInstant().toEpochMilli() }
        .minByOrNull { it.second }

    val dayEnd = nowMillis + 24 * 3_600_000L
    val startOfToday = now.toLocalDate().atStartOfDay(now.zone).toInstant().toEpochMilli()
    val upcoming = buildList {
        alarms.filter { it.enabled }.forEach { a ->
            val at = AlarmRepository.nextTrigger(a).toInstant().toEpochMilli()
            if (at <= dayEnd) add(Upcoming("a${a.id}", at, a.label.ifBlank { "Alarm" }, MarkKind.ALARM, false))
        }
        reminders.filter { !it.done && it.triggerAt <= dayEnd && it.triggerAt >= startOfToday }.forEach { r ->
            add(Upcoming("r${r.id}", r.triggerAt, r.title, MarkKind.REMINDER, r.triggerAt < nowMillis))
        }
    }.sortedBy { it.atMillis }
    val marks = upcoming.filter { !it.overdue }.map { DialMark(it.atMillis, it.kind) }

    fun submit(text: String) {
        if (text.isBlank()) return
        assistant.send(text)
        ask = ""
        onNavigate(Tab.ASSISTANT)
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { submit(it) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.ground)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding(),
    ) {
        // Greeting and settings.
        Row(
            Modifier.fillMaxWidth().padding(start = Gutter, end = 8.dp, top = 16.dp).staggerIn(stagger, "header", 0),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(greeting(now.toLocalTime()), style = HubType.title, color = colors.ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())),
                    style = HubType.body, color = colors.inkMuted,
                )
            }
            Box(
                Modifier.size(48.dp).clip(CircleShape).pressable(onClick = { onNavigate(Tab.SETTINGS) }, onClickLabel = "Settings"),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = colors.ink)
                CountBadge(pendingSetup, colors.dawn, Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp))
            }
        }

        // Setup nudge: a tiny progress ring and one sentence.
        AnimatedVisibility(
            visible = pendingSetup > 0,
            enter = expandVertically(tween(320, easing = Motion.EaseOut)) + fadeIn(),
            exit = shrinkVertically(tween(240)) + fadeOut(),
        ) {
            Row(
                Modifier
                    .padding(horizontal = Gutter)
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.dawnSoft)
                    .pressable(onClick = { onNavigate(Tab.SETTINGS) })
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val done = checks.size - pendingSetup
                Canvas(Modifier.size(28.dp)) {
                    val s = 4.dp.toPx()
                    drawCircle(colors.raised2, radius = size.minDimension / 2 - s / 2, style = Stroke(s))
                    drawArc(
                        colors.dawn, -90f, 360f * done / checks.size.coerceAtLeast(1), false,
                        topLeft = androidx.compose.ui.geometry.Offset(s / 2, s / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - s, size.height - s),
                        style = Stroke(s, cap = StrokeCap.Round),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    "$pendingSetup setup step${if (pendingSetup == 1) "" else "s"} left so alarms ring on time",
                    style = HubType.bodyStrong, color = colors.ink, modifier = Modifier.weight(1f),
                )
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = colors.dawn)
            }
        }

        // The dial.
        val dialDescription = nextAlarm?.let { (a, at) ->
            "Next alarm ${Fmt.time(context, LocalTime.of(a.hour, a.minute))}, in ${Fmt.untilText(at, nowMillis)}"
        } ?: "No alarms on"
        DayDial(
            now = now,
            marks = marks,
            description = dialDescription,
            modifier = Modifier.padding(horizontal = 14.dp).padding(top = 12.dp),
        ) {
            Column(
                Modifier
                    .clip(CircleShape)
                    .pressable(onClick = { onNavigate(Tab.ALARMS) }, showRipple = false, onClickLabel = "Open alarms")
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (nextAlarm != null) {
                    val (a, at) = nextAlarm
                    Text("Next alarm", style = HubType.small, color = colors.inkMuted)
                    RollingText(Fmt.time(context, LocalTime.of(a.hour, a.minute)), HubType.clockLarge, color = colors.ink)
                    Spacer(Modifier.height(2.dp))
                    RollingText("in ${Fmt.untilText(at, nowMillis)}", HubType.bodyStrong, color = colors.dawn)
                } else {
                    Text("Now", style = HubType.small, color = colors.inkMuted)
                    RollingText(Fmt.time(context, now.toLocalTime()), HubType.clockLarge, color = colors.ink)
                    Spacer(Modifier.height(2.dp))
                    Text("No alarms on", style = HubType.bodyStrong, color = colors.inkMuted)
                }
            }
        }

        // Legend: what the colours mean.
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp).staggerIn(stagger, "legend", 2),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendItem(colors.dawn, "Alarms")
            Spacer(Modifier.width(18.dp))
            LegendItem(colors.sky, "Reminders")
            Spacer(Modifier.width(18.dp))
            LegendItem(colors.ink, "Now")
        }

        // Ask the assistant.
        AskBar(
            value = ask,
            onValueChange = { ask = it },
            onSend = { submit(ask) },
            onMic = {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "What should I do?")
                try {
                    speech.launch(intent)
                } catch (e: ActivityNotFoundException) {
                    Toast.makeText(context, "Speech recognition isn't available", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.padding(horizontal = Gutter).padding(top = 24.dp).staggerIn(stagger, "ask", 3),
        )

        // Today's body and money, each opening its own page.
        HealthHomeCard(
            onOpen = { onNavigate(Tab.HEALTH) },
            modifier = Modifier.padding(horizontal = Gutter).padding(top = 16.dp).staggerIn(stagger, "health", 4),
        )
        ExpenseHomeCard(
            onOpen = { onNavigate(Tab.EXPENSES) },
            modifier = Modifier.padding(horizontal = Gutter).padding(top = 12.dp).staggerIn(stagger, "money", 5),
        )

        // The next 24 hours as a timeline.
        Text(
            "Next 24 hours",
            style = HubType.section, color = colors.ink,
            modifier = Modifier.padding(start = Gutter, end = Gutter, top = 32.dp, bottom = 8.dp).staggerIn(stagger, "nextTitle", 4),
        )
        if (upcoming.isEmpty()) {
            Text(
                "Nothing scheduled. Ask the assistant above, or add a reminder.",
                style = HubType.body, color = colors.inkMuted,
                modifier = Modifier.padding(horizontal = Gutter).staggerIn(stagger, "nextEmpty", 5),
            )
        }
        upcoming.take(8).forEachIndexed { i, u ->
            TimelineRow(
                u = u,
                timeText = Fmt.time(context, Instant.ofEpochMilli(u.atMillis).atZone(ZoneId.systemDefault()).toLocalTime()),
                isTomorrow = Instant.ofEpochMilli(u.atMillis).atZone(ZoneId.systemDefault()).toLocalDate() != now.toLocalDate(),
                isLast = i == minOf(upcoming.size, 8) - 1,
                onClick = { onNavigate(if (u.kind == MarkKind.ALARM) Tab.ALARMS else Tab.REMINDERS) },
                modifier = Modifier.staggerIn(stagger, u.key, 5 + i),
            )
        }
        if (upcoming.size > 8) {
            Text(
                "${upcoming.size - 8} more later",
                style = HubType.small, color = colors.inkMuted,
                modifier = Modifier.padding(start = Gutter + 80.dp, top = 4.dp),
            )
        }

        // Shortcuts.
        if (shortcuts.isNotEmpty()) {
            Text(
                "Shortcuts",
                style = HubType.section, color = colors.ink,
                modifier = Modifier.padding(start = Gutter, end = Gutter, top = 32.dp, bottom = 12.dp).staggerIn(stagger, "scTitle", 8),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = Gutter),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(shortcuts, key = { _, s -> s.id }) { i, s ->
                    Column(
                        Modifier
                            .width(72.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .pressable(onClick = {
                                if (!ShortcutLauncher.launch(context, s)) {
                                    Toast.makeText(context, "Couldn't open ${s.label}", Toast.LENGTH_SHORT).show()
                                }
                            }, pressedScale = 0.9f)
                            .padding(vertical = 8.dp)
                            .staggerIn(stagger, "sc${s.id}", 9 + i),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppIcon(s.packageName, size = 52.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            s.label, style = HubType.small, color = colors.ink,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color, size = 8.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, style = HubType.small, color = Hub.colors.inkMuted)
    }
}

/**
 * One row of the 24-hour timeline: time in clock numerals, a coloured dot on a soft thread
 * that joins it to the next row, and the title.
 */
@Composable
private fun TimelineRow(
    u: Upcoming,
    timeText: String,
    isTomorrow: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Hub.colors
    val accent = if (u.kind == MarkKind.ALARM) colors.dawn else colors.sky
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .pressable(onClick = onClick, pressedScale = 0.98f)
            .padding(horizontal = Gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(76.dp)) {
            Text(timeText, style = HubType.clockSmall, color = if (u.overdue) colors.danger else colors.ink, maxLines = 1)
            if (isTomorrow) Text("Tomorrow", style = HubType.small, color = colors.inkMuted)
            if (u.overdue) Text("Overdue", style = HubType.small, color = colors.danger)
        }
        Box(Modifier.width(28.dp).height(60.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 3.dp.toPx()
                if (!isLast) {
                    drawRoundRect(
                        accent.copy(alpha = 0.22f),
                        topLeft = androidx.compose.ui.geometry.Offset(center.x - w / 2, center.y),
                        size = androidx.compose.ui.geometry.Size(w, size.height / 2 + 2.dp.toPx()),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w),
                    )
                }
                drawCircle(colors.ground, radius = 8.dp.toPx())
                drawCircle(accent, radius = 5.5.dp.toPx())
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(u.title, style = HubType.bodyStrong, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

/**
 * The quick-ask pill. The send button morphs out of the mic's place when there's text.
 */
@Composable
private fun AskBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Hub.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(colors.raised)
            .padding(start = 16.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = colors.sky, modifier = Modifier.size(22.dp))
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("Remind me to call mom at 6pm", style = HubType.body, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            singleLine = true,
            textStyle = HubType.body,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = colors.sky,
                focusedTextColor = colors.ink,
                unfocusedTextColor = colors.ink,
                focusedPlaceholderColor = colors.inkMuted,
                unfocusedPlaceholderColor = colors.inkMuted,
            ),
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(
                visible = value.isBlank(),
                enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.85f),
                exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.85f),
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).pressable(onClick = onMic, onClickLabel = "Speak"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Mic, contentDescription = "Speak", tint = colors.ink)
                }
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = value.isNotBlank(),
                enter = fadeIn(tween(160)) + scaleIn(Motion.bouncy(), initialScale = 0.85f),
                exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.85f),
            ) {
                // Appears as a scalloped cookie and settles into a circle.
                val morph = remember { Morph(HubShapes.cookie, HubShapes.circle) }
                val settle = remember { androidx.compose.animation.core.Animatable(0f) }
                androidx.compose.runtime.LaunchedEffect(Unit) { settle.animateTo(1f, Motion.bouncy()) }
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(MorphShape(morph, settle.value.coerceIn(0f, 1f), rotation = (1f - settle.value) * 60f))
                        .background(colors.sky)
                        .pressable(onClick = onSend, pressedScale = 0.9f, onClickLabel = "Ask"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask", tint = colors.onSky, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

private fun greeting(t: LocalTime): String = when (t.hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}
