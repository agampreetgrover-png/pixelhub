@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.agam.pixelhub.ui.expenses

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agam.pixelhub.data.Expense
import com.agam.pixelhub.data.ExpenseCategory
import com.agam.pixelhub.expenses.Money
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

/**
 * Add or edit an expense in a sheet. The amount reads like a calculator display: huge
 * condensed numerals that roll as you type, shrinking to fit as they grow, with a caret in
 * the category's colour. A keystroke that can't be an amount shakes the display.
 *
 * [initial] null, or with id 0, is a new expense (a draft from the Assistant or a past
 * month keeps its fields).
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
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val stagger = rememberStagger()
    val zone = remember { ZoneId.systemDefault() }
    val isNew = initial == null || initial.id == 0L

    var amount by remember {
        val start = initial?.amountPaise?.takeIf { it > 0L }?.let { plainAmount(it) }.orEmpty()
        mutableStateOf(TextFieldValue(start, TextRange(start.length)))
    }
    var category by remember { mutableStateOf(initial?.category ?: ExpenseCategory.FOOD) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    val initialAt = remember { initial?.spentAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() } }
    var useNow by remember { mutableStateOf(initial == null) }
    var date by remember { mutableStateOf(initialAt?.toLocalDate() ?: LocalDate.now()) }
    var time by remember { mutableStateOf((initialAt?.toLocalTime() ?: LocalTime.now()).withSecond(0).withNano(0)) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }

    val paise = Money.parse(amount.text)
    val valid = paise != null && paise > 0L

    // Rejected keystrokes: a small spring shake and a brief danger tint.
    val shake = remember { Animatable(0f) }
    var flash by remember { mutableStateOf(false) }
    fun reject() {
        haptics.performHapticFeedback(HapticFeedbackType.Reject)
        scope.launch {
            flash = true
            if (reduced) {
                delay(260)
            } else {
                shake.snapTo(0f)
                shake.animateTo(0f, spring(dampingRatio = 0.22f, stiffness = 1500f), initialVelocity = 520f)
            }
            flash = false
        }
    }

    val amountFocus = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (isNew && amount.text.isEmpty()) {
            delay(380)
            runCatching { amountFocus.requestFocus() }
            keyboard?.show()
        }
    }

    HubSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(
                if (isNew) "New expense" else "Edit expense",
                style = HubType.section,
                color = c.ink,
                modifier = Modifier.staggerIn(stagger, "title", 0),
            )
            Spacer(Modifier.height(12.dp))

            // The display. The real text field sits invisibly underneath to own the keyboard.
            BasicTextField(
                value = amount,
                onValueChange = { v ->
                    val cleaned = sanitizeAmount(v.text)
                    if (cleaned == null) reject() else amount = TextFieldValue(cleaned, TextRange(cleaned.length))
                },
                textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                cursorBrush = SolidColor(Color.Transparent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(amountFocus)
                    .onFocusChanged { focused = it.isFocused }
                    .semantics { contentDescription = "Amount in rupees" }
                    .staggerIn(stagger, "amount", 1),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.matchParentSize().alpha(0f)) { inner() }
                        AmountDisplay(
                            text = amount.text,
                            accent = category.color(c),
                            focused = focused,
                            flash = flash,
                            modifier = Modifier.graphicsLayer { translationX = shake.value * density },
                        )
                    }
                },
            )

            Text(
                "Category",
                style = HubType.bodyStrong,
                color = c.ink,
                modifier = Modifier.padding(top = 20.dp, bottom = 12.dp).staggerIn(stagger, "catTitle", 2),
            )
            FlowRow(
                Modifier.fillMaxWidth().staggerIn(stagger, "cats", 3),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ExpenseCategory.entries.forEach { cat ->
                    CategoryPill(cat, selected = cat == category, onClick = { category = cat })
                }
            }

            HubTextField(
                value = note,
                onValueChange = { note = it },
                label = "Note",
                placeholder = "Lunch, cab home, phone bill",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                modifier = Modifier.padding(top = 24.dp).staggerIn(stagger, "note", 4),
            )

            Text(
                "When",
                style = HubType.bodyStrong,
                color = c.ink,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp).staggerIn(stagger, "whenTitle", 5),
            )
            FlowRow(
                Modifier.fillMaxWidth().staggerIn(stagger, "when", 6),
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
                    enter = fadeIn(tween(180)) + scaleIn(if (reduced) tween(0) else Motion.bouncy(), initialScale = 0.8f),
                    exit = fadeOut(tween(120)) + scaleOut(tween(140), targetScale = 0.85f),
                ) {
                    ValuePill(
                        text = Fmt.time(context, time),
                        icon = Icons.Outlined.Schedule,
                        description = "Change time",
                        onClick = { showTime = true },
                    )
                }
            }

            Column(Modifier.padding(top = 32.dp).staggerIn(stagger, "actions", 7)) {
                PrimaryButton(
                    text = "Save expense",
                    enabled = valid,
                    container = c.ink,
                    content = c.ground,
                    onClick = {
                        val p = Money.parse(amount.text)
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
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null && !isNew) {
                    Spacer(Modifier.height(10.dp))
                    QuietButton(
                        text = "Delete",
                        onClick = onDelete,
                        color = c.danger,
                        icon = Icons.Outlined.Delete,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
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
                time = it
                useNow = false
                showTime = false
            },
            accent = c.ink,
        )
    }
}

/**
 * ₹ in the category colour, then the figures rolling digit by digit, then a softly blinking
 * caret. The numerals shrink on a spring as the amount grows so it always fits on one line.
 */
@Composable
private fun AmountDisplay(
    text: String,
    accent: Color,
    focused: Boolean,
    flash: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val empty = text.isEmpty()
    val shown = if (empty) "0" else groupedAmount(text)
    val size by animateFloatAsState(
        when {
            shown.length <= 6 -> 96f
            shown.length <= 9 -> 72f
            else -> 56f
        },
        if (reduced) tween(0) else Motion.snappy(),
        label = "amountSize",
    )
    val digits by animateColorAsState(
        when {
            flash -> c.danger
            empty -> c.inkMuted.copy(alpha = 0.55f)
            else -> c.ink
        },
        tween(if (flash) 60 else 260),
        label = "amountColor",
    )
    val rupee by animateColorAsState(if (flash) c.danger else accent, tween(240), label = "rupeeColor")
    val style = HubType.clockHuge.copy(fontSize = size.sp, lineHeight = size.sp)

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            "₹",
            style = HubType.clockMedium.copy(fontSize = (size * 0.5f).sp, lineHeight = (size * 0.5f).sp),
            color = rupee,
            modifier = Modifier.padding(end = 6.dp),
        )
        RollingText(shown, style = style, color = digits)
        val blink = if (focused && !reduced) {
            rememberInfiniteTransition(label = "caret").animateFloat(
                1f, 0.1f, infiniteRepeatable(tween(520, easing = Motion.EaseInOut), RepeatMode.Reverse), label = "blink",
            ).value
        } else 1f
        AnimatedVisibility(
            visible = focused,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120)),
        ) {
            Box(
                Modifier
                    .padding(start = 6.dp)
                    .width(3.dp)
                    .height((size * 0.62f).dp)
                    .graphicsLayer { alpha = blink }
                    .clip(CircleShape)
                    .background(accent),
            )
        }
    }
}

/** A category choice. Selecting fills it with the category colour and it pops. */
@Composable
private fun CategoryPill(category: ExpenseCategory, selected: Boolean, onClick: () -> Unit) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val col = category.color(c)
    val bg by animateColorAsState(if (selected) col else c.raised2, tween(200), label = "pillBg")
    val fg by animateColorAsState(if (selected) c.ground else c.ink, tween(200), label = "pillFg")
    val tint by animateColorAsState(if (selected) c.ground else col, tween(200), label = "pillTint")
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected && !reduced) {
            pop.animateTo(1.09f, spring(dampingRatio = 1f, stiffness = 1600f))
            pop.animateTo(1f, Motion.bouncy())
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
            .padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(category.icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(category.label, style = HubType.button, color = fg)
    }
}

/** One of the "when" choices; the selected one fills with ink and pops. */
@Composable
private fun WhenPill(text: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val bg by animateColorAsState(if (selected) c.ink else c.raised2, tween(200), label = "whenBg")
    val fg by animateColorAsState(if (selected) c.ground else c.ink, tween(200), label = "whenFg")
    val pop = remember { Animatable(1f) }
    LaunchedEffect(selected) {
        if (selected && !reduced) {
            pop.animateTo(1.07f, spring(dampingRatio = 1f, stiffness = 1600f))
            pop.animateTo(1f, Motion.bouncy())
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

/** A value you can change: the time, in clock numerals on the raised tone. */
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
 * Keeps an amount typed into the display well formed: digits and one point, at most two
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
