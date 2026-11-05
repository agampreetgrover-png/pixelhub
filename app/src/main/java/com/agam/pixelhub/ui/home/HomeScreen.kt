package com.agam.pixelhub.ui.home

import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AddAlarm
import androidx.compose.material.icons.outlined.AddAlert
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.container
import com.agam.pixelhub.data.Alarm
import com.agam.pixelhub.data.AlarmRepository
import com.agam.pixelhub.data.Reminder
import com.agam.pixelhub.expenses.Money
import com.agam.pixelhub.expenses.MonthSummary
import com.agam.pixelhub.notify.ReminderNotifier
import com.agam.pixelhub.scheduling.Days
import com.agam.pixelhub.ui.CheckDot
import com.agam.pixelhub.ui.CircleIconButton
import com.agam.pixelhub.ui.CountBadge
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.Gutter
import com.agam.pixelhub.ui.HubListRow
import com.agam.pixelhub.ui.LocalNavInset
import com.agam.pixelhub.ui.QuickAction
import com.agam.pixelhub.ui.QuietButton
import com.agam.pixelhub.ui.Tab
import com.agam.pixelhub.ui.alarms.AlarmEditorDialog
import com.agam.pixelhub.ui.assistant.AssistantViewModel
import com.agam.pixelhub.ui.calendar.UpcomingEventsCard
import com.agam.pixelhub.ui.expenses.ExpenseEditorSheet
import com.agam.pixelhub.ui.expenses.color
import com.agam.pixelhub.ui.expenses.icon
import com.agam.pixelhub.ui.expenses.soft
import com.agam.pixelhub.ui.health.HealthHomeCard
import com.agam.pixelhub.ui.hubCard
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.RollingText
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.motion.rememberStagger
import com.agam.pixelhub.ui.motion.revealEnter
import com.agam.pixelhub.ui.motion.revealExit
import com.agam.pixelhub.ui.motion.staggerIn
import com.agam.pixelhub.ui.reminders.ReminderDraft
import com.agam.pixelhub.ui.reminders.ReminderEditorDialog
import com.agam.pixelhub.ui.settings.rememberSetupChecks
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Something in the next 24 hours, for the Today card. */
private sealed interface TodayItem {
    val key: String
    val atMillis: Long

    data class AlarmItem(val alarm: Alarm, override val atMillis: Long) : TodayItem {
        override val key get() = "a${alarm.id}"
    }

    data class ReminderItem(val reminder: Reminder, val overdue: Boolean) : TodayItem {
        override val key get() = "r${reminder.id}"
        override val atMillis get() = reminder.triggerAt
    }
}

/** Which editor Home has open, if any. Editors open right here; no trip to another tab. */
private sealed interface HomeEditor {
    data class NewAlarm(val hour: Int, val minute: Int) : HomeEditor
    data class EditAlarm(val id: Long) : HomeEditor
    data object NewReminder : HomeEditor
    data class EditReminder(val id: Long) : HomeEditor
    data object NewExpense : HomeEditor
}

/** Keeps an open editor through rotation and theme changes; edited items are found again by id. */
private val HomeEditorSaver: Saver<HomeEditor?, String> = Saver(
    save = { e ->
        when (e) {
            null -> null
            is HomeEditor.NewAlarm -> "na:${e.hour}:${e.minute}"
            is HomeEditor.EditAlarm -> "ea:${e.id}"
            HomeEditor.NewReminder -> "nr"
            is HomeEditor.EditReminder -> "er:${e.id}"
            HomeEditor.NewExpense -> "nx"
        }
    },
    restore = { s ->
        val p = s.split(":")
        when (p[0]) {
            "na" -> HomeEditor.NewAlarm(p[1].toInt(), p[2].toInt())
            "ea" -> HomeEditor.EditAlarm(p[1].toLong())
            "nr" -> HomeEditor.NewReminder
            "er" -> HomeEditor.EditReminder(p[1].toLong())
            else -> HomeEditor.NewExpense
        }
    },
)

@Suppress("UNUSED_PARAMETER")
@Composable
fun HomeScreen(onNavigate: (Tab) -> Unit, assistant: AssistantViewModel, onOpenMenu: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.container
    val colors = Hub.colors
    val reminders by app.reminders.all.collectAsStateWithLifecycle(emptyList())
    val alarms by app.alarms.all.collectAsStateWithLifecycle(emptyList())
    val checks = rememberSetupChecks()
    val pendingSetup = checks.count { !it.ok }
    val stagger = rememberStagger()
    val reduced = LocalReducedMotion.current
    var editor by rememberSaveable(stateSaver = HomeEditorSaver) { mutableStateOf<HomeEditor?>(null) }
    val scroll = rememberScrollState()

    // Ticks on the minute so countdowns and the greeting stay current, and re-reads the clock on
    // every resume: the minute delay runs on uptime, which stops while the phone sleeps, so
    // coming back in the morning showed last night's greeting and countdowns for up to a minute.
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    LifecycleResumeEffect(Unit) {
        now = ZonedDateTime.now()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay((60 - ZonedDateTime.now().second) * 1000L + 50)
            now = ZonedDateTime.now()
        }
    }
    val nowMillis = now.toInstant().toEpochMilli()
    val zone = now.zone
    // Follows the calendar: after midnight on the last day the card moves on to the new month.
    val thisMonth = YearMonth.from(now)
    val monthFlow = remember(thisMonth) { app.expenses.month(thisMonth) }
    val month by monthFlow.collectAsStateWithLifecycle<MonthSummary?>(initialValue = null)

    val nextAlarm = alarms.filter { it.enabled }
        .map { it to AlarmRepository.nextTrigger(it).toInstant().toEpochMilli() }
        .minByOrNull { it.second }
    val nextReminder = reminders.filter { !it.done && it.triggerAt >= nowMillis }.minByOrNull { it.triggerAt }
    val overdue = reminders.count { !it.done && it.triggerAt < nowMillis }

    val dayEnd = nowMillis + 24 * 3_600_000L
    val startOfToday = now.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    val today: List<TodayItem> = buildList {
        alarms.filter { it.enabled }.forEach { a ->
            val at = AlarmRepository.nextTrigger(a).toInstant().toEpochMilli()
            if (at <= dayEnd) add(TodayItem.AlarmItem(a, at))
        }
        // Every overdue reminder, from any day, so the list agrees with the badge on the bell.
        reminders.filter { !it.done && it.triggerAt <= dayEnd }.forEach { r ->
            add(TodayItem.ReminderItem(r, r.triggerAt < nowMillis))
        }
    }.sortedBy { it.atMillis }

    fun timeOf(millis: Long): String = Fmt.time(context, Instant.ofEpochMilli(millis).atZone(zone).toLocalTime())
    fun dayOf(millis: Long): String = dayLabel(Instant.ofEpochMilli(millis).atZone(zone).toLocalDate(), now.toLocalDate())

    val alarmFace = CardFace(
        name = "Alarms",
        color = colors.dawn,
        onColor = colors.onDawn,
        icon = Icons.Outlined.Alarm,
        corner = nextAlarm?.let { (a, _) -> Days.label(a.daysMask) } ?: "${alarms.size} saved",
        kicker = if (nextAlarm != null) "Next alarm" else "No alarm on",
        figure = nextAlarm?.let { (a, _) -> Fmt.time(context, LocalTime.of(a.hour, a.minute)) } ?: "Off",
        asideLabel = nextAlarm?.let { "Rings in" },
        aside = nextAlarm?.let { (_, at) -> Fmt.untilText(at, nowMillis) },
        footLabel = "Label",
        foot = nextAlarm?.first?.label?.ifBlank { "Alarm" } ?: "Set one for tomorrow",
        actionLabel = "New alarm",
        onAction = { editor = HomeEditor.NewAlarm(7, 0) },
        onOpen = { nextAlarm?.let { editor = HomeEditor.EditAlarm(it.first.id) } ?: onNavigate(Tab.ALARMS) },
    )
    val reminderFace = CardFace(
        name = "Reminders",
        color = colors.sun,
        onColor = colors.onSun,
        icon = Icons.Outlined.NotificationsActive,
        corner = nextReminder?.let { dayOf(it.triggerAt) } ?: if (overdue > 0) "Overdue" else "All clear",
        kicker = when {
            nextReminder != null -> "Next reminder"
            overdue > 0 -> "Overdue"
            else -> "Nothing due"
        },
        figure = nextReminder?.let { timeOf(it.triggerAt) } ?: if (overdue > 0) "$overdue" else "Free",
        asideLabel = nextReminder?.let { "Due in" },
        aside = nextReminder?.let { Fmt.untilText(it.triggerAt, nowMillis) },
        footLabel = "Reminder",
        foot = nextReminder?.title ?: if (overdue > 0) "$overdue overdue" else "Add what's on your mind",
        actionLabel = "Reminder",
        onAction = { editor = HomeEditor.NewReminder },
        onOpen = { nextReminder?.let { editor = HomeEditor.EditReminder(it.id) } ?: onNavigate(Tab.REMINDERS) },
    )

    Box(Modifier.fillMaxSize().background(colors.ground)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .statusBarsPadding()
                // Landscape: keep clear of the camera cutout and a side navigation bar.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            // The menu button, top left; it opens the navigation drawer.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 12.dp).staggerIn(stagger, "bar", 0),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    val attention = overdue + pendingSetup
                    CircleIconButton(
                        Icons.Outlined.Menu,
                        if (attention > 0) "Open menu, $attention need attention" else "Open menu",
                        onClick = onOpenMenu,
                    )
                    // One dot for anything that needs you: overdue reminders or unfinished setup.
                    // The drawer shows which (counts on Reminders and Settings).
                    CountBadge(attention, colors.danger, Modifier.align(Alignment.TopEnd).clearAndSetSemantics { })
                }
            }

            // Greeting.
            Column(Modifier.padding(horizontal = Gutter).padding(top = 20.dp).staggerIn(stagger, "hello", 1)) {
                Text(greeting(now.toLocalTime()), style = HubType.title, color = colors.ink)
                Text(
                    now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                    style = HubType.small, color = colors.inkMuted,
                )
            }

            // The deck: next alarm in front, next reminder peeking behind.
            CardDeck(
                faces = listOf(alarmFace, reminderFace),
                modifier = Modifier.padding(horizontal = Gutter).padding(top = 18.dp).staggerIn(stagger, "deck", 2),
            )

            // Quick actions, each opening its editor right here.
            Row(
                Modifier
                    .padding(horizontal = Gutter)
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .hubCard(colors.raised)
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .staggerIn(stagger, "quick", 3),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                QuickAction(Icons.Outlined.AddAlarm, "Alarm", onClick = { editor = HomeEditor.NewAlarm(7, 0) })
                QuickAction(Icons.Outlined.AddAlert, "Reminder", onClick = { editor = HomeEditor.NewReminder })
                QuickAction(Icons.Outlined.Payments, "Expense", onClick = { editor = HomeEditor.NewExpense })
                QuickAction(Icons.Outlined.MoreHoriz, "More", onClick = onOpenMenu)
            }

            // Setup nudge: a small progress ring and one sentence.
            AnimatedVisibility(
                visible = pendingSetup > 0,
                enter = revealEnter(reduced),
                exit = revealExit(reduced),
            ) {
                SetupNudge(done = checks.size - pendingSetup, total = checks.size, pending = pendingSetup, onClick = { onNavigate(Tab.SETTINGS) })
            }

            // Today.
            Column(
                Modifier
                    .padding(horizontal = Gutter)
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .hubCard(colors.raised)
                    .padding(bottom = 8.dp)
                    .animateContentSize(if (reduced) snap() else tween(220, easing = Motion.EaseOut))
                    .staggerIn(stagger, "today", 4),
            ) {
                CardHeader("Today", "View all") { onNavigate(Tab.REMINDERS) }
                if (today.isEmpty()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Nothing in the next 24 hours.",
                            style = HubType.body, color = colors.inkMuted,
                            modifier = Modifier.weight(1f),
                        )
                        QuietButton(
                            "Add",
                            onClick = { editor = HomeEditor.NewReminder },
                            modifier = Modifier.semantics { contentDescription = "Add reminder" },
                        )
                    }
                }
                today.take(6).forEach { item -> key(item.key) {
                    when (item) {
                        is TodayItem.AlarmItem -> HubListRow(
                            icon = Icons.Outlined.Alarm,
                            title = item.alarm.label.ifBlank { "Alarm" },
                            meta = "${timeOf(item.atMillis)} • ${dayOf(item.atMillis)}",
                            trailing = "in ${Fmt.untilText(item.atMillis, nowMillis)}",
                            iconTint = colors.dawn,
                            onClick = { editor = HomeEditor.EditAlarm(item.alarm.id) },
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                        is TodayItem.ReminderItem -> ReminderRow(
                            r = item.reminder,
                            meta = "${timeOf(item.atMillis)} • ${if (item.overdue) "Overdue" else dayOf(item.atMillis)}",
                            overdue = item.overdue,
                            onOpen = { editor = HomeEditor.EditReminder(item.reminder.id) },
                            // Ticking a repeating reminder would end the whole series; it shows when it fires next instead.
                            onDone = if (item.reminder.recurrence.isRepeating) null else {
                                {
                                    // The row has already drawn its tick and folded away.
                                    app.appScope.launch {
                                        ReminderNotifier.cancel(context, item.reminder.id)
                                        app.reminders.setDone(item.reminder, true)
                                    }
                                }
                            },
                            trailing = if (item.overdue) null else "in ${Fmt.untilText(item.atMillis, nowMillis)}",
                        )
                    }
                } }
                if (today.size > 6) {
                    Text(
                        "${today.size - 6} more",
                        style = HubType.small, color = colors.inkMuted,
                        modifier = Modifier.padding(start = 70.dp, top = 2.dp, bottom = 4.dp),
                    )
                }
            }

            // What's on the phone's calendars (Google Calendar included).
            UpcomingEventsCard(
                onOpen = { onNavigate(Tab.CALENDAR) },
                modifier = Modifier.padding(horizontal = Gutter).padding(top = 14.dp).staggerIn(stagger, "calendar", 5),
            )

            // Money this month.
            SpendingCard(
                summary = month,
                onViewAll = { onNavigate(Tab.EXPENSES) },
                onAdd = { editor = HomeEditor.NewExpense },
                modifier = Modifier.padding(horizontal = Gutter).padding(top = 14.dp).staggerIn(stagger, "money", 6),
            )

            // Today's body.
            HealthHomeCard(
                onOpen = { onNavigate(Tab.HEALTH) },
                modifier = Modifier.padding(horizontal = Gutter).padding(top = 14.dp).staggerIn(stagger, "health", 7),
            )

            // Clear the floating tab bar and the system navigation bar.
            Spacer(Modifier.height(LocalNavInset.current + 8.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
        // Ground under the status bar so scrolled content never sits behind the clock and icons.
        Spacer(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(colors.ground))
    }

    when (val e = editor) {
        null -> Unit
        is HomeEditor.NewAlarm -> AlarmEditorDialog(
            initial = Alarm(hour = e.hour, minute = e.minute),
            isNew = true,
            onDismiss = { editor = null },
            onSave = { a -> editor = null; saveAlarm(context, a) },
            onDelete = null,
        )
        is HomeEditor.EditAlarm -> alarms.firstOrNull { it.id == e.id }?.let { a ->
            AlarmEditorDialog(
                initial = a,
                isNew = false,
                onDismiss = { editor = null },
                onSave = { x -> editor = null; saveAlarm(context, x) },
                onDelete = { editor = null; app.appScope.launch { app.alarms.delete(a) } },
            )
        }
        HomeEditor.NewReminder -> ReminderEditorDialog(
            initial = ReminderDraft(),
            isNew = true,
            onDismiss = { editor = null },
            onSave = { d -> editor = null; app.appScope.launch { app.reminders.save(0, d.title, d.notes, d.at, d.recurrence) } },
        )
        is HomeEditor.EditReminder -> reminders.firstOrNull { it.id == e.id }?.let { r ->
            ReminderEditorDialog(
                initial = ReminderDraft(r.title, r.notes, r.anchor, r.recurrence),
                isNew = false,
                onDismiss = { editor = null },
                onSave = { d ->
                    editor = null
                    app.appScope.launch { app.reminders.save(r.id, d.title, d.notes, d.at, d.recurrence) }
                },
            )
        }
        HomeEditor.NewExpense -> ExpenseEditorSheet(
            initial = null,
            onDismiss = { editor = null },
            onSave = { x -> editor = null; app.appScope.launch { app.expenses.save(x) } },
            onDelete = null,
        )
    }
}

private fun saveAlarm(context: android.content.Context, alarm: Alarm) {
    val app = context.container
    app.appScope.launch {
        val saved = app.alarms.save(alarm)
        if (saved.enabled) {
            val at = AlarmRepository.nextTrigger(saved).toInstant().toEpochMilli()
            Toast.makeText(context, "Alarm set for ${Fmt.untilText(at)} from now", Toast.LENGTH_SHORT).show()
        }
    }
}

/** A card's title row with a quiet action at the far end. */
@Composable
private fun CardHeader(title: String, action: String, onAction: () -> Unit) {
    val c = Hub.colors
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = HubType.section, color = c.ink, modifier = Modifier.weight(1f))
        Text(
            action,
            style = HubType.small,
            color = c.inkMuted,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(CircleShape)
                .pressable(onClick = onAction, onClickLabel = "$action $title")
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/** A reminder in the Today card: tick it off right here, or tap to edit. */
@Composable
private fun ReminderRow(
    r: Reminder,
    meta: String,
    overdue: Boolean,
    onOpen: () -> Unit,
    onDone: (() -> Unit)?,
    trailing: String?,
) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    // Keyed on the whole reminder: if it comes back changed (edited, or the tick didn't stick),
    // the row starts over visible instead of staying folded away.
    var ticked by remember(r) { mutableStateOf(false) }
    val shown = remember(r) { MutableTransitionState(true) }
    val done by rememberUpdatedState(onDone)
    // Ticked: let the tick draw, then fold the row away so the ones below glide up.
    LaunchedEffect(shown, ticked) {
        if (ticked) {
            delay(if (reduced) 150L else 300L)
            shown.targetState = false
        }
    }
    // Only once it has fully folded away; fires once per tick. Normally the reminder then leaves
    // the list and this row with it; if it is still here a while later, the tick didn't stick,
    // so the row comes back instead of staying invisible.
    LaunchedEffect(shown) {
        while (true) {
            snapshotFlow { shown.isIdle && !shown.currentState }.first { it }
            done?.invoke()
            delay(2_000)
            ticked = false
            shown.targetState = true
        }
    }
    AnimatedVisibility(
        visibleState = shown,
        enter = fadeIn(tween(150)),
        exit = if (reduced) fadeOut(tween(150))
        else shrinkVertically(tween(200, easing = Motion.EaseOut)) + fadeOut(tween(120)),
    ) {
    Row(
        Modifier
            .padding(horizontal = 4.dp)
            .fillMaxWidth()
            // As tall as a HubListRow, so alarms and reminders line up in the Today card.
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(18.dp))
            .pressable(onClick = onOpen, pressedScale = 0.98f, showRipple = false)
            .padding(start = 12.dp, end = 0.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(c.well), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = if (overdue) c.danger else c.sky, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.title, style = HubType.bodyStrong.copy(fontSize = HubType.small.fontSize * 1.17f), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = HubType.small.copy(fontSize = HubType.small.fontSize * 0.92f), color = if (overdue) c.danger else c.inkMuted, maxLines = 1)
        }
        if (onDone != null) {
            CheckDot(
                checked = ticked,
                // Once only: a second tap while the row folds away does nothing.
                onCheckedChange = { if (it && !ticked) ticked = true },
                accent = c.ok,
                size = 24.dp,
                contentDescription = "Mark ${r.title} done",
            )
        } else if (trailing != null) {
            Text(
                trailing, style = HubType.clockSmall.copy(fontSize = 14.sp), color = c.ink, maxLines = 1,
                modifier = Modifier.padding(start = 8.dp, end = 12.dp),
            )
        }
    }
    }
}

/** This month's money out and in, and the latest entries, like a wallet's recent activity. */
@Composable
private fun SpendingCard(summary: MonthSummary?, onViewAll: () -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val c = Hub.colors
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }
    Column(modifier.fillMaxWidth().hubCard(c.raised).padding(bottom = 8.dp)) {
        CardHeader("Money", "View all", onViewAll)
        if (summary != null) {
            Row(Modifier.padding(horizontal = 16.dp).padding(bottom = 6.dp), verticalAlignment = Alignment.Bottom) {
                // Rolls to the new total after an expense is added, instead of snapping.
                RollingText(Money.format(summary.totalPaise), HubType.clockMedium.copy(fontSize = HubType.clockMedium.fontSize * 0.82f), color = c.ink)
                Spacer(Modifier.width(8.dp))
                val budget = summary.budgetPaise?.takeIf { it > 0 }
                val over = budget != null && summary.totalPaise > budget
                Text(
                    when {
                        budget == null -> "spent this month"
                        over -> "${Money.format(summary.totalPaise - budget)} over budget"
                        else -> "spent of ${Money.format(budget)}"
                    },
                    style = HubType.small, color = if (over) c.danger else c.inkMuted,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            if (summary.incomePaise > 0) {
                Text(
                    "+${Money.format(summary.incomePaise)} received • net ${signed(summary.netPaise)}",
                    style = HubType.small, color = c.ok,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 6.dp),
                )
            }
        }
        val recent = summary?.entries?.take(4).orEmpty()
        if (summary != null && recent.isEmpty()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Nothing spent or received yet.", style = HubType.body, color = c.inkMuted, modifier = Modifier.weight(1f))
                QuietButton("Add", onClick = onAdd)
            }
        }
        recent.forEach { e ->
            val at = Instant.ofEpochMilli(e.spentAt).atZone(zone)
            HubListRow(
                icon = e.category.icon,
                title = e.note.ifBlank { e.category.label },
                meta = "${Fmt.time(context, at.toLocalTime())} • ${at.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()))}",
                trailing = if (e.isIncome) "+${Money.format(e.amountPaise)}" else Money.format(e.amountPaise),
                trailingColor = if (e.isIncome) c.ok else c.ink,
                iconTint = e.category.color(c),
                iconTone = e.category.soft(c),
                onClick = onViewAll,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** "+₹1,200" or "-₹300". */
private fun signed(paise: Long): String = if (paise >= 0) "+${Money.format(paise)}" else Money.format(paise)

@Composable
private fun SetupNudge(done: Int, total: Int, pending: Int, onClick: () -> Unit) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    // The ring fills to the new share when a step is finished, instead of jumping.
    val progress by animateFloatAsState(
        done.toFloat() / total.coerceAtLeast(1),
        if (reduced) snap() else tween(400, easing = Motion.EaseOut),
        label = "setupProgress",
    )
    Row(
        Modifier
            .padding(horizontal = Gutter)
            .padding(top = 14.dp)
            .fillMaxWidth()
            // Press first, so the whole card shrinks, not just its contents.
            .pressable(onClick = onClick, pressedScale = 0.98f, showRipple = false)
            .hubCard(c.raised)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(30.dp)) {
            val s = 4.dp.toPx()
            drawCircle(c.well, radius = size.minDimension / 2 - s / 2, style = Stroke(s))
            drawArc(
                c.dawn, -90f, 360f * progress, false,
                topLeft = Offset(s / 2, s / 2), size = Size(size.width - s, size.height - s),
                style = Stroke(s, cap = StrokeCap.Round),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Finish setup", style = HubType.bodyStrong, color = c.ink)
            Text(
                "$pending step${if (pending == 1) "" else "s"} left so alarms ring on time",
                style = HubType.small, color = c.inkMuted,
            )
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = c.ink)
    }
}

/** A small filled circle, used as a legend key. */
@Composable
fun Dot(color: Color, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Canvas(modifier.size(size)) { drawCircle(color) }
}

private fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> d.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
}

private fun greeting(t: LocalTime): String = when (t.hour) {
    in 5..11 -> "Good morning!"
    in 12..16 -> "Good afternoon!"
    in 17..21 -> "Good evening!"
    else -> "Good night!"
}
