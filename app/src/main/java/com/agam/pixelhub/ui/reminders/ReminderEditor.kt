@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.agam.pixelhub.ui.reminders

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerColors
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agam.pixelhub.scheduling.Days
import com.agam.pixelhub.scheduling.Recurrence
import com.agam.pixelhub.scheduling.RecurrenceCalc
import com.agam.pixelhub.scheduling.RepeatType
import com.agam.pixelhub.ui.CardRadius
import com.agam.pixelhub.ui.DaysPicker
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.HubSheet
import com.agam.pixelhub.ui.HubTextField
import com.agam.pixelhub.ui.PrimaryButton
import com.agam.pixelhub.ui.QuietButton
import com.agam.pixelhub.ui.TimeSelectDialog
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.RollingText
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.motion.revealEnter
import com.agam.pixelhub.ui.motion.revealExit
import com.agam.pixelhub.ui.motion.toggleSpec
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class ReminderDraft(
    val title: String = "",
    val notes: String = "",
    val at: LocalDateTime = defaultReminderTime(),
    val recurrence: Recurrence = Recurrence.NONE,
)

fun defaultReminderTime(): LocalDateTime = LocalDateTime.now().plusHours(1).truncatedTo(ChronoUnit.HOURS)

private const val MAX_INTERVAL = 365

private fun repeatLabel(type: RepeatType) = when (type) {
    RepeatType.NONE -> "Doesn't repeat"
    RepeatType.DAILY -> "Every day"
    RepeatType.WEEKLY -> "Certain days"
    RepeatType.EVERY_N_DAYS -> "Every few days"
    RepeatType.EVERY_N_WEEKS -> "Every few weeks"
}

/** Create or edit a reminder. Also used by the assistant to confirm what it understood. */
@Composable
fun ReminderEditorDialog(
    initial: ReminderDraft,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (ReminderDraft) -> Unit,
) {
    ReminderEditorSheet(initial, isNew, onDismiss, onSave, onDelete = null)
}

/** The editor sheet. [onDelete] adds a two-tap delete button to the header. */
@Composable
internal fun ReminderEditorSheet(
    initial: ReminderDraft,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (ReminderDraft) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var title by rememberSaveable { mutableStateOf(initial.title) }
    var notes by rememberSaveable { mutableStateOf(initial.notes) }
    var date by rememberSaveable { mutableStateOf(initial.at.toLocalDate()) }
    var time by rememberSaveable { mutableStateOf(initial.at.toLocalTime()) }
    var type by rememberSaveable { mutableStateOf(initial.recurrence.type) }
    var mask by rememberSaveable { mutableIntStateOf(initial.recurrence.daysMask) }
    var interval by rememberSaveable {
        val r = initial.recurrence
        val custom = r.type == RepeatType.EVERY_N_DAYS || r.type == RepeatType.EVERY_N_WEEKS
        mutableIntStateOf(if (custom) r.interval.coerceIn(1, MAX_INTERVAL) else 2)
    }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var showTime by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    // Keeps the last message on screen while the error line collapses.
    val lastError = remember { arrayOf("") }
    error?.let { lastError[0] = it }
    val shake = remember { Animatable(0f) }

    // Keeps "in 2h 5m" honest while the sheet is open.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(15_000)
            value = System.currentTimeMillis()
        }
    }
    val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()

    // Any edit clears the last complaint.
    LaunchedEffect(title, date, time, type, mask, interval) { error = null }

    fun recurrence() = Recurrence(
        type = type,
        daysMask = if (type == RepeatType.WEEKLY) mask else 0,
        interval = if (type == RepeatType.EVERY_N_DAYS || type == RepeatType.EVERY_N_WEEKS) interval.coerceAtLeast(1) else 1,
    )

    fun build(): ReminderDraft? {
        val at = LocalDateTime.of(date, time)
        val rec = recurrence()
        error = when {
            title.isBlank() -> "Give the reminder a title"
            type == RepeatType.NONE && !at.isAfter(LocalDateTime.now()) -> "Pick a time in the future"
            type == RepeatType.WEEKLY && (mask and Days.ALL) == 0 -> "Pick at least one day"
            else -> null
        }
        return if (error == null) ReminderDraft(title.trim(), notes.trim(), at, rec) else null
    }

    fun save() {
        val draft = build()
        if (draft != null) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onSave(draft)
        } else {
            haptics.performHapticFeedback(HapticFeedbackType.Reject)
            if (!reduced) {
                scope.launch {
                    shake.snapTo(0f)
                    // A kick of velocity into a loose spring: a few quick, shrinking wobbles.
                    shake.animateTo(0f, spring(dampingRatio = 0.2f, stiffness = 1100f), initialVelocity = 360f)
                }
            }
        }
    }

    // When it next fires, for the "in 3h 20m" line under the time.
    val nextMillis = remember(date, time, type, mask, interval, now) {
        RecurrenceCalc.nextTrigger(recurrence(), LocalDateTime.of(date, time), ZonedDateTime.now())
            ?.toInstant()?.toEpochMilli()
    }

    val custom = type == RepeatType.EVERY_N_DAYS || type == RepeatType.EVERY_N_WEEKS
    val dirty = title != initial.title || notes != initial.notes ||
        LocalDateTime.of(date, time) != initial.at || type != initial.recurrence.type ||
        (type == RepeatType.WEEKLY && mask != initial.recurrence.daysMask) ||
        (custom && interval != initial.recurrence.interval)

    HubSheet(onDismiss = onDismiss, dirty = dirty) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(c.sun),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = c.onSun, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    if (isNew) "New reminder" else "Edit reminder",
                    style = HubType.title.copy(fontSize = 24.sp, lineHeight = 30.sp),
                    color = c.ink,
                    modifier = Modifier.weight(1f),
                )
                if (onDelete != null) DeleteButton(onDelete)
            }

            Spacer(Modifier.height(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HubTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "Title",
                    placeholder = "What should I remind you about?",
                    isError = error != null && title.isBlank(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next,
                    ),
                )
                HubTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "Notes",
                    placeholder = "Optional",
                    singleLine = false,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            }

            Spacer(Modifier.height(22.dp))
            Text("When", style = HubType.section, color = c.ink)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val word = relativeWord(date, today)
                val days = ChronoUnit.DAYS.between(today, date)
                ValueChip(
                    icon = Icons.Outlined.CalendarMonth,
                    description = "Date",
                    sub = when {
                        word != null -> shortDate(date, today)
                        days > 1 -> "In $days days"
                        else -> "${-days} days ago"
                    },
                    subColor = c.inkMuted,
                    onClick = { showDate = true },
                    modifier = Modifier.weight(1f),
                ) {
                    AnimatedContent(
                        targetState = date,
                        transitionSpec = { roll(targetState.isAfter(initialState), reduced) },
                        label = "dateRoll",
                    ) { d ->
                        Text(
                            relativeWord(d, today) ?: shortDate(d, today),
                            style = HubType.section,
                            color = c.ink,
                            maxLines = 1,
                        )
                    }
                }
                val past = nextMillis == null && type == RepeatType.NONE
                ValueChip(
                    icon = Icons.Outlined.Schedule,
                    description = "Time",
                    sub = when {
                        nextMillis != null -> "In ${Fmt.untilText(nextMillis, now)}"
                        past -> "That time has passed"
                        else -> "Pick a day below"
                    },
                    subColor = if (past) c.danger else c.inkMuted,
                    onClick = { showTime = true },
                    modifier = Modifier.weight(1f),
                ) {
                    RollingText(
                        Fmt.time(context, time),
                        style = HubType.clockSmall.copy(fontSize = 28.sp, lineHeight = 30.sp),
                        color = c.ink,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            QuickDates(
                date = date,
                today = today,
                onPick = { date = it },
            )

            Spacer(Modifier.height(26.dp))
            Column {
                Text("Repeat", style = HubType.section, color = c.ink)
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RepeatType.entries.forEach { t ->
                        RepeatPill(repeatLabel(t), selected = t == type) {
                            type = t
                            if (t == RepeatType.WEEKLY && (mask and Days.ALL) == 0) mask = Days.bit(date.dayOfWeek)
                        }
                    }
                }
                AnimatedVisibility(
                    visible = type == RepeatType.WEEKLY,
                    enter = revealEnter(reduced),
                    exit = revealExit(reduced),
                ) {
                    Column {
                        Spacer(Modifier.height(14.dp))
                        DaysPicker(mask, onChange = { mask = it }, accent = c.sky)
                    }
                }
                AnimatedVisibility(
                    visible = type == RepeatType.EVERY_N_DAYS || type == RepeatType.EVERY_N_WEEKS,
                    enter = revealEnter(reduced),
                    exit = revealExit(reduced),
                ) {
                    Column {
                        Spacer(Modifier.height(14.dp))
                        IntervalStepper(
                            value = interval,
                            weeks = type == RepeatType.EVERY_N_WEEKS,
                            onChange = { interval = it.coerceIn(1, MAX_INTERVAL) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Summary(
                text = summaryText(context, date, time, today, type, mask, interval),
            )

            AnimatedVisibility(
                visible = error != null,
                enter = revealEnter(reduced),
                exit = revealExit(reduced),
            ) {
                Text(
                    error ?: lastError[0],
                    style = HubType.bodyStrong,
                    color = c.danger,
                    modifier = Modifier.padding(top = 14.dp, start = 4.dp),
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuietButton("Cancel", onClick = onDismiss)
                PrimaryButton(
                    text = "Save reminder",
                    onClick = { save() },
                    icon = Icons.Outlined.Check,
                    modifier = Modifier
                        .weight(1f)
                        .graphicsLayer { translationX = shake.value.dp.toPx() },
                )
            }
        }
    }

    if (showDate) {
        // DatePicker works in UTC midnight millis.
        val todayUtc = remember { LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        val thisYear = remember { LocalDate.now().year }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = remember {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtc
                    override fun isSelectableYear(year: Int) = year >= thisYear
                }
            },
        )
        val colors = hubDatePickerColors()
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                QuietButton("Set date", color = c.sky, onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                })
            },
            dismissButton = { QuietButton("Cancel", color = c.inkMuted, onClick = { showDate = false }) },
            shape = RoundedCornerShape(32.dp),
            tonalElevation = 0.dp,
            colors = colors,
        ) {
            DatePicker(state = state, colors = colors)
        }
    }
    if (showTime) {
        TimeSelectDialog(
            initial = time,
            onDismiss = { showTime = false },
            onConfirm = { time = it; showTime = false },
            accent = c.sky,
        )
    }
}

/** The date picker in the PixelHub palette: sky selection, no divider. */
@Composable
private fun hubDatePickerColors(): DatePickerColors {
    val c = Hub.colors
    return DatePickerDefaults.colors(
        containerColor = c.raised,
        titleContentColor = c.inkMuted,
        headlineContentColor = c.ink,
        weekdayContentColor = c.inkMuted,
        subheadContentColor = c.inkMuted,
        navigationContentColor = c.ink,
        yearContentColor = c.ink,
        disabledYearContentColor = c.inkMuted.copy(alpha = 0.4f),
        currentYearContentColor = c.sky,
        selectedYearContentColor = c.onSky,
        selectedYearContainerColor = c.sky,
        dayContentColor = c.ink,
        disabledDayContentColor = c.inkMuted.copy(alpha = 0.4f),
        selectedDayContentColor = c.onSky,
        selectedDayContainerColor = c.sky,
        todayContentColor = c.sky,
        todayDateBorderColor = c.sky,
        dividerColor = Color.Transparent,
    )
}

/** Odometer-style swap: a later value rolls up, an earlier one rolls down. */
private fun roll(up: Boolean, reduced: Boolean): ContentTransform =
    if (reduced) {
        fadeIn(tween(120)) togetherWith fadeOut(tween(120))
    } else {
        val dir = if (up) 1 else -1
        (slideInVertically(tween(260, easing = Motion.EaseOut)) { h -> dir * h } + fadeIn(tween(200))) togetherWith
            (slideOutVertically(tween(220, easing = Motion.EaseOut)) { h -> -dir * h } + fadeOut(tween(140)))
    }

/** A big tappable value: icon, the value itself, and one quiet line under it. */
@Composable
private fun ValueChip(
    icon: ImageVector,
    description: String,
    sub: String,
    subColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: @Composable () -> Unit,
) {
    val c = Hub.colors
    Column(
        modifier
            .pressable(onClick = onClick, pressedScale = 0.97f, onClickLabel = "Change ${description.lowercase()}", showRipple = false)
            .clip(RoundedCornerShape(CardRadius))
            .background(c.raised2)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(c.sun), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = c.onSun, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(description, style = HubType.small, color = c.inkMuted, maxLines = 1)
        }
        Spacer(Modifier.height(10.dp))
        value()
        Spacer(Modifier.height(2.dp))
        AnimatedContent(
            targetState = sub,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "chipSub",
        ) { s ->
            Text(s, style = HubType.small, color = subColor, maxLines = 1)
        }
    }
}

/**
 * Today / Tomorrow / Next week. The sky indicator slides to the choice and stretches on the
 * way: its leading edge moves on a stiffer spring than its trailing edge.
 */
@Composable
private fun QuickDates(date: LocalDate, today: LocalDate, onPick: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val options = listOf("Today" to today, "Tomorrow" to today.plusDays(1), "Next week" to today.plusWeeks(1))
    val picked = options.indexOfFirst { it.second == date }
    val left = remember { Animatable(picked.coerceAtLeast(0).toFloat()) }
    val right = remember { Animatable(picked.coerceAtLeast(0) + 1f) }
    val shown by animateFloatAsState(if (picked >= 0) 1f else 0f, tween(200), label = "quickShown")
    LaunchedEffect(picked) {
        if (picked < 0) return@LaunchedEffect
        val target = picked.toFloat()
        // Hidden or reduced: appear in place rather than sliding from a stale spot.
        if (reduced || shown < 0.05f) {
            left.snapTo(target)
            right.snapTo(target + 1f)
            return@LaunchedEffect
        }
        val forward = target > left.value
        val lead = spring<Float>(dampingRatio = 0.8f, stiffness = 900f)
        val trail = spring<Float>(dampingRatio = 0.85f, stiffness = 320f)
        launch { left.animateTo(target, if (forward) trail else lead) }
        launch { right.animateTo(target + 1f, if (forward) lead else trail) }
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(CircleShape)
            .background(c.raised2)
            .padding(4.dp)
            .drawBehind {
                if (shown <= 0f) return@drawBehind
                val slot = size.width / options.size
                drawRoundRect(
                    color = c.sky.copy(alpha = shown),
                    topLeft = Offset(slot * left.value, 0f),
                    size = Size(slot * (right.value - left.value), size.height),
                    cornerRadius = CornerRadius(size.height / 2, size.height / 2),
                )
            },
    ) {
        options.forEachIndexed { i, (label, d) ->
            val fg by animateColorAsState(if (i == picked) c.onSky else c.ink, tween(200), label = "quickFg")
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .pressable(onClick = { onPick(d) }, role = Role.RadioButton, showRipple = false)
                    .semantics { selected = i == picked },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = HubType.button, color = fg, maxLines = 1)
            }
        }
    }
}

@Composable
private fun RepeatPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Hub.colors
    val bg by animateColorAsState(if (selected) c.sky else c.raised2, Motion.toggleSpec(), label = "pillBg")
    val fg by animateColorAsState(if (selected) c.onSky else c.ink, Motion.toggleSpec(), label = "pillFg")
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .pressable(onClick = onClick, pressedScale = 0.94f, role = Role.RadioButton, showRipple = false)
            .clip(CircleShape)
            .background(bg)
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = HubType.button.copy(fontSize = 14.sp), color = fg, maxLines = 1)
    }
}

/** "Every – 3 + days": the number rolls as it changes. */
@Composable
private fun IntervalStepper(value: Int, weeks: Boolean, onChange: (Int) -> Unit) {
    val c = Hub.colors
    val unit = when {
        weeks && value == 1 -> "week"
        weeks -> "weeks"
        value == 1 -> "day"
        else -> "days"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius))
            .background(c.raised2)
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Every", style = HubType.body, color = c.ink)
        Spacer(Modifier.weight(1f))
        StepButton(Icons.Outlined.Remove, "Fewer", enabled = value > 1) { onChange(value - 1) }
        Box(Modifier.widthIn(min = 60.dp), contentAlignment = Alignment.Center) {
            RollingText(
                value.toString(),
                style = HubType.clockSmall.copy(fontSize = 30.sp, lineHeight = 34.sp),
                color = c.ink,
            )
        }
        StepButton(Icons.Outlined.Add, "More", enabled = value < MAX_INTERVAL) { onChange(value + 1) }
        Spacer(Modifier.weight(1f))
        AnimatedContent(
            targetState = unit,
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false) },
            label = "unit",
        ) { u ->
            Text(u, style = HubType.body, color = c.ink, modifier = Modifier.widthIn(min = 44.dp))
        }
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Hub.colors
    val alpha by animateFloatAsState(if (enabled) 1f else 0.35f, tween(160), label = "stepAlpha")
    Box(
        Modifier
            .size(48.dp)
            .graphicsLayer { this.alpha = alpha }
            .pressable(onClick = onClick, enabled = enabled, pressedScale = 0.88f, onClickLabel = description, showRipple = false)
            .clip(CircleShape)
            .background(c.raised)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
    }
}

/** One line in plain words saying what will happen. Crossfades as you change things. */
@Composable
private fun Summary(text: String, modifier: Modifier = Modifier) {
    val c = Hub.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardRadius))
            .background(c.sun)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(c.onSun.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Schedule, contentDescription = null, tint = c.onSun, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false)
            },
            label = "summary",
        ) { t ->
            Text(t, style = HubType.bodyStrong, color = c.onSun)
        }
    }
}

/** Delete needs two taps: the first turns the button red and says "Delete". */
@Composable
private fun DeleteButton(onDelete: () -> Unit) {
    val c = Hub.colors
    val haptics = LocalHapticFeedback.current
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3_000)
            armed = false
        }
    }
    val bg by animateColorAsState(if (armed) c.danger else c.dangerSoft, Motion.toggleSpec(), label = "delBg")
    val fg by animateColorAsState(if (armed) c.onDanger else c.danger, Motion.toggleSpec(), label = "delFg")
    Row(
        Modifier
            .height(48.dp)
            .pressable(
                onClick = {
                    if (armed) {
                        onDelete()
                    } else {
                        armed = true
                        haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    }
                },
                onClickLabel = if (armed) "Delete reminder" else "Delete",
                showRipple = false,
            )
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Delete,
            contentDescription = if (armed) "Tap again to delete" else "Delete reminder",
            tint = fg,
            modifier = Modifier.size(20.dp),
        )
        AnimatedVisibility(
            visible = armed,
            enter = fadeIn(tween(160)) + expandHorizontally(Motion.snappy()),
            exit = fadeOut(tween(100)) + shrinkHorizontally(Motion.snappy()),
        ) {
            Text("Delete", style = HubType.button, color = fg, modifier = Modifier.padding(start = 8.dp, end = 4.dp))
        }
    }
}

/** "Today", "Tomorrow", "Yesterday", or null for any other day. */
private fun relativeWord(date: LocalDate, today: LocalDate): String? = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    today.minusDays(1) -> "Yesterday"
    else -> null
}

private fun shortDate(date: LocalDate, today: LocalDate): String = date.format(
    DateTimeFormatter.ofPattern(if (date.year == today.year) "EEE d MMM" else "EEE d MMM yyyy", Locale.getDefault()),
)

/** "Tomorrow at 6:00 PM, then every 3 days", "Weekdays at 7:30 AM, starting tomorrow". */
private fun summaryText(
    context: android.content.Context,
    date: LocalDate,
    time: LocalTime,
    today: LocalDate,
    type: RepeatType,
    mask: Int,
    interval: Int,
): String {
    val word = relativeWord(date, today)
    val day = word ?: shortDate(date, today)
    val at = Fmt.time(context, time)
    val starting = when {
        date == today -> ""
        word != null -> ", starting ${word.lowercase()}"
        else -> ", starting $day"
    }
    return when (type) {
        RepeatType.NONE -> "$day at $at"
        RepeatType.DAILY -> "Every day at $at$starting"
        RepeatType.WEEKLY ->
            if ((mask and Days.ALL) == 0) "Pick the days it repeats on" else "${Days.label(mask)} at $at$starting"
        RepeatType.EVERY_N_DAYS -> "$day at $at, then every ${if (interval <= 1) "day" else "$interval days"}"
        RepeatType.EVERY_N_WEEKS -> "$day at $at, then every ${if (interval <= 1) "week" else "$interval weeks"}"
    }
}

/** Keeps an open "new reminder" draft through rotation and theme changes. */
val ReminderDraftSaver: Saver<ReminderDraft?, ArrayList<Any>> = Saver(
    save = { d ->
        d?.let { arrayListOf(it.title, it.notes, it.at.toString(), it.recurrence.type.name, it.recurrence.daysMask, it.recurrence.interval) }
    },
    restore = { l ->
        ReminderDraft(
            title = l[0] as String,
            notes = l[1] as String,
            at = LocalDateTime.parse(l[2] as String),
            recurrence = Recurrence(RepeatType.valueOf(l[3] as String), l[4] as Int, l[5] as Int),
        )
    },
)
