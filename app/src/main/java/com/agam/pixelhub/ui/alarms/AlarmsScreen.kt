package com.agam.pixelhub.ui.alarms

import android.os.SystemClock
import com.agam.pixelhub.ui.notchWidthFor
import com.agam.pixelhub.ui.heroHeight
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.alarm.AlarmRingService
import com.agam.pixelhub.container
import com.agam.pixelhub.data.Alarm
import com.agam.pixelhub.data.AlarmRepository
import com.agam.pixelhub.scheduling.Days
import com.agam.pixelhub.ui.CardRadius
import com.agam.pixelhub.ui.CircleIconButton
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.Gutter
import com.agam.pixelhub.ui.HubSwitch
import com.agam.pixelhub.ui.QuickAction
import com.agam.pixelhub.ui.ScreenScaffold
import com.agam.pixelhub.ui.hubCard
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.RollingText
import com.agam.pixelhub.ui.motion.animatedRadius
import com.agam.pixelhub.ui.motion.hubAnimateItem
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.motion.quickFade
import com.agam.pixelhub.ui.motion.rememberStagger
import com.agam.pixelhub.ui.motion.staggerIn
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubColors
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * The violet card's fill. A touch deeper in dark mode, where white text on the bright violet
 * falls short of 4.5:1; here even the small lines clear it in both themes.
 */
internal val HubColors.dawnCard: Color
    get() = if (isDark) lerp(dawn, Color.Black, 0.16f) else dawn

/** Secondary text on the violet card. Still 4.5:1 against [dawnCard]. */
internal val HubColors.onDawnSoft: Color
    get() = onDawn.copy(alpha = 0.92f)

private val Peek = 54.dp
// Both grow with the phone's font size so the card's text and the pill's label are never cut off.
private val HeroHeight: Dp @Composable get() = heroHeight()
private val NotchWidth: Dp @Composable get() = notchWidthFor(listOf("New alarm"), chrome = 8.dp + 40.dp + 10.dp + 14.dp + NotchGap, min = 158.dp)
private val NotchHeight = 64.dp
private val NotchGap = 6.dp

/** One-tap alarms that ring once, a set number of minutes from now. */
private val QUICK: List<Pair<Int, ImageVector>> = listOf(
    10 to Icons.Outlined.Timer,
    20 to Icons.Outlined.Snooze,
    30 to Icons.Outlined.Bedtime,
    60 to Icons.Outlined.Schedule,
)

/** Label given to quick alarms; the same alarm is reused so they don't pile up. */
private const val QUICK_LABEL = "Quick alarm"

@Composable
fun AlarmsScreen() {
    val context = LocalContext.current
    val c = context.container
    // Null until the database answers, so the empty state never flashes on open.
    val loaded by c.alarms.all.collectAsStateWithLifecycle<List<Alarm>?>(initialValue = null)
    val alarms = loaded
    val list = alarms.orEmpty()
    val lastQuickTap = remember { longArrayOf(0L) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val stagger = rememberStagger()

    // Ticks on the :00 and :30 of every minute so the countdowns turn over on time.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000L - System.currentTimeMillis() % 30_000L + 50L)
            value = System.currentTimeMillis()
        }
    }
    // Switched-on alarms in the order they will ring.
    val upcoming: List<Pair<Alarm, Long>> = remember(list, now) {
        list.filter { it.enabled }
            .map { it to AlarmRepository.nextTrigger(it).toInstant().toEpochMilli() }
            .sortedBy { it.second }
    }

    val snackbar = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()

    fun save(alarm: Alarm) {
        c.appScope.launch {
            val saved = c.alarms.save(alarm)
            if (saved.enabled) {
                val at = AlarmRepository.nextTrigger(saved).toInstant().toEpochMilli()
                uiScope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar("Alarm set for ${Fmt.untilText(at)} from now", duration = SnackbarDuration.Short)
                }
            }
        }
    }

    fun delete(a: Alarm) {
        c.appScope.launch { c.alarms.delete(a) }
        uiScope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val name = a.label.ifBlank { Fmt.time(context, LocalTime.of(a.hour, a.minute)) }
            val undo = snackbar.showSnackbar("Deleted “$name”", actionLabel = "Undo", duration = SnackbarDuration.Short)
            // Comes back as a new row (and is scheduled again if it was on).
            if (undo == SnackbarResult.ActionPerformed) c.appScope.launch { c.alarms.save(a.copy(id = 0L)) }
        }
    }

    fun quickAlarm(minutes: Int) {
        // A double tap would set two alarms for the same minute; the second then rings again
        // as "Snoozed" after the first is dismissed.
        val tap = SystemClock.uptimeMillis()
        if (tap - lastQuickTap[0] < 1_000L) return
        lastQuickTap[0] = tap
        // Rounded to the nearest minute.
        val t = LocalTime.now().plusSeconds(minutes * 60L + 30L)
        if (list.any { it.label == QUICK_LABEL && it.enabled && (it.daysMask and Days.ALL) == 0 && it.hour == t.hour && it.minute == t.minute }) return
        // Reuse a spent quick alarm, but never replace one that is still set to ring.
        // A one-shot quick alarm that rang and was snoozed is switched off but still due to ring.
        val reuse = list.firstOrNull {
            it.label == QUICK_LABEL && (it.daysMask and Days.ALL) == 0 && !it.enabled &&
                !c.scheduler.hasAlarmSnooze(it.id) && AlarmRingService.state.value?.alarmId != it.id
        }
        val base = reuse ?: Alarm(hour = t.hour, minute = t.minute, label = QUICK_LABEL)
        save(base.copy(hour = t.hour, minute = t.minute, enabled = true))
    }

    ScreenScaffold(
        title = "Alarms",
        snackbarHost = snackbar,
        actions = {
            CircleIconButton(icon = Icons.Outlined.Add, contentDescription = "New alarm", onClick = { creating = true })
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
        ) {
            item(key = "hero") {
                NextAlarmHero(
                    state = HeroState(
                        loaded = alarms != null,
                        any = list.isNotEmpty(),
                        next = upcoming.firstOrNull(),
                        now = now,
                    ),
                    then = upcoming.getOrNull(1),
                    onOpen = { editingId = it.id },
                    onNew = { creating = true },
                    modifier = hubAnimateItem(fadeIn = false)
                        .staggerIn(stagger, "hero", 0)
                        .padding(horizontal = Gutter),
                )
            }
            item(key = "quick") {
                QuickAlarms(
                    onPick = { quickAlarm(it) },
                    modifier = hubAnimateItem(fadeIn = false)
                        .staggerIn(stagger, "quick", 1)
                        .padding(horizontal = Gutter)
                        .padding(top = 12.dp),
                )
            }
            if (list.isNotEmpty()) {
                item(key = "head") {
                    ListHead(
                        total = list.size,
                        on = list.count { it.enabled },
                        modifier = hubAnimateItem(fadeIn = false)
                            .staggerIn(stagger, "head", 2)
                            .padding(top = 12.dp),
                    )
                }
            }
            itemsIndexed(list, key = { _, a -> a.id }) { i, a ->
                AlarmRow(
                    alarm = a,
                    last = i == list.lastIndex,
                    now = now,
                    onToggle = { on -> save(a.copy(enabled = on)) },
                    onClick = { editingId = a.id },
                    onDelete = { delete(a) },
                    // Only the first screenful takes part in the entrance; the rest are just there.
                    modifier = if (i + 3 <= 5) {
                        hubAnimateItem(fadeIn = false).staggerIn(stagger, a.id, i + 3)
                    } else {
                        hubAnimateItem()
                    },
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
    list.firstOrNull { it.id == editingId }?.let { a ->
        key(a.id) {
            AlarmEditorDialog(
                initial = a,
                isNew = false,
                onDismiss = { editingId = null },
                onSave = { editingId = null; save(it) },
                onDelete = {
                    editingId = null
                    delete(a)
                },
            )
        }
    }
}

private data class HeroState(val loaded: Boolean, val any: Boolean, val next: Pair<Alarm, Long>?, val now: Long)

/** 0 = still loading, 1 = no alarms at all, 2 = alarms but all off, 3 = one is coming up. */
private val HeroState.kind: Int
    get() = when {
        !loaded -> 0
        !any -> 1
        next == null -> 2
        else -> 3
    }

/**
 * The hero, as on the reference wallet: the next alarm on a violet card with the time large,
 * and the one after it peeking out behind. Tap the card to edit that alarm, tap the strip
 * behind to edit the following one. The card's bottom-right corner is cut away to seat the
 * New alarm button.
 */
@Composable
private fun NextAlarmHero(
    state: HeroState,
    then: Pair<Alarm, Long>?,
    onOpen: (Alarm) -> Unit,
    onNew: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val notchWidth = NotchWidth
    val notch = remember(notchWidth) { NotchShape(CardRadius, 18.dp, notchWidth, NotchHeight) }
    val peek by animateDpAsState(
        if (then != null) Peek else 0.dp,
        if (reduced) snap<Dp>() else Motion.snappy<Dp>(),
        label = "peek",
    )
    val next = state.next
    // When the following alarm goes away, keep drawing it while the card slides down over it,
    // instead of blanking the strip while the gap is still closing.
    val lastThen = remember { arrayOfNulls<Pair<Alarm, Long>>(1) }
    if (then != null) lastThen[0] = then
    val strip = then ?: lastThen[0].takeIf { peek > 0.dp }

    Box(modifier.fillMaxWidth().height(peek + HeroHeight)) {
        if (strip != null) {
            ThenStrip(
                alarm = strip.first,
                at = strip.second,
                now = state.now,
                onClick = { onOpen(strip.first) },
            )
        }
        Box(
            Modifier
                .offset(y = peek)
                .fillMaxWidth()
                .height(HeroHeight)
                .pressable(
                    onClick = { next?.let { onOpen(it.first) } },
                    enabled = next != null,
                    pressedScale = 0.985f,
                    showRipple = false,
                    onClickLabel = "Edit next alarm",
                )
                .clip(notch)
                .background(c.dawnCard),
        ) {
            HeroBody(state)
        }
        // The card's own action, seated in its notch.
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .width(NotchWidth - NotchGap)
                .height(NotchHeight - NotchGap)
                .pressable(onClick = onNew, pressedScale = 0.95f, onClickLabel = "New alarm", showRipple = false)
                .clip(CircleShape)
                .background(c.chrome)
                .padding(start = 8.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(c.onChrome), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = c.chrome, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("New alarm", style = HubType.button, color = c.onChrome, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun HeroBody(state: HeroState) {
    val c = Hub.colors
    val context = LocalContext.current
    val reduced = LocalReducedMotion.current
    val on = c.onDawn
    val soft = c.onDawnSoft
    val meta = HubType.small.copy(fontSize = 11.sp, lineHeight = 16.sp)
    Column(Modifier.padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().height(Peek), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Alarm, contentDescription = null, tint = on, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("Next alarm", style = HubType.bodyStrong.copy(fontSize = 14.sp), color = on, maxLines = 1)
            Spacer(Modifier.weight(1f))
            state.next?.let { (alarm, _) ->
                Text(
                    repeatCaption(alarm.daysMask),
                    style = HubType.bodyStrong.copy(fontSize = 14.sp),
                    color = on,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AnimatedContent(
            targetState = state,
            contentKey = { it.kind },
            transitionSpec = {
                if (reduced) {
                    fadeIn(tween(150)) togetherWith fadeOut(tween(100)) using SizeTransform(clip = false)
                } else {
                    fadeIn(tween(220, delayMillis = 40, easing = Motion.EaseOut)) togetherWith
                        fadeOut(tween(120)) using SizeTransform(clip = false)
                }
            },
            label = "hero",
        ) { s ->
            when (s.kind) {
                0 -> Spacer(Modifier.fillMaxWidth().height(100.dp))
                1, 2 -> Column {
                    Text(if (s.kind == 1) "Nothing set yet" else "Every alarm is off", style = HubType.small, color = soft, maxLines = 1)
                    Text(if (s.kind == 1) "No alarms" else "All off", style = HubType.clockLarge, color = on, maxLines = 1)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (s.kind == 1) "Tap New alarm, or pick a quick alarm below" else "Switch one on below, or add a new one",
                        style = meta,
                        color = soft,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = NotchWidth),
                    )
                }
                else -> s.next?.let { (alarm, at) ->
                    val (digits, period) = splitTime(Fmt.time(context, LocalTime.of(alarm.hour, alarm.minute)))
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Column(Modifier.weight(1f)) {
                                Text("Rings at", style = HubType.small, color = soft, maxLines = 1)
                                Row(verticalAlignment = Alignment.Bottom) {
                                    RollingText(digits, HubType.clockLarge, color = on)
                                    if (period != null) {
                                        Text(
                                            period,
                                            style = HubType.bodyStrong,
                                            color = on,
                                            modifier = Modifier.padding(start = 6.dp, bottom = 7.dp),
                                        )
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 6.dp)) {
                                Text("Rings in", style = meta, color = soft, maxLines = 1)
                                RollingText(Fmt.untilText(at, s.now), HubType.clockSmall, color = on)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Column(Modifier.padding(end = NotchWidth)) {
                            Text(Fmt.whenText(context, at), style = meta, color = soft, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                alarm.label.ifBlank { "Alarm" },
                                style = HubType.bodyStrong,
                                color = on,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The alarm after the next one, peeking out from behind the violet card. */
@Composable
private fun ThenStrip(alarm: Alarm, at: Long, now: Long, onClick: () -> Unit) {
    val c = Hub.colors
    val context = LocalContext.current
    val time = Fmt.time(context, LocalTime.of(alarm.hour, alarm.minute))
    Box(
        Modifier
            .fillMaxWidth()
            .height(Peek + CardRadius + 8.dp)
            .pressable(onClick = onClick, pressedScale = 0.99f, showRipple = false, onClickLabel = "Edit alarm at $time")
            .clip(RoundedCornerShape(CardRadius))
            .background(c.raised)
            .background(c.dawnSoft),
    ) {
        Row(
            Modifier.fillMaxWidth().height(Peek).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Then", style = HubType.small, color = c.inkMuted)
            Spacer(Modifier.width(10.dp))
            Text(time, style = HubType.clockSmall.copy(fontSize = 14.sp), color = c.ink, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Text(
                alarm.label.ifBlank { repeatCaption(alarm.daysMask) },
                style = HubType.small,
                color = c.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text("in ${Fmt.untilText(at, now)}", style = HubType.small.copy(fontSize = 12.sp), color = c.ink, maxLines = 1)
        }
    }
}

/** A white card of one-tap alarms: an icon in a well with "In 10 min" under it. */
@Composable
private fun QuickAlarms(onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Hub.colors
    val haptics = LocalHapticFeedback.current
    Column(modifier.fillMaxWidth().hubCard(c.raised).padding(horizontal = 8.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Quick alarm", style = HubType.section, color = c.ink, modifier = Modifier.weight(1f))
            Text("Rings once", style = HubType.small, color = c.inkMuted)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            QUICK.forEach { (minutes, icon) ->
                QuickAction(
                    icon = icon,
                    label = if (minutes % 60 == 0) "In ${minutes / 60} hour" else "In $minutes min",
                    onClick = {
                        // One tap sets a real alarm: confirm it.
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onPick(minutes)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Rounds only the corners at the card's ends, so consecutive list items read as one card. */
private fun Modifier.cardSlice(color: Color, top: Boolean, bottom: Boolean): Modifier =
    cardSlice(color, if (top) CardRadius else 0.dp, if (bottom) CardRadius else 0.dp)

/** As above with the radii given, so a row can ease its corners when it becomes the last. */
private fun Modifier.cardSlice(color: Color, top: Dp, bottom: Dp): Modifier =
    clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)).background(color)

/** [animatedRadius], never below zero (the spring overshoots a little on the way down). */
@Composable
private fun animatedEdge(target: Dp): Dp = animatedRadius(target).coerceAtLeast(0.dp)

/** The top of the alarms card: its title and how many are on. */
@Composable
private fun ListHead(total: Int, on: Int, modifier: Modifier = Modifier) {
    val c = Hub.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .cardSlice(c.raised, top = true, bottom = false)
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Your alarms", style = HubType.section, color = c.ink, modifier = Modifier.weight(1f))
        // The count rolls as alarms are switched on and off.
        Row(Modifier.clearAndSetSemantics { contentDescription = "$on of $total on" }) {
            RollingText("$on", HubType.small, color = c.inkMuted)
            Text(" of $total on", style = HubType.small, color = c.inkMuted)
        }
    }
}

/**
 * One alarm inside the card: the time large, the label and repeat days muted under it with
 * when it rings (or "Off"), and the switch. Tap anywhere else on the row to edit, swipe it
 * left to delete (Undo follows). The switch answers at once and the save follows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmRow(
    alarm: Alarm,
    last: Boolean,
    now: Long,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val c = Hub.colors
    val haptics = LocalHapticFeedback.current
    // Optimistic: the switch answers immediately, the database catches up.
    var on by remember(alarm.enabled) { mutableStateOf(alarm.enabled) }
    val timeColor by animateColorAsState(if (on) c.ink else c.inkMuted, tween(200, easing = Motion.EaseOut), label = "time")
    val (digits, period) = splitTime(Fmt.time(context, LocalTime.of(alarm.hour, alarm.minute)))
    val ringsAt = remember(alarm, now) { AlarmRepository.nextTrigger(alarm).toInstant().toEpochMilli() }
    val meta = listOfNotNull(alarm.label.takeIf { it.isNotBlank() }, repeatCaption(alarm.daysMask)).joinToString(" • ")
    val metaStyle = HubType.small.copy(fontSize = 11.sp, lineHeight = 16.sp)
    // The card's bottom corners and margin ease in and out as this row becomes, or stops being, the last.
    val bottomRadius = animatedEdge(if (last) CardRadius else 0.dp)
    val bottomPad = animatedEdge(if (last) 10.dp else 0.dp)
    val rowShape = RoundedCornerShape(20.dp)

    // Swipe left to delete, as on Reminders. Not saved state: an alarm brought back by Undo
    // must come back settled, not already swiped.
    val density = LocalDensity.current
    val dismiss = remember(density) {
        SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, density, positionalThreshold = { it * 0.45f })
    }
    val latestDelete by rememberUpdatedState(onDelete)
    LaunchedEffect(dismiss.currentValue) {
        if (dismiss.currentValue == SwipeToDismissBoxValue.EndToStart) latestDelete()
    }
    LaunchedEffect(dismiss.targetValue) {
        if (dismiss.targetValue == SwipeToDismissBoxValue.EndToStart) {
            haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
        }
    }
    var widthPx by remember { mutableIntStateOf(1) }
    // 0 at rest, 1 when the row has travelled its full width to the left. Read only while drawing.
    val swipe: () -> Float = {
        runCatching { (-dismiss.requireOffset() / widthPx).coerceIn(0f, 1f) }.getOrDefault(0f)
    }

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .cardSlice(c.raised, top = 0.dp, bottom = bottomRadius)
            .padding(start = 8.dp, end = 8.dp, bottom = bottomPad),
    ) {
        SwipeToDismissBox(
            state = dismiss,
            modifier = Modifier.onSizeChanged { widthPx = it.width.coerceAtLeast(1) },
            enableDismissFromStartToEnd = false,
            backgroundContent = {
                Box(
                    Modifier
                        .fillMaxSize()
                        // Invisible at rest so it never peeks round the row's corners.
                        .graphicsLayer { alpha = (swipe() * 6f).coerceAtMost(1f) }
                        .clip(rowShape)
                        .background(c.danger),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = c.onDanger,
                        modifier = Modifier
                            .padding(end = 24.dp)
                            .size(24.dp)
                            .graphicsLayer {
                                val f = (swipe() / 0.45f).coerceIn(0f, 1f)
                                val s = 0.7f + 0.5f * f
                                scaleX = s
                                scaleY = s
                            },
                    )
                }
            },
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(rowShape)
                    // Opaque, so the red only shows where the row has moved away.
                    .background(c.raised)
                    .pressable(onClick = onClick, pressedScale = 0.98f, onClickLabel = "Edit alarm")
                    // Screen readers can't swipe: offer Delete as an action on the row.
                    .semantics { customActions = listOf(CustomAccessibilityAction("Delete alarm") { latestDelete(); true }) }
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(digits, style = HubType.clockMedium, color = timeColor)
                        if (period != null) {
                            Text(
                                period,
                                style = HubType.bodyStrong.copy(fontSize = 14.sp),
                                color = timeColor,
                                modifier = Modifier.padding(start = 5.dp, bottom = 6.dp),
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            meta,
                            style = metaStyle,
                            color = c.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        // Words, not just the dimmed time, say whether it is on. Fades only when it is
                        // switched; the countdown itself just updates.
                        AnimatedContent(targetState = on, transitionSpec = { quickFade() }, label = "status") { isOn ->
                            Text(
                                if (isOn) " • in ${Fmt.untilText(ringsAt, now)}" else " • Off",
                                style = HubType.small.copy(fontSize = 12.sp, lineHeight = 16.sp),
                                color = c.inkMuted,
                                maxLines = 1,
                            )
                        }
                    }
                }
                HubSwitch(
                    checked = on,
                    onCheckedChange = {
                        haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                        on = it
                        onToggle(it)
                    },
                    accent = c.dawn,
                    contentDescription = buildString {
                        append("Alarm at ").append(digits)
                        if (period != null) append(' ').append(period)
                        if (alarm.label.isNotBlank()) append(", ").append(alarm.label)
                    },
                )
            }
        }
    }
}

/**
 * A rounded card with its bottom-right corner cut away: convex corners where the card meets
 * the cut, a pill-round inner corner that hugs the button seated there. Same geometry as the
 * deck on Home.
 */
private class NotchShape(
    private val radius: Dp,
    private val fillet: Dp,
    private val notchWidth: Dp,
    private val notchHeight: Dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        with(density) {
            val w = size.width
            val h = size.height
            val r = radius.toPx()
            val f = fillet.toPx()
            val nw = notchWidth.toPx()
            val nh = notchHeight.toPx()
            val inner = nh / 2f
            val p = Path().apply {
                moveTo(r, 0f)
                lineTo(w - r, 0f)
                arcTo(Rect(w - 2 * r, 0f, w, 2 * r), -90f, 90f, false)
                lineTo(w, h - nh - f)
                arcTo(Rect(w - 2 * f, h - nh - 2 * f, w, h - nh), 0f, 90f, false)
                lineTo(w - nw + inner, h - nh)
                arcTo(Rect(w - nw, h - nh, w - nw + 2 * inner, h - nh + 2 * inner), -90f, -90f, false)
                lineTo(w - nw, h - f)
                arcTo(Rect(w - nw - 2 * f, h - 2 * f, w - nw, h), 0f, 90f, false)
                lineTo(r, h)
                arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
                lineTo(0f, r)
                arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
                close()
            }
            return Outline.Generic(p)
        }
    }
}

private fun repeatCaption(mask: Int): String =
    if ((mask and Days.ALL) == 0) "Rings once" else Days.label(mask)

/** "7:30 AM" → ("7:30", "AM"); "07:30" → ("07:30", null). */
private fun splitTime(s: String): Pair<String, String?> {
    val i = s.indexOfFirst { it.isWhitespace() || it == ' ' }
    if (i <= 0) return s to null
    val rest = s.substring(i + 1).trim()
    return if (rest.any { it.isLetter() }) s.substring(0, i) to rest else s to null
}
