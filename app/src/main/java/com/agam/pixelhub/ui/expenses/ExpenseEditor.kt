@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.agam.pixelhub.ui.expenses

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agam.pixelhub.data.Expense
import com.agam.pixelhub.data.ExpenseCategory
import com.agam.pixelhub.expenses.Money
import com.agam.pixelhub.ui.CircleIconButton
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
import com.agam.pixelhub.ui.motion.rememberStagger
import com.agam.pixelhub.ui.motion.staggerIn
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
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Titles of the Expenses sheets. */
internal val SheetTitle = HubType.title.copy(fontSize = 22.sp, lineHeight = 28.sp)

/**
 * Add or edit an expense in a sheet, by hand and fast: a big amount typed on the sheet's own
 * keypad (no system keyboard), the category as a row of wells, an optional note, when it
 * happened, and an ink Save. A keystroke that can't be an amount shakes the display.
 *
 * [initial] null, or with id 0, is a new expense (a draft from the Assistant, a past month or
 * "Repeat" keeps its fields).
 */
@Composable
fun ExpenseEditorSheet(
    initial: Expense?,
    onDismiss: () -> Unit,
    onSave: (Expense) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val stagger = rememberStagger()
    val zone = remember { ZoneId.systemDefault() }
    val isNew = initial == null || initial.id == 0L

    var amount by rememberSaveable { mutableStateOf(initial?.amountPaise?.takeIf { it > 0L }?.let { plainAmount(it) }.orEmpty()) }
    var category by rememberSaveable { mutableStateOf(initial?.category ?: ExpenseCategory.FOOD) }
    var note by rememberSaveable { mutableStateOf(initial?.note.orEmpty()) }
    val initialAt = remember { initial?.spentAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() } }
    // A draft stamped within the last minute ("Repeat") counts as "now".
    var useNow by rememberSaveable {
        mutableStateOf(initial == null || (initial.id == 0L && abs(initial.spentAt - System.currentTimeMillis()) < 60_000L))
    }
    var date by rememberSaveable { mutableStateOf(initialAt?.toLocalDate() ?: LocalDate.now()) }
    var time by rememberSaveable { mutableStateOf((initialAt?.toLocalTime() ?: LocalTime.now()).withSecond(0).withNano(0)) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var showTime by rememberSaveable { mutableStateOf(false) }
    val today = remember { LocalDate.now() }

    val paise = Money.parse(amount)
    val valid = paise != null && paise > 0L

    // Rejected keystrokes: a small spring shake and a brief danger tint.
    val shake = remember { Animatable(0f) }
    var flash by remember { mutableStateOf(false) }
    fun reject() {
        haptics.performHapticFeedback(HapticFeedbackType.Reject)
        scope.launch {
            flash = true
            if (reduced) {
                delay(240)
            } else {
                shake.snapTo(0f)
                shake.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = 2200f), initialVelocity = 480f)
            }
            flash = false
        }
    }
    fun type(ch: Char) {
        val next = sanitizeAmount(amount + ch)
        if (next == null) reject() else amount = next
    }

    // Start the category row on the chosen category when it's off to the right.
    val catScroll = rememberScrollState()
    LaunchedEffect(Unit) {
        val i = category.ordinal
        if (i > 3) catScroll.scrollTo(with(density) { ((i - 1) * 76).dp.roundToPx() })
    }

    HubSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().staggerIn(stagger, "title", 0), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isNew) "New expense" else "Edit expense",
                    style = SheetTitle,
                    color = c.ink,
                    modifier = Modifier.weight(1f),
                )
                if (onDelete != null && !isNew) {
                    CircleIconButton(
                        icon = Icons.Outlined.Delete,
                        contentDescription = "Delete expense",
                        onClick = onDelete,
                        tone = c.dangerSoft,
                        tint = c.danger,
                        size = 44.dp,
                        iconSize = 20.dp,
                    )
                }
            }

            // The display.
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 92.dp)
                    .staggerIn(stagger, "amount", 1)
                    .semantics(mergeDescendants = true) {
                        contentDescription = if (amount.isEmpty()) "Amount, empty" else "Amount ₹${groupedAmount(amount)}"
                    },
                contentAlignment = Alignment.Center,
            ) {
                AmountDisplay(
                    text = amount,
                    accent = category.color(c),
                    flash = flash,
                    modifier = Modifier.graphicsLayer { translationX = shake.value * density.density },
                )
            }

            // Category, as a row of wells.
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(catScroll)
                    .staggerIn(stagger, "cats", 2),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ExpenseCategory.entries.forEach { cat ->
                    CategoryChip(cat, selected = cat == category, onClick = { category = cat })
                }
            }

            Keypad(
                onType = { type(it) },
                onBack = { if (amount.isNotEmpty()) amount = amount.dropLast(1) else reject() },
                onClear = { amount = "" },
                modifier = Modifier.padding(top = 14.dp).staggerIn(stagger, "keys", 3),
            )

            HubTextField(
                value = note,
                onValueChange = { note = it },
                label = "Note",
                placeholder = "Lunch, cab home, phone bill",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.padding(top = 18.dp).staggerIn(stagger, "note", 4),
            )

            FlowRow(
                Modifier.fillMaxWidth().padding(top = 14.dp).staggerIn(stagger, "when", 5),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WhenPill("Now", selected = useNow, onClick = { useNow = true })
                WhenPill(
                    "Earlier today",
                    selected = !useNow && date == today,
                    onClick = {
                        val now = LocalTime.now().withSecond(0).withNano(0)
                        if (useNow || date != today || time.isAfter(now)) time = now.minusHours(1).coerceAtMost(now)
                        date = today
                        useNow = false
                        showTime = true
                    },
                )
                WhenPill(
                    if (!useNow && date != today) dayText(date, today) else "Pick a date",
                    selected = !useNow && date != today,
                    icon = Icons.Outlined.CalendarToday,
                    onClick = { showDate = true },
                )
                AnimatedVisibility(
                    visible = !useNow,
                    enter = fadeIn(tween(160)) + scaleIn(if (reduced) tween(0) else Motion.snappy(), initialScale = 0.85f),
                    exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.9f),
                ) {
                    ValuePill(
                        text = Fmt.time(context, time),
                        icon = Icons.Outlined.Schedule,
                        description = "Change time",
                        onClick = { showTime = true },
                    )
                }
            }

            PrimaryButton(
                text = if (isNew) "Save expense" else "Save changes",
                enabled = valid,
                onClick = {
                    val p = Money.parse(amount)
                    if (p == null || p <= 0L) {
                        reject()
                        return@PrimaryButton
                    }
                    val at = if (useNow) {
                        System.currentTimeMillis()
                    } else {
                        LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli()
                    }
                    onSave(
                        Expense(
                            id = initial?.id ?: 0L,
                            amountPaise = p,
                            category = category,
                            note = note.trim(),
                            spentAt = at,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp).staggerIn(stagger, "save", 6),
            )
        }
    }

    if (showDate) {
        val todayUtc = remember { LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        val thisYear = remember { LocalDate.now().year }
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = remember {
                object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
                    override fun isSelectableYear(year: Int) = year <= thisYear
                }
            },
        )
        val colors = inkDatePickerColors()
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                QuietButton("Set date", color = c.ink, onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                        useNow = false
                    }
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
            onConfirm = {
                // An expense can't be later than now; a later time today is clamped to the current minute.
                val nowTime = LocalTime.now().withSecond(0).withNano(0)
                time = if (date == LocalDate.now() && it.isAfter(nowTime)) nowTime else it
                useNow = false
                showTime = false
            },
            accent = c.ink,
        )
    }
}

/**
 * ₹ in the category colour, then the figures rolling digit by digit. The numerals shrink on
 * a spring as the amount grows so it always fits on one line.
 */
@Composable
private fun AmountDisplay(
    text: String,
    accent: Color,
    flash: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val empty = text.isEmpty()
    val shown = if (empty) "0" else groupedAmount(text)
    val size by animateFloatAsState(
        when {
            shown.length <= 6 -> 64f
            shown.length <= 9 -> 52f
            else -> 42f
        },
        if (reduced) tween(0) else Motion.snappy(),
        label = "amountSize",
    )
    val digits by animateColorAsState(
        when {
            flash -> c.danger
            empty -> c.inkMuted
            else -> c.ink
        },
        tween(if (flash) 60 else 220),
        label = "amountColor",
    )
    val rupee by animateColorAsState(if (flash) c.danger else accent, tween(200), label = "rupeeColor")
    val style = HubType.clockLarge.copy(fontSize = size.sp, lineHeight = (size * 1.15f).sp)

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            "₹",
            style = HubType.clockMedium.copy(fontSize = (size * 0.55f).sp, lineHeight = (size * 0.6f).sp),
            color = rupee,
            modifier = Modifier.padding(end = 6.dp),
        )
        RollingText(shown, style = style, color = digits)
    }
}

/** A category: its well with the short name under it. Choosing it fills the well and it pops. */
@Composable
private fun CategoryChip(category: ExpenseCategory, selected: Boolean, onClick: () -> Unit) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected && !reduced) {
            pop.animateTo(1.08f, spring(dampingRatio = 1f, stiffness = 1600f))
            pop.animateTo(1f, Motion.snappy())
        }
    }
    val label by animateColorAsState(if (selected) c.ink else c.inkMuted, tween(180), label = "chipLabel")
    Column(
        Modifier
            .width(72.dp)
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .pressable(onClick = onClick, pressedScale = 0.92f, role = Role.RadioButton, showRipple = false, onClickLabel = category.label)
            .semantics { this.selected = selected }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CategoryWell(category, size = 52.dp, selected = selected)
        Spacer(Modifier.height(6.dp))
        Text(
            category.shortLabel,
            style = HubType.small.copy(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal),
            color = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Digits in round wells, 1–9, then point, 0 and delete. Holding delete clears the amount. */
@Composable
private fun Keypad(onType: (Char) -> Unit, onBack: () -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("123", "456", "789").forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { ch -> Key(ch.toString(), description = ch.toString(), onClick = { onType(ch) }) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key(".", description = "Decimal point", onClick = { onType('.') })
            Key("0", description = "0", onClick = { onType('0') })
            Key(
                null,
                icon = Icons.AutoMirrored.Outlined.Backspace,
                description = "Delete last digit, hold to clear",
                onClick = onBack,
                onLongClick = onClear,
            )
        }
    }
}

@Composable
private fun RowScope.Key(
    text: String?,
    description: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val c = Hub.colors
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .weight(1f)
            .height(52.dp)
            .pressable(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                },
                onLongClick = onLongClick,
                pressedScale = 0.92f,
                showRipple = false,
                onClickLabel = description,
            )
            .clip(CircleShape)
            .background(c.well)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(22.dp))
        } else if (text != null) {
            Text(text, style = HubType.clockSmall.copy(fontSize = 22.sp, lineHeight = 28.sp), color = c.ink)
        }
    }
}

/** One of the "when" choices; the selected one fills with ink and pops. */
@Composable
private fun WhenPill(text: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val bg by animateColorAsState(if (selected) c.ink else c.raised2, tween(180), label = "whenBg")
    val fg by animateColorAsState(if (selected) c.ground else c.ink, tween(180), label = "whenFg")
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected && !reduced) {
            pop.animateTo(1.06f, spring(dampingRatio = 1f, stiffness = 1600f))
            pop.animateTo(1f, Motion.snappy())
        }
    }
    Row(
        Modifier
            .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
            .pressable(onClick = onClick, pressedScale = 0.93f, role = Role.RadioButton, showRipple = false)
            .semantics { this.selected = selected }
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = HubType.button.copy(fontFeatureSettings = "tnum"), color = fg)
    }
}

/** A value you can change: the time, on raised2. */
@Composable
private fun ValuePill(text: String, icon: ImageVector, description: String, onClick: () -> Unit) {
    val c = Hub.colors
    Row(
        Modifier
            .pressable(onClick = onClick, pressedScale = 0.93f, showRipple = false, onClickLabel = description)
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(c.raised2)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        RollingText(text, style = HubType.clockSmall, color = c.ink)
    }
}

/** The date picker in ink: an ink selection, no divider. */
@Composable
private fun inkDatePickerColors(): DatePickerColors {
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
        currentYearContentColor = c.ink,
        selectedYearContentColor = c.ground,
        selectedYearContainerColor = c.ink,
        dayContentColor = c.ink,
        disabledDayContentColor = c.inkMuted.copy(alpha = 0.4f),
        selectedDayContentColor = c.ground,
        selectedDayContainerColor = c.ink,
        todayContentColor = c.ink,
        todayDateBorderColor = c.ink,
        dividerColor = Color.Transparent,
    )
}

/**
 * Keeps an amount typed on the keypad well formed: digits and one point, at most two
 * decimals and eight rupee digits, no leading zeros. Null means the keystroke is refused.
 */
private fun sanitizeAmount(raw: String): String? {
    val s = raw.replace(",", "").replace(" ", "")
    if (s.isEmpty()) return ""
    if (s.any { !it.isDigit() && it != '.' }) return null
    if (s.count { it == '.' } > 1) return null
    val hasPoint = '.' in s
    val whole = s.substringBefore('.')
    val fraction = if (hasPoint) s.substringAfter('.') else ""
    if (fraction.length > 2) return null
    val trimmed = whole.trimStart('0').ifEmpty { "0" }
    if (trimmed.length > 8) return null
    return if (hasPoint) "$trimmed.$fraction" else trimmed
}

/** "125000.5" → "1,25,000.5", keeping a trailing point while it's being typed. */
private fun groupedAmount(text: String): String {
    val whole = text.substringBefore('.')
    val rest = if ('.' in text) "." + text.substringAfter('.') else ""
    val grouped = if (whole.length <= 3) whole else {
        val head = whole.dropLast(3).reversed().chunked(2).joinToString(",").reversed()
        "$head,${whole.takeLast(3)}"
    }
    return grouped + rest
}

/** 125050 paise → "1250.5"; whole rupees have no point. */
private fun plainAmount(paise: Long): String {
    val rupees = paise / 100
    val p = paise % 100
    return if (p == 0L) "$rupees" else "$rupees.${p.toString().padStart(2, '0').trimEnd('0')}"
}

private fun dayText(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> d.format(
        DateTimeFormatter.ofPattern(if (d.year == today.year) "EEE d MMM" else "EEE d MMM yyyy", Locale.getDefault()),
    )
}
