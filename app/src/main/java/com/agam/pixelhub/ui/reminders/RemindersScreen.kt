@file:OptIn(ExperimentalMaterial3Api::class)

package com.agam.pixelhub.ui.reminders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.container
import com.agam.pixelhub.data.Reminder
import com.agam.pixelhub.notify.ReminderNotifier
import com.agam.pixelhub.ui.CheckDot
import com.agam.pixelhub.ui.EmptyState
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.HubFab
import com.agam.pixelhub.ui.ScreenScaffold
import com.agam.pixelhub.ui.SectionHeader
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.motion.rememberStagger
import com.agam.pixelhub.ui.motion.staggerIn
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Which group a reminder sits in. Overdue and Upcoming are drawn as a timeline. */
private enum class Lane(val threaded: Boolean) { Overdue(true), Upcoming(true), Repeating(false), Done(false) }

/** Width of the time column on the left of every row. */
private val TimeCol = 76.dp
/** The square that holds the check (CheckDot is 48dp with a 28dp dot). */
private val DotBox = 48.dp
private val DotRadius = 14.dp
private val RowPadV = 6.dp
private val RowPadStart = 12.dp
private val RowShape = RoundedCornerShape(24.dp)

@Composable
fun RemindersScreen() {
    val context = LocalContext.current
    val c = context.container
    val colors = Hub.colors
    val reduced = LocalReducedMotion.current
    val stagger = rememberStagger()

    // null until the database answers, so the empty state never flashes on open.
    val reminders by c.reminders.all.collectAsStateWithLifecycle(initialValue = null)
    // Ticks so a reminder slides from Upcoming into Overdue while you watch.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Reminder?>(null) }

    val all = reminders
    val done = all.orEmpty().filter { it.done }
    val active = all.orEmpty().filterNot { it.done }
    val repeating = active.filter { it.recurrence.isRepeating }
    val oneOff = active.filterNot { it.recurrence.isRepeating }
    val overdue = oneOff.filter { it.triggerAt < now }
    val upcoming = oneOff.filter { it.triggerAt >= now }

    fun delete(r: Reminder) {
        ReminderNotifier.cancel(context, r.id)
        c.appScope.launch { c.reminders.delete(r) }
    }

    fun setDone(r: Reminder, d: Boolean) {
        c.appScope.launch {
            // Let the tick draw itself before the row travels down to Done.
            if (d && !reduced) delay(420)
            c.reminders.setDone(r, d)
        }
    }

    val fabIn = remember { MutableTransitionState(false).apply { targetState = true } }

    ScreenScaffold(
        title = "Reminders",
        floatingActionButton = {
            AnimatedVisibility(
                visibleState = fabIn,
                enter = scaleIn(initialScale = 0.6f, animationSpec = Motion.bouncy()) + fadeIn(tween(180)),
            ) {
                HubFab(
                    icon = Icons.Outlined.Add,
                    contentDescription = "New reminder",
                    onClick = { creating = true },
                    container = colors.sky,
                    content = colors.onSky,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 132.dp),
        ) {
            val placement = if (reduced) null else spring(
                dampingRatio = 0.86f,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = IntOffset.VisibilityThreshold,
            )

            if (all != null && all.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    EmptyState(
                        title = "Nothing to remember yet",
                        body = "Tap + to add a reminder, or tell the assistant what to remind you about.",
                        accent = colors.sky,
                        modifier = Modifier.animateItem(placementSpec = placement).staggerIn(stagger, "empty"),
                    )
                }
            }

            var n = 0
            fun lane(lane: Lane, title: String, rows: List<Reminder>, headerColor: Color) {
                if (rows.isEmpty()) return
                val headerKey = "header_${lane.name}"
                val headerIndex = n++
                item(key = headerKey, contentType = "header") {
                    SectionHeader(
                        title,
                        Modifier.animateItem(placementSpec = placement).staggerIn(stagger, headerKey, headerIndex),
                        color = headerColor,
                    )
                }
                rows.forEachIndexed { i, r ->
                    val index = n++
                    item(key = r.id, contentType = "reminder") {
                        ReminderRow(
                            r = r,
                            lane = lane,
                            threadAbove = lane.threaded && i > 0,
                            threadBelow = lane.threaded && i < rows.lastIndex,
                            isNext = lane == Lane.Upcoming && i == 0,
                            now = now,
                            // Repeating reminders can't be checked off; they show a repeat mark instead.
                            onToggle = { d: Boolean -> setDone(r, d) }.takeIf { lane != Lane.Repeating },
                            onOpen = { editing = r },
                            onDelete = { delete(r) },
                            modifier = Modifier.animateItem(placementSpec = placement).staggerIn(stagger, r.id, index),
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

    if (creating) {
        ReminderEditorSheet(
            initial = ReminderDraft(),
            isNew = true,
            onDismiss = { creating = false },
            onSave = { d ->
                creating = false
                c.appScope.launch { c.reminders.save(0, d.title, d.notes, d.at, d.recurrence) }
            },
            onDelete = null,
        )
    }
    editing?.let { r ->
        key(r.id) {
            ReminderEditorSheet(
                initial = ReminderDraft(r.title, r.notes, r.anchor, r.recurrence),
                isNew = false,
                onDismiss = { editing = null },
                onSave = { d ->
                    editing = null
                    c.appScope.launch { c.reminders.save(r.id, d.title, d.notes, d.at, d.recurrence) }
                },
                onDelete = {
                    editing = null
                    delete(r)
                },
            )
        }
    }
}

/**
 * One stop on the timeline: time on the left, the check on the thread, the title and its
 * details on the right. Swipe left to delete.
 */
@Composable
private fun ReminderRow(
    r: Reminder,
    lane: Lane,
    threadAbove: Boolean,
    threadBelow: Boolean,
    isNext: Boolean,
    now: Long,
    onToggle: ((Boolean) -> Unit)?,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current

    // Checked locally first so the tick and strike start the instant you tap.
    var checked by remember(r.done) { mutableStateOf(r.done) }

    val dismiss = rememberSwipeToDismissBoxState()
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

    // The thread grows out of the dot towards a neighbour, and retracts when one leaves.
    val above by animateFloatAsState(
        if (threadAbove) 1f else 0f, if (reduced) tween(0) else Motion.gentle(), label = "threadAbove",
    )
    val below by animateFloatAsState(
        if (threadBelow) 1f else 0f, if (reduced) tween(0) else Motion.gentle(), label = "threadBelow",
    )
    val threadColor = if (lane == Lane.Overdue) c.dangerSoft else c.skySoft

    val at = Instant.ofEpochMilli(r.triggerAt).atZone(ZoneId.systemDefault())
    val today = LocalDate.now()
    val dateText = dateLine(at.toLocalDate(), today)
    // Follows the local check, so unchecking a done item un-strikes it before it travels back up.
    val faded = checked
    val timeColor by animateColorAsState(
        when {
            faded -> c.inkMuted
            lane == Lane.Overdue -> c.danger
            else -> c.ink
        },
        tween(260), label = "timeColor",
    )

    SwipeToDismissBox(
        state = dismiss,
        modifier = modifier
            .padding(horizontal = 8.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) },
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    // Invisible at rest so it never peeks round the row's corners.
                    .graphicsLayer { alpha = (swipe() * 6f).coerceAtMost(1f) }
                    .clip(RowShape)
                    .background(c.danger),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = c.onDanger,
                    modifier = Modifier
                        .padding(end = 28.dp)
                        .size(26.dp)
                        .graphicsLayer {
                            val f = (swipe() / 0.45f).coerceIn(0f, 1f)
                            val s = 0.7f + 0.55f * f
                            scaleX = s
                            scaleY = s
                            rotationZ = -14f * (1f - f)
                        },
                )
            }
        },
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RowShape)
                .background(c.ground)
                .drawBehind {
                    if (above <= 0f && below <= 0f) return@drawBehind
                    val w = 3.dp.toPx()
                    val x = (RowPadStart + TimeCol + DotBox / 2).toPx() - w / 2
                    val cy = (RowPadV + DotBox / 2).toPx()
                    val gap = (DotRadius + 5.dp).toPx()
                    val corner = CornerRadius(w / 2, w / 2)
                    if (above > 0f) {
                        val end = cy - gap
                        val len = (end + w) * above
                        drawRoundRect(threadColor, Offset(x, end - len), Size(w, len), corner)
                    }
                    if (below > 0f) {
                        val start = cy + gap
                        val len = (size.height - start + w) * below
                        drawRoundRect(threadColor, Offset(x, start), Size(w, len), corner)
                    }
                }
                .pressable(onClick = onOpen, pressedScale = 0.985f, onClickLabel = "Edit reminder")
                .padding(start = RowPadStart, end = 12.dp, top = RowPadV, bottom = RowPadV),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.width(TimeCol).padding(top = 12.dp)) {
                TimeText(Fmt.time(context, at.toLocalTime()), timeColor)
                if (dateText != null) {
                    Text(
                        dateText,
                        style = HubType.small,
                        color = if (lane == Lane.Overdue && !faded) c.danger else c.inkMuted,
                        maxLines = 1,
                    )
                }
            }

            Box(Modifier.size(DotBox), contentAlignment = Alignment.Center) {
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
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(c.skySoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Repeat, contentDescription = "Repeating", tint = c.sky, modifier = Modifier.size(17.dp))
                    }
                }
            }

            Column(
                Modifier.weight(1f).padding(start = 6.dp, top = 12.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                StrikeTitle(r.title, struck = faded)
                when {
                    lane == Lane.Overdue && !faded ->
                        Text("${Fmt.untilText(now, r.triggerAt)} ago", style = HubType.small, color = c.danger)
                    isNext && !faded ->
                        Text("In ${Fmt.untilText(r.triggerAt, now)}", style = HubType.small, color = c.sky)
                }
                r.recurrence.describe().takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = HubType.small, color = c.inkMuted)
                }
                if (r.notes.isNotBlank()) {
                    Text(
                        r.notes,
                        style = HubType.small,
                        color = c.inkMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Condensed clock numerals with the AM/PM marker set smaller, in any locale. */
@Composable
private fun TimeText(text: String, color: Color) {
    val styled = remember(text) {
        buildAnnotatedString {
            text.forEach { ch ->
                if (ch.isLetter() || ch == '.') {
                    withStyle(SpanStyle(fontSize = 12.sp)) { append(ch) }
                } else {
                    append(ch)
                }
            }
        }
    }
    Text(styled, style = HubType.clockSmall, color = color, maxLines = 1, softWrap = false)
}

/** The title, with a strike that draws itself across every line when it's done. */
@Composable
private fun StrikeTitle(text: String, struck: Boolean) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val progress by animateFloatAsState(
        if (struck) 1f else 0f,
        if (reduced) tween(0) else if (struck) tween(380, easing = Motion.EaseOut) else tween(180),
        label = "strike",
    )
    val color by animateColorAsState(if (struck) c.inkMuted else c.ink, tween(300), label = "titleInk")
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        text,
        style = HubType.bodyStrong,
        color = color,
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
                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                )
                remaining -= seg
            }
        },
    )
}

/** The small line under the time: nothing for today, a word for neighbours, else a short date. */
private fun dateLine(date: LocalDate, today: LocalDate): String? {
    val locale = Locale.getDefault()
    return when {
        date == today -> null
        date == today.plusDays(1) -> "Tomorrow"
        date == today.minusDays(1) -> "Yesterday"
        date.isAfter(today) && date.isBefore(today.plusDays(7)) ->
            date.format(DateTimeFormatter.ofPattern("EEE d", locale))
        date.year == today.year -> date.format(DateTimeFormatter.ofPattern("d MMM", locale))
        else -> date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
    }
}
