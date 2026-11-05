package com.agam.pixelhub.ui.expenses

import com.agam.pixelhub.ui.heroHeight
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AssignmentReturn
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.container
import com.agam.pixelhub.data.ExpenseCategory
import com.agam.pixelhub.expenses.Money
import com.agam.pixelhub.expenses.MonthSummary
import com.agam.pixelhub.ui.CardRadius
import com.agam.pixelhub.ui.hubCard
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.RollingText
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.motion.rememberDrawIn
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubColors
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.YearMonth
import kotlin.math.abs

/*
 * Money keeps ink as its accent. Each spending category has its own hue from a set of eight,
 * spaced round the wheel at the same muted chroma: pale and soft on the night ground, deeper on
 * the day ground, so every one holds about the same contrast against its page. The five income
 * categories take the gaps between them at the same chroma (each at least 4.5:1 on its card).
 * Income itself reads as the ok green, always with a "+" so colour is never the only signal.
 */

/** The category's own colour, tuned for [c]'s ground. */
fun ExpenseCategory.color(c: HubColors): Color = if (c.isDark) {
    when (this) {
        ExpenseCategory.FOOD -> Color(0xFFEE9A7A)          // terracotta
        ExpenseCategory.BILLS -> Color(0xFFE3BE6A)         // amber
        ExpenseCategory.HEALTH -> Color(0xFF86D1A8)        // mint
        ExpenseCategory.TRANSPORT -> Color(0xFF6FC6C6)     // teal
        ExpenseCategory.TRAVEL -> Color(0xFF8DB2EC)        // cornflower
        ExpenseCategory.ENTERTAINMENT -> Color(0xFFB8A1EE) // lavender
        ExpenseCategory.SHOPPING -> Color(0xFFEA98BD)      // rose
        ExpenseCategory.OTHER -> Color(0xFFA6AFC4)         // slate
        // Income: five more hues in the gaps, distinct from each other and from slate (Other).
        ExpenseCategory.SALARY -> Color(0xFF8FD47E)        // leaf
        ExpenseCategory.FREELANCE -> Color(0xFF7CC4EE)     // sky
        ExpenseCategory.GIFTS -> Color(0xFFD69BE8)         // orchid
        ExpenseCategory.REFUNDS -> Color(0xFFF2AE72)       // apricot
        ExpenseCategory.INVESTMENTS -> Color(0xFF9AA5F2)   // indigo
    }
} else {
    when (this) {
        ExpenseCategory.FOOD -> Color(0xFFB0522F)
        ExpenseCategory.BILLS -> Color(0xFF8F660A)
        ExpenseCategory.HEALTH -> Color(0xFF23805A)
        ExpenseCategory.TRANSPORT -> Color(0xFF12767A)
        ExpenseCategory.TRAVEL -> Color(0xFF2F63AE)
        ExpenseCategory.ENTERTAINMENT -> Color(0xFF6C4EC0)
        ExpenseCategory.SHOPPING -> Color(0xFFAE3B70)
        ExpenseCategory.OTHER -> Color(0xFF5A647B)
        ExpenseCategory.SALARY -> Color(0xFF3E7F2A)
        ExpenseCategory.FREELANCE -> Color(0xFF1F6C99)
        ExpenseCategory.GIFTS -> Color(0xFF8E3FA8)
        ExpenseCategory.REFUNDS -> Color(0xFF9A5512)
        ExpenseCategory.INVESTMENTS -> Color(0xFF4452B8)
    }
}

/** A quiet wash of the category colour, for wells and selected backgrounds. */
fun ExpenseCategory.soft(c: HubColors): Color = color(c).copy(alpha = if (c.isDark) 0.18f else 0.13f)

/** A quiet wash of the ok green, for the wells of money coming in. */
internal fun HubColors.incomeSoft(): Color = ok.copy(alpha = if (isDark) 0.18f else 0.13f)

/** Icon colour on a well filled with the ok green. */
internal fun HubColors.onOk(): Color = if (isDark) Color(0xFF141414) else Color.White

/** "+₹5,000": income always carries its sign. */
internal fun incomeText(paise: Long): String = "+" + Money.format(paise)

/** "+₹4,500" or "−₹4,500" (a true minus sign), for a net figure. */
internal fun signedText(paise: Long): String = (if (paise < 0L) "−" else "+") + Money.format(abs(paise))

val ExpenseCategory.icon: ImageVector
    get() = when (this) {
        ExpenseCategory.FOOD -> Icons.Outlined.Restaurant
        ExpenseCategory.TRANSPORT -> Icons.Outlined.DirectionsBus
        ExpenseCategory.SHOPPING -> Icons.Outlined.ShoppingBag
        ExpenseCategory.BILLS -> Icons.Outlined.Receipt
        ExpenseCategory.HEALTH -> Icons.Outlined.MedicalServices
        ExpenseCategory.ENTERTAINMENT -> Icons.Outlined.Movie
        ExpenseCategory.TRAVEL -> Icons.Outlined.Flight
        ExpenseCategory.OTHER -> Icons.Outlined.Category
        ExpenseCategory.SALARY -> Icons.Outlined.Payments
        ExpenseCategory.FREELANCE -> Icons.Outlined.Laptop
        ExpenseCategory.GIFTS -> Icons.Outlined.CardGiftcard
        ExpenseCategory.REFUNDS -> Icons.AutoMirrored.Outlined.AssignmentReturn
        ExpenseCategory.INVESTMENTS -> Icons.AutoMirrored.Outlined.TrendingUp
    }

/** Icon colour on a well filled with the category colour: dark on the pale night hues, white on the deep day ones. */
internal fun ExpenseCategory.onColor(c: HubColors): Color = if (c.isDark) Color(0xFF141414) else Color.White

/** "Bills", "Food": the label without its "& …" tail, for chips under a well. */
internal val ExpenseCategory.shortLabel: String get() = label.substringBefore(" &")

/** Text styles with tabular figures, for captions that carry amounts. */
internal val SmallFigures = HubType.small.copy(fontFeatureSettings = "tnum")
internal val BodyFigures = HubType.body.copy(fontFeatureSettings = "tnum")
internal val BodyStrongFigures = HubType.bodyStrong.copy(fontFeatureSettings = "tnum")

/** Row title and meta, as on the reference list rows. */
internal val RowTitle = HubType.bodyStrong.copy(fontSize = 14.sp, lineHeight = 20.sp)
internal val RowMeta = HubType.small.copy(fontSize = 11.sp, lineHeight = 16.sp, fontFeatureSettings = "tnum")

/** The category's icon in a round well washed with its colour; [selected] fills the well. */
@Composable
internal fun CategoryWell(
    category: ExpenseCategory,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    selected: Boolean = false,
) {
    val c = Hub.colors
    val bg by animateColorAsState(if (selected) category.color(c) else category.soft(c), tween(200), label = "wellBg")
    val tint by animateColorAsState(if (selected) category.onColor(c) else category.color(c), tween(200), label = "wellTint")
    Box(modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(category.icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

/** Keys whose figure has already rolled in during this process. */
private val rolledIn = mutableSetOf<Any>()

/**
 * The first time a figure appears its digits read as zeros and then roll up to the real
 * value, like an odometer settling. Later changes roll normally inside [RollingText].
 * With a [key], the roll-in plays once per process: coming back to the screen shows the
 * figure as it is.
 */
@Composable
internal fun rememberRolledIn(text: String?, delayMs: Long = 140, enabled: Boolean = true, key: Any? = null): String {
    val reduced = LocalReducedMotion.current || !enabled || (key != null && key in rolledIn)
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(text) {
        if (text == null) return@LaunchedEffect
        if (key != null) rolledIn += key
        if (shown == null && !reduced) {
            shown = zeroed(text)
            delay(delayMs)
        }
        shown = text
    }
    return shown ?: text?.let { if (reduced) it else zeroed(it) } ?: ""
}

private fun zeroed(s: String) = s.map { if (it.isDigit()) '0' else it }.joinToString("")

// ---------------------------------------------------------------------------------------------
// Bars
// ---------------------------------------------------------------------------------------------

/**
 * A rounded progress bar on a track. Grows in from nothing the first time [drawKey] is shown
 * in this process, then only follows changes (never regrowing when scrolled back into view or
 * revisited). Without a [drawKey] the call site's position stands in for one.
 */
@Composable
internal fun ProgressTrack(
    fraction: Float,
    color: Color,
    track: Color,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    drawKey: Any? = null,
) {
    val target = fraction.coerceIn(0f, 1f)
    val site = currentCompositeKeyHash
    val progress = rememberDrawIn(drawKey ?: "track-$site", target)
    val fill by animateColorAsState(color, tween(200), label = "trackFill")
    Canvas(modifier.fillMaxWidth().height(height).clearAndSetSemantics { }) {
        val h = size.height
        drawRoundRect(track, cornerRadius = CornerRadius(h / 2f))
        val w = size.width * progress.value
        if (w > 0.5f) drawRoundRect(fill, size = Size(maxOf(w, h), h), cornerRadius = CornerRadius(h / 2f))
    }
}

/**
 * The month's spending as one bar shared out by category, on a track that stands for the
 * budget (or the total, without one). [highlight] dims the other categories. It grows in
 * once per process per [drawKey] (the call site's position when none is given).
 */
@Composable
internal fun CategoryBar(
    summary: MonthSummary,
    modifier: Modifier = Modifier,
    track: Color = Hub.colors.well,
    highlight: ExpenseCategory? = null,
    height: Dp = 10.dp,
    drawKey: Any? = null,
) {
    val c = Hub.colors
    val parts = summary.byCategory.filter { it.spentPaise > 0L }
    val total = summary.totalPaise.coerceAtLeast(0L)
    val budget = summary.budgetPaise?.takeIf { it > 0L }
    val span = maxOf(total, budget ?: total, 1L).toFloat()
    val site = currentCompositeKeyHash
    val grow = rememberDrawIn(drawKey ?: "bar-$site", 1f)
    val alphas = ExpenseCategory.entries.map { cat ->
        animateFloatAsState(if (highlight == null || highlight == cat) 1f else 0.28f, tween(200), label = "barAlpha").value
    }
    Canvas(modifier.fillMaxWidth().height(height).clearAndSetSemantics { }) {
        val h = size.height
        val gap = 3.dp.toPx()
        drawRoundRect(track, cornerRadius = CornerRadius(h / 2f))
        var x = 0f
        parts.forEach { t ->
            val w = size.width * (t.spentPaise / span) * grow.value
            val drawn = w - gap
            if (drawn > 0.5f) {
                drawRoundRect(
                    t.category.color(c).copy(alpha = alphas[t.category.ordinal]),
                    topLeft = Offset(x, 0f),
                    size = Size(drawn, h),
                    cornerRadius = CornerRadius(minOf(drawn, h) / 2f),
                )
            }
            x += w
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The hero card: a colour card with its bottom-right corner cut away to seat a black pill,
// and optionally a second card peeking out behind it, like the reference wallet.
// ---------------------------------------------------------------------------------------------

/** Grows with the phone's font size so the card's lines are never clipped. */
internal val HeroHeight: Dp @Composable get() = heroHeight()
internal val HeroPeek = 54.dp
internal val HeroNotchHeight = 64.dp
private val HeroNotchGap = 6.dp

/**
 * [content] fills the card (20dp side padding); keep its last line clear of the notch with
 * `padding(end = notchWidth)`. [pill] lays out inside the black pill seated in the notch.
 * [peek] is the strip of the card behind; tapping it runs [onPeek] when given.
 */
@Composable
internal fun NotchedHero(
    color: Color,
    notchWidth: Dp,
    modifier: Modifier = Modifier,
    peekColor: Color = Color.Transparent,
    peek: (@Composable RowScope.() -> Unit)? = null,
    onPeek: (() -> Unit)? = null,
    peekLabel: String? = null,
    pill: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Hub.colors
    val notch = remember(notchWidth) { HeroNotchShape(CardRadius, 18.dp, notchWidth, HeroNotchHeight) }
    val top = if (peek != null) HeroPeek else 0.dp
    Box(modifier.fillMaxWidth().height(top + HeroHeight)) {
        if (peek != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(HeroPeek + CardRadius)
                    .then(
                        if (onPeek != null) {
                            Modifier.pressable(onClick = onPeek, pressedScale = 0.98f, showRipple = false, onClickLabel = peekLabel)
                        } else Modifier,
                    )
                    .clip(RoundedCornerShape(CardRadius))
                    .background(peekColor)
                    .padding(start = 20.dp, end = 20.dp, bottom = CardRadius),
                verticalAlignment = Alignment.CenterVertically,
                content = peek,
            )
        }
        Column(
            Modifier
                .padding(top = top)
                .fillMaxWidth()
                .height(HeroHeight)
                .clip(notch)
                .background(color)
                .padding(horizontal = 20.dp),
            content = content,
        )
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .width(notchWidth - HeroNotchGap)
                .height(HeroNotchHeight - HeroNotchGap)
                .clip(CircleShape)
                .background(c.chrome)
                .padding(horizontal = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = pill,
        )
    }
}

/**
 * A white disc inside the black pill. A tap steps once; with [repeat], holding keeps
 * stepping, faster than you could tap. Springs in while held.
 */
@Composable
internal fun PillDisc(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onStep: () -> Unit,
    repeat: Boolean = false,
) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val step by rememberUpdatedState(onStep)
    val isEnabled by rememberUpdatedState(enabled)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed && !reduced) 0.86f else 1f,
        if (pressed) spring(dampingRatio = 0.9f, stiffness = 1400f) else Motion.snappy(),
        label = "discPress",
    )
    val bg by animateColorAsState(if (enabled) c.onChrome else c.chromeWell, tween(160), label = "discBg")
    val tint by animateColorAsState(if (enabled) c.chrome else c.onChrome.copy(alpha = 0.45f), tween(160), label = "discTint")
    Box(
        Modifier
            .size(48.dp)
            .pointerInput(repeat) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (!isEnabled) return@awaitEachGesture
                    pressed = true
                    var repeated = false
                    val repeater = if (repeat) {
                        scope.launch {
                            delay(450)
                            while (isEnabled) {
                                repeated = true
                                step()
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                delay(80)
                            }
                        }
                    } else null
                    val up = waitForUpOrCancellation()
                    repeater?.cancel()
                    pressed = false
                    if (up != null && !repeated) {
                        up.consume()
                        step()
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                }
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
                onClick(label = description) {
                    if (isEnabled) step()
                    isEnabled
                }
                if (!enabled) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
    }
}

/**
 * A rounded card with its bottom-right corner cut away: convex corners where the card meets
 * the cut, a pill-round inner corner that hugs the button seated there.
 */
private class HeroNotchShape(
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

// ---------------------------------------------------------------------------------------------
// Home card
// ---------------------------------------------------------------------------------------------

/**
 * This month's spending on Home: a violet wallet well with the line under it saying how it
 * stands, the total rolling into place, and the month shared out by category on one bar.
 * The whole card opens the Expenses page.
 */
@Composable
fun ExpenseHomeCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalContext.current.container
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    // Moves on to the new month if Home stays open past midnight on the last day.
    var month by remember { mutableStateOf(YearMonth.now()) }
    LifecycleResumeEffect(Unit) {
        month = YearMonth.now()
        onPauseOrDispose { }
    }
    val flow = remember(month) { container.expenses.month(month) }
    val summary by flow.collectAsStateWithLifecycle<MonthSummary?>(initialValue = null)
    val s = summary
    val total = s?.totalPaise ?: 0L
    val budget = s?.budgetPaise?.takeIf { it > 0L }
    val over = budget != null && total > budget
    val top = s?.byCategory?.firstOrNull { it.spentPaise > 0L }

    val caption = when {
        s == null -> "This month"
        over -> "${Money.format(total - (budget ?: 0L))} over budget"
        budget != null -> "${Money.format(budget - total)} left of ${Money.format(budget)}"
        top != null -> "Most on ${top.category.label.lowercase()}"
        else -> "Nothing spent this month"
    }
    val captionColor by animateColorAsState(if (over) c.danger else c.inkMuted, tween(220), label = "cardCaption")

    Column(
        modifier
            .fillMaxWidth()
            .pressable(onClick = onOpen, pressedScale = 0.97f, showRipple = false, onClickLabel = "Open expenses")
            .hubCard(c.raised)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(c.dawn), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, tint = c.onDawn, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Spent this month", style = RowTitle, color = c.ink, maxLines = 1)
                AnimatedContent(
                    targetState = caption,
                    transitionSpec = {
                        fadeIn(tween(if (reduced) 120 else 200)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false)
                    },
                    label = "homeCaption",
                ) { t ->
                    Text(t, style = RowMeta, color = captionColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.size(36.dp).clip(CircleShape).background(c.well), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            RollingText(
                rememberRolledIn(s?.let { Money.format(it.totalPaise) }, key = "home-spend"),
                style = HubType.clockMedium,
                color = c.ink,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.weight(1f))
            if (budget != null) {
                Text(
                    "of ${Money.format(budget)}",
                    style = SmallFigures,
                    color = c.inkMuted,
                    maxLines = 1,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (s != null) {
            CategoryBar(s, track = c.well, height = 8.dp, drawKey = "home-spend")
        } else {
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(c.well))
        }
        // Money in, when there is any: a quiet line under the bar.
        val income = s?.incomePaise ?: 0L
        AnimatedVisibility(
            visible = income > 0L,
            enter = fadeIn(tween(200)) + expandVertically(tween(if (reduced) 0 else 240, easing = Motion.EaseOut)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(if (reduced) 0 else 200)),
        ) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Savings, contentDescription = null, tint = c.ok, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("In ", style = SmallFigures, color = c.inkMuted, maxLines = 1)
                RollingText(incomeText(income), style = SmallFigures, color = c.ok)
            }
        }
    }
}
