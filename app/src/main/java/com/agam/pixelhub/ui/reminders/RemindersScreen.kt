@file:OptIn(ExperimentalMaterial3Api::class)

package com.agam.pixelhub.ui.reminders

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.container
import com.agam.pixelhub.data.Reminder
import com.agam.pixelhub.notify.ReminderNotifier
import com.agam.pixelhub.ui.CardRadius
import com.agam.pixelhub.ui.CheckDot
import com.agam.pixelhub.ui.EmptyState
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.Gutter
import com.agam.pixelhub.ui.HubFab
import com.agam.pixelhub.ui.QuickAction
import com.agam.pixelhub.ui.ScreenScaffold
import com.agam.pixelhub.ui.SectionHeader
import com.agam.pixelhub.ui.hubCard
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.animatedRadius
import com.agam.pixelhub.ui.motion.hubAnimateItem
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.motion.rememberStagger
import com.agam.pixelhub.ui.motion.staggerIn
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Which group a reminder sits in. Each lane is one card. */
private enum class Lane { Overdue, Upcoming, Repeating, Done }

/**
 * Rounds only the outer corners, so the rows of a lane read as one card. Takes the radii so a
 * row can ease its corners when it becomes, or stops being, the first or last of its lane.
 */
private fun segment(top: Dp, bottom: Dp) = RoundedCornerShape(
    topStart = top,
    topEnd = top,
    bottomStart = bottom,
    bottomEnd = bottom,
)

/** [animatedRadius], never below zero (the spring overshoots a little on the way down). */
@Composable
private fun animatedEdge(target: Dp): Dp = animatedRadius(target).coerceAtLeast(0.dp)

@Composable
fun RemindersScreen() {
    val context = LocalContext.current
    val c = context.container
    val colors = Hub.colors
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val stagger = rememberStagger()

    // null until the database answers, so the empty state never flashes on open.
    val reminders by c.reminders.all.collectAsStateWithLifecycle(initialValue = null)
    // Ticks so a reminder slides from Upcoming into Overdue while you watch.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            // On :00 and :30, so a reminder moves to Overdue right as its minute arrives.
            delay(30_000L - System.currentTimeMillis() % 30_000L + 50L)
            value = System.currentTimeMillis()
        }
    }
    // The editor opens on a draft: blank from "New", prefilled from a quick-add preset.
    var creating by rememberSaveable(stateSaver = ReminderDraftSaver) { mutableStateOf<ReminderDraft?>(null) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showDone by rememberSaveable { mutableStateOf(true) }

    val all = reminders
    val done = all.orEmpty().filter { it.done }
    val active = all.orEmpty().filterNot { it.done }
    // The hero: whatever fires next, one-off or repeating. It isn't repeated in its lane.
    val next = active.filter { it.triggerAt >= now }.minByOrNull { it.triggerAt }
    val repeating = active.filter { it.recurrence.isRepeating && it.id != next?.id }
    val oneOff = active.filterNot { it.recurrence.isRepeating }
    val overdue = oneOff.filter { it.triggerAt < now }
    val upcoming = oneOff.filter { it.triggerAt >= now && it.id != next?.id }
    // Everything ticked off: a small note takes the hero's place instead of nothing.
    val caughtUp = all != null && active.isEmpty() && done.isNotEmpty()
    // Whether this visit has seen something still to do, so finishing the last one can pop the note.
    val sawActive = remember { BooleanArray(1) }
    if (active.isNotEmpty()) sawActive[0] = true

    val snackbar = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()
    fun delete(r: Reminder) {
        ReminderNotifier.cancel(context, r.id)
        c.appScope.launch { c.reminders.delete(r) }
        uiScope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val undo = snackbar.showSnackbar("Deleted “${r.title}”", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (undo == SnackbarResult.ActionPerformed) c.appScope.launch { c.reminders.restore(r) }
        }
    }

    // A tick waits for its animation before saving; un-ticking in that window cancels it.
    val pendingTicks = remember { mutableMapOf<Long, Job>() }
    fun setDone(r: Reminder, d: Boolean, letTickDraw: Boolean = true) {
        val waiting = pendingTicks.remove(r.id)
        if (waiting != null && waiting.isActive && !d) {
            waiting.cancel()
            return
        }
        pendingTicks[r.id] = c.appScope.launch {
            // Let the tick draw itself before the row travels down to Done.
            if (d && letTickDraw && !reduced) delay(420)
            if (d) ReminderNotifier.cancel(context, r.id)
            c.reminders.setDone(r, d)
        }
    }

    val fabIn = remember { MutableTransitionState(false).apply { targetState = true } }

    ScreenScaffold(
        title = "Reminders",
        snackbarHost = snackbar,
        floatingActionButton = {
            AnimatedVisibility(
                visibleState = fabIn,
                enter = if (reduced) {
                    fadeIn(tween(150))
                } else {
                    scaleIn(initialScale = 0.8f, animationSpec = Motion.snappy()) + fadeIn(tween(180))
                },
            ) {
                HubFab(
                    icon = Icons.Outlined.Add,
                    contentDescription = "New reminder",
                    onClick = { creating = ReminderDraft() },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = 4.dp, bottom = 104.dp),
        ) {
            // Nothing until the database answers: if Quick add were laid out first, the hero
            // inserted above it would push the list off its top and open the screen scrolled.
            if (all == null) return@LazyColumn
            if (next != null) {
                item(key = "hero", contentType = "hero") {
                    NextHero(
                        r = next,
                        now = now,
                        // Repeating reminders can't be checked off, so the hero offers only Edit.
                        onDone = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            setDone(next, true, letTickDraw = false)
                        }.takeIf { !next.recurrence.isRepeating },
                        onOpen = { editingId = next.id },
                        modifier = hubAnimateItem(fadeIn = false)
                            .padding(horizontal = Gutter)
                            .staggerIn(stagger, "hero", 0),
                    )
                }
            }

            if (caughtUp) {
                item(key = "caughtUp", contentType = "hero") {
                    // Pops only when you finish the last one here; opening the screen to it just rises in.
                    val justFinished = remember { sawActive[0] }
                    CaughtUp(
                        pop = justFinished,
                        modifier = if (justFinished) {
                            hubAnimateItem().padding(horizontal = Gutter)
                        } else {
                            hubAnimateItem(fadeIn = false).staggerIn(stagger, "caughtUp", 0).padding(horizontal = Gutter)
                        },
                    )
                }
            }

            item(key = "quick", contentType = "quick") {
                QuickAdd(
                    onPick = { creating = it },
                    modifier = hubAnimateItem(fadeIn = false)
                        .padding(horizontal = Gutter)
                        .padding(top = if (next != null || caughtUp) 12.dp else 0.dp)
                        .staggerIn(stagger, "quick", 1),
                )
            }

            if (all.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    EmptyState(
                        title = "Nothing to remember yet",
                        body = "Pick a quick time above or tap New reminder. You can also ask the assistant.",
                        accent = colors.sky,
                        modifier = hubAnimateItem(fadeIn = false).staggerIn(stagger, "empty", 2),
                    )
                }
            }

            var n = 2
            fun lane(lane: Lane, title: String, rows: List<Reminder>, headerColor: Color) {
                if (rows.isEmpty()) return
                val headerKey = "header_${lane.name}"
                val headerIndex = n++
                val collapsible = lane == Lane.Done
                item(key = headerKey, contentType = "header") {
                    SectionHeader(
                        title,
                        // Only the first screenful takes part in the entrance; the rest are just there.
                        if (headerIndex <= 5) {
                            hubAnimateItem(fadeIn = false).staggerIn(stagger, headerKey, headerIndex)
                        } else {
                            hubAnimateItem()
                        },
                        color = headerColor,
                        action = if (!collapsible) null else if (showDone) "Hide" else "Show ${rows.size}",
                        onAction = if (!collapsible) null else ({ showDone = !showDone }),
                    )
                }
                if (collapsible && !showDone) return
                rows.forEachIndexed { i, r ->
                    val index = n++
                    item(key = r.id, contentType = "reminder") {
                        ReminderRow(
                            r = r,
                            lane = lane,
                            first = i == 0,
                            last = i == rows.lastIndex,
                            now = now,
                            // Repeating reminders can't be checked off; they show when they fire next.
                            onToggle = { d: Boolean -> setDone(r, d) }.takeIf { lane != Lane.Repeating },
                            onOpen = { editingId = r.id },
                            onDelete = { delete(r) },
                            modifier = if (index <= 5) {
                                hubAnimateItem(fadeIn = false).padding(horizontal = Gutter).staggerIn(stagger, r.id, index)
                            } else {
                                hubAnimateItem().padding(horizontal = Gutter)
                            },
                        )
                    }
                }
            }

            lane(Lane.Overdue, "Overdue", overdue, colors.danger)
            lane(Lane.Upcoming, "Upcoming", upcoming, colors.ink)
            lane(Lane.Repeating, "Repeating", repeating, colors.ink)
            lane(Lane.Done, "Done", done, colors.ink)
        }
    }

    creating?.let { draft ->
        key(draft) {
            ReminderEditorSheet(
                initial = draft,
                isNew = true,
                onDismiss = { creating = null },
                onSave = { d ->
                    creating = null
                    c.appScope.launch { c.reminders.save(0, d.title, d.notes, d.at, d.recurrence) }
                },
                onDelete = null,
            )
        }
    }
    reminders?.firstOrNull { it.id == editingId }?.let { r ->
        key(r.id) {
            ReminderEditorSheet(
                initial = ReminderDraft(r.title, r.notes, r.anchor, r.recurrence),
                isNew = false,
                onDismiss = { editingId = null },
                onSave = { d ->
                    editingId = null
                    // A new time or repeat brings a finished reminder back; a title fix leaves it done.
                    val reopen = r.done && (d.at != r.anchor || d.recurrence != r.recurrence)
                    c.appScope.launch {
                        c.reminders.save(r.id, d.title, d.notes, d.at, d.recurrence, done = if (reopen) false else null)
                    }
                },
                onDelete = {
                    editingId = null
                    delete(r)
                },
            )
        }
    }
}

/**
 * The next reminder on the yellow card: when it fires, what it is, and two direct actions.
 * Tapping anywhere else on the card edits it. Swaps with a short rise when the next one changes.
 */
@Composable
private fun NextHero(
    r: Reminder,
    now: Long,
    onDone: (() -> Unit)?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val soft = c.onSun.copy(alpha = 0.72f)

    Box(
        modifier
            .fillMaxWidth()
            // Press first so the whole card gives, not just what is on it.
            .pressable(onClick = onOpen, pressedScale = 0.985f, onClickLabel = "Edit reminder", showRipple = false)
            .hubCard(c.sun),
    ) {
        AnimatedContent(
            targetState = r,
            contentKey = { it.id },
            transitionSpec = {
                if (reduced) {
                    fadeIn(tween(150)) togetherWith fadeOut(tween(100))
                } else {
                    (slideInVertically(tween(260, easing = Motion.EaseOut)) { it / 6 } + fadeIn(tween(200))) togetherWith
                        (slideOutVertically(tween(180, easing = Motion.EaseOut)) { -it / 8 } + fadeOut(tween(120)))
                }
            },
            label = "nextHero",
        ) { item ->
            val at = Instant.ofEpochMilli(item.triggerAt).atZone(ZoneId.systemDefault())
            val day = dayWord(at.toLocalDate(), LocalDate.now())
            val meta = listOfNotNull(
                day,
                item.recurrence.describe().takeIf { it.isNotEmpty() },
            ).joinToString("  •  ")

            Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(c.onSun.copy(alpha = 0.10f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (item.recurrence.isRepeating) Icons.Outlined.Repeat else Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = c.onSun,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Next reminder",
                        style = HubType.small.copy(fontWeight = FontWeight.Medium),
                        color = soft,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "in ${Fmt.untilText(item.triggerAt, now)}",
                        style = HubType.small.copy(fontWeight = FontWeight.Medium),
                        color = c.sun,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(c.onSun)
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    Fmt.time(context, at.toLocalTime()),
                    style = HubType.clockLarge,
                    color = c.onSun,
                    maxLines = 1,
                )
                Text(
                    item.title,
                    style = HubType.section.copy(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
                    color = c.onSun,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(meta, style = HubType.small, color = soft, maxLines = 1, overflow = TextOverflow.Ellipsis)

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onDone != null) {
                        HeroPill(
                            icon = Icons.Outlined.Check,
                            label = "Mark done",
                            onClick = onDone,
                            container = c.onSun,
                            content = c.sun,
                            description = "Mark ${item.title} as done",
                        )
                    }
                    HeroPill(
                        icon = Icons.Outlined.Edit,
                        label = "Edit",
                        onClick = onOpen,
                        container = c.onSun.copy(alpha = 0.08f),
                        content = c.onSun,
                        description = "Edit ${item.title}",
                    )
                }
            }
        }
    }
}

/**
 * In the hero's place when every reminder is done. A rare moment, so it pops once when you
 * have just ticked off the last one; otherwise it simply rises in with the screen.
 */
@Composable
private fun CaughtUp(pop: Boolean, modifier: Modifier = Modifier) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val scale = remember { Animatable(if (pop && !reduced) 0.92f else 1f) }
    LaunchedEffect(Unit) { if (scale.value < 1f) scale.animateTo(1f, Motion.bouncy()) }
    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .hubCard(c.sun)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(c.onSun.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.TaskAlt, contentDescription = null, tint = c.onSun, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("All caught up", style = HubType.section, color = c.onSun, maxLines = 1)
            Text("Everything here is done.", style = HubType.small, color = c.onSun.copy(alpha = 0.72f))
        }
    }
}

/** A pill on the yellow card: an icon in a round disc, then the action's name. */
@Composable
private fun HeroPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    description: String,
) {
    Row(
        Modifier
            .heightIn(min = 48.dp)
            .pressable(onClick = onClick, pressedScale = 0.94f, onClickLabel = description, showRipple = false)
            .clip(CircleShape)
            .background(container)
            .semantics { contentDescription = description }
            .padding(start = 6.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(content.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(label, style = HubType.button, color = content, maxLines = 1)
    }
}

/** One-tap starting points. Each opens the editor already set to that time. */
@Composable
private fun QuickAdd(onPick: (ReminderDraft) -> Unit, modifier: Modifier = Modifier) {
    // Re-read on resume (a skipped composable never re-reads the clock), so the choice between
    // "Tonight" and "Next week" is right after the app sat in the background.
    var nowTime by remember { mutableStateOf(LocalDateTime.now()) }
    LifecycleResumeEffect(Unit) {
        nowTime = LocalDateTime.now()
        onPauseOrDispose { }
    }
    val tonight = LocalDateTime.of(nowTime.toLocalDate(), LocalTime.of(20, 0))
    // Each time is worked out from the clock at the tap, so a screen left open past midnight or
    // for most of an hour still gives "Tomorrow" and "In 1 hour" their real meaning.
    // "Tonight" only while there's still an evening left; otherwise offer next week.
    val third: Triple<ImageVector, String, (LocalDateTime) -> LocalDateTime?> =
        if (nowTime.isBefore(tonight.minusHours(1))) {
            Triple(Icons.Outlined.Bedtime, "Tonight", { n -> LocalDateTime.of(n.toLocalDate(), LocalTime.of(20, 0)) })
        } else {
            Triple(Icons.Outlined.Event, "Next week", { n -> LocalDateTime.of(n.toLocalDate().plusWeeks(1), LocalTime.of(9, 0)) })
        }
    val options = listOf<Triple<ImageVector, String, (LocalDateTime) -> LocalDateTime?>>(
        Triple(Icons.Outlined.Add, "New", { _ -> null }),
        Triple(Icons.Outlined.Timer, "In 1 hour", { n -> n.plusHours(1).truncatedTo(ChronoUnit.MINUTES) }),
        third,
        Triple(Icons.Outlined.WbSunny, "Tomorrow", { n -> LocalDateTime.of(n.toLocalDate().plusDays(1), LocalTime.of(9, 0)) }),
    )
    Row(
        modifier
            .fillMaxWidth()
            .hubCard(Hub.colors.raised)
            .padding(horizontal = 6.dp, vertical = 12.dp),
    ) {
        options.forEach { (icon, label, make) ->
            QuickAction(
                icon = icon,
                label = label,
                onClick = {
                    val n = LocalDateTime.now()
                    val at = make(n)
                    // A time already past (e.g. "Tonight" offered before 20:00, tapped after) moves to an hour from now.
                    val fresh = at?.takeIf { it.isAfter(n) }
                        ?: at?.let { n.plusHours(1).truncatedTo(ChronoUnit.MINUTES) }
                    onPick(if (fresh == null) ReminderDraft() else ReminderDraft(at = fresh))
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * One reminder inside its lane's card: an icon in a well, the title with "time • date" under
 * it, and the check at the end. Tap to edit, check to complete, swipe left to delete.
 */
@Composable
private fun ReminderRow(
    r: Reminder,
    lane: Lane,
    first: Boolean,
    last: Boolean,
    now: Long,
    onToggle: ((Boolean) -> Unit)?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val c = Hub.colors
    val haptics = LocalHapticFeedback.current
    val shape = segment(
        top = animatedEdge(if (first) CardRadius else 0.dp),
        bottom = animatedEdge(if (last) CardRadius else 0.dp),
    )
    val topPad = animatedEdge(if (first) 8.dp else 0.dp)
    val bottomPad = animatedEdge(if (last) 8.dp else 0.dp)

    // Checked locally first so the tick and strike start the instant you tap.
    var checked by remember(r.done) { mutableStateOf(r.done) }

    // Not saved state: a deleted row brought back by Undo reuses its key, and saved state would
    // come back already swiped and delete it again. A row always starts settled.
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

    val at = Instant.ofEpochMilli(r.triggerAt).atZone(ZoneId.systemDefault())
    val overdue = lane == Lane.Overdue && !checked
    val meta = metaLine(
        time = Fmt.time(context, at.toLocalTime()),
        day = dayWord(at.toLocalDate(), LocalDate.now()),
        repeat = r.recurrence.describe(),
        late = if (overdue) "${Fmt.untilText(now, r.triggerAt)} late" else null,
        muted = c.inkMuted,
        danger = c.danger,
    )
    val wellTone by animateColorAsState(if (overdue) c.dangerSoft else c.well, tween(200), label = "wellTone")
    val wellTint by animateColorAsState(
        when {
            checked -> c.inkMuted
            overdue -> c.danger
            else -> c.ink
        },
        tween(200), label = "wellTint",
    )
    val wellIcon = when {
        checked -> Icons.Outlined.TaskAlt
        lane == Lane.Repeating -> Icons.Outlined.Repeat
        overdue -> Icons.Outlined.NotificationsActive
        else -> Icons.Outlined.NotificationsNone
    }

    SwipeToDismissBox(
        state = dismiss,
        modifier = modifier.onSizeChanged { widthPx = it.width.coerceAtLeast(1) },
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    // Invisible at rest so it never peeks round the card's corners.
                    .graphicsLayer { alpha = (swipe() * 6f).coerceAtMost(1f) }
                    .clip(shape)
                    .background(c.danger),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = c.onDanger,
                    modifier = Modifier
                        .padding(end = 28.dp)
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
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(c.raised)
                .padding(
                    start = 6.dp,
                    end = 6.dp,
                    top = topPad,
                    bottom = bottomPad,
                ),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .pressable(onClick = onOpen, pressedScale = 0.98f, onClickLabel = "Edit reminder")
                    .padding(start = 12.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(wellTone), contentAlignment = Alignment.Center) {
                    Crossfade(wellIcon, animationSpec = tween(150), label = "wellIcon") { icon ->
                        Icon(icon, contentDescription = null, tint = wellTint, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                    StrikeTitle(r.title, struck = checked)
                    Text(meta, style = HubType.small.copy(fontSize = 11.sp, lineHeight = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (r.notes.isNotBlank()) {
                        Text(
                            r.notes,
                            style = HubType.small.copy(fontSize = 11.sp, lineHeight = 16.sp),
                            color = c.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (onToggle != null) {
                    CheckDot(
                        checked = checked,
                        onCheckedChange = { d ->
                            checked = d
                            haptics.performHapticFeedback(if (d) HapticFeedbackType.Confirm else HapticFeedbackType.ToggleOff)
                            onToggle(d)
                        },
                        accent = c.sky,
                        contentDescription = if (checked) "Mark ${r.title} as not done" else "Mark ${r.title} as done",
                    )
                } else {
                    // Repeating: the value at the end is how long until it fires again.
                    Text(
                        "in ${Fmt.untilText(r.triggerAt, now)}",
                        style = HubType.clockSmall.copy(fontSize = 13.sp),
                        color = c.sky,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp, end = 12.dp),
                    )
                }
            }
        }
    }
}

/** "9:00 AM  •  Tomorrow  •  Every day", with how late it is in red when overdue. */
private fun metaLine(
    time: String,
    day: String,
    repeat: String,
    late: String?,
    muted: Color,
    danger: Color,
): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = muted)) {
        append(time)
        append("  •  ")
        append(day)
        if (repeat.isNotEmpty()) {
            append("  •  ")
            append(repeat)
        }
    }
    if (late != null) {
        withStyle(SpanStyle(color = muted)) { append("  •  ") }
        withStyle(SpanStyle(color = danger)) { append(late) }
    }
}

/** The title, with a strike that draws itself across every line when it's done. */
@Composable
private fun StrikeTitle(text: String, struck: Boolean) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val progress by animateFloatAsState(
        if (struck) 1f else 0f,
        if (reduced) tween(0) else if (struck) tween(280, easing = Motion.EaseOut) else tween(160),
        label = "strike",
    )
    val color by animateColorAsState(if (struck) c.inkMuted else c.ink, tween(240), label = "titleInk")
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text,
        style = HubType.bodyStrong.copy(fontSize = 14.sp, lineHeight = 20.sp),
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { layout = it },
        modifier = Modifier.drawWithContent {
            drawContent()
            val l = layout ?: return@drawWithContent
            if (progress <= 0f) return@drawWithContent
            val widths = (0 until l.lineCount).map { l.getLineRight(it) - l.getLineLeft(it) }
            var remaining = widths.sum() * progress
            for (i in 0 until l.lineCount) {
                if (remaining <= 0f) break
                val seg = minOf(widths[i], remaining)
                val y = (l.getLineTop(i) + l.getLineBottom(i)) / 2f + 1.dp.toPx()
                val left = l.getLineLeft(i)
                drawLine(
                    color, Offset(left, y), Offset(left + seg, y),
                    strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round,
                )
                remaining -= seg
            }
        },
    )
}

/** "Today", "Tomorrow", "Yesterday", "Wed 12 Nov" this week, else "12 Nov" or "12 Nov 2027". */
private fun dayWord(date: LocalDate, today: LocalDate): String {
    val locale = Locale.getDefault()
    return when {
        date == today -> "Today"
        date == today.plusDays(1) -> "Tomorrow"
        date == today.minusDays(1) -> "Yesterday"
        date.isAfter(today) && date.isBefore(today.plusDays(7)) ->
            date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
        date.year == today.year -> date.format(DateTimeFormatter.ofPattern("d MMMM", locale))
        else -> date.format(DateTimeFormatter.ofPattern("d MMMM, yyyy", locale))
    }
}
