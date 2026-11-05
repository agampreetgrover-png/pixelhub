package com.agam.pixelhub.ui.expenses

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.MedicalServices
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.container
import com.agam.pixelhub.data.ExpenseCategory
import com.agam.pixelhub.expenses.Money
import com.agam.pixelhub.expenses.MonthSummary
import com.agam.pixelhub.ui.motion.HubShapes
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.PolygonShape
import com.agam.pixelhub.ui.motion.RollingText
import com.agam.pixelhub.ui.motion.easeOutBack
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubColors
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.delay
import java.time.YearMonth
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * Money keeps ink as its accent. Each category has its own hue from a set of nine, spaced
 * round the wheel at the same muted chroma: pale and soft on the night ground, deeper on the
 * day ground, so every one holds about the same contrast against its page.
 */

/** The category's own colour, tuned for [c]'s ground. */
fun ExpenseCategory.color(c: HubColors): Color = if (c.isDark) {
    when (this) {
        ExpenseCategory.FOOD -> Color(0xFFEE9A7A)          // terracotta
        ExpenseCategory.BILLS -> Color(0xFFE3BE6A)         // amber
        ExpenseCategory.GROCERIES -> Color(0xFFB3C878)     // olive
        ExpenseCategory.HEALTH -> Color(0xFF86D1A8)        // mint
        ExpenseCategory.TRANSPORT -> Color(0xFF6FC6C6)     // teal
        ExpenseCategory.TRAVEL -> Color(0xFF8DB2EC)        // cornflower
        ExpenseCategory.ENTERTAINMENT -> Color(0xFFB8A1EE) // lavender
        ExpenseCategory.SHOPPING -> Color(0xFFEA98BD)      // rose
        ExpenseCategory.OTHER -> Color(0xFFA6AFC4)         // slate
    }
} else {
    when (this) {
        ExpenseCategory.FOOD -> Color(0xFFB0522F)
        ExpenseCategory.BILLS -> Color(0xFF8F660A)
        ExpenseCategory.GROCERIES -> Color(0xFF5B771C)
        ExpenseCategory.HEALTH -> Color(0xFF23805A)
        ExpenseCategory.TRANSPORT -> Color(0xFF12767A)
        ExpenseCategory.TRAVEL -> Color(0xFF2F63AE)
        ExpenseCategory.ENTERTAINMENT -> Color(0xFF6C4EC0)
        ExpenseCategory.SHOPPING -> Color(0xFFAE3B70)
        ExpenseCategory.OTHER -> Color(0xFF5A647B)
    }
}

/** A quiet wash of the category colour, for badges and selected backgrounds. */
fun ExpenseCategory.soft(c: HubColors): Color = color(c).copy(alpha = if (c.isDark) 0.18f else 0.13f)

val ExpenseCategory.icon: ImageVector
    get() = when (this) {
        ExpenseCategory.FOOD -> Icons.Outlined.Restaurant
        ExpenseCategory.GROCERIES -> Icons.Outlined.ShoppingBasket
        ExpenseCategory.TRANSPORT -> Icons.Outlined.DirectionsBus
        ExpenseCategory.SHOPPING -> Icons.Outlined.ShoppingBag
        ExpenseCategory.BILLS -> Icons.Outlined.Receipt
        ExpenseCategory.HEALTH -> Icons.Outlined.MedicalServices
        ExpenseCategory.ENTERTAINMENT -> Icons.Outlined.Movie
        ExpenseCategory.TRAVEL -> Icons.Outlined.Flight
        ExpenseCategory.OTHER -> Icons.Outlined.Category
    }

/** Text styles with tabular figures, for captions that carry amounts. */
internal val SmallFigures = HubType.small.copy(fontFeatureSettings = "tnum")
internal val BodyFigures = HubType.body.copy(fontFeatureSettings = "tnum")
internal val BodyStrongFigures = HubType.bodyStrong.copy(fontFeatureSettings = "tnum")

/** The category's icon on a soft scalloped badge in its own colour. */
@Composable
fun CategoryBadge(category: ExpenseCategory, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val c = Hub.colors
    val shape = remember { PolygonShape(HubShapes.cookie) }
    val wash by animateColorAsState(category.soft(c), tween(220), label = "badgeWash")
    val tint by animateColorAsState(category.color(c), tween(220), label = "badgeTint")
    Box(
        modifier.size(size).clip(shape).background(wash),
        contentAlignment = Alignment.Center,
    ) {
        Icon(category.icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

/**
 * The first time a figure appears its digits read as zeros and then roll up to the real
 * value, like an odometer settling. Later changes roll normally inside [RollingText].
 */
@Composable
internal fun rememberRolledIn(text: String?, delayMs: Long = 140, enabled: Boolean = true): String {
    val reduced = LocalReducedMotion.current || !enabled
    var shown by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(text) {
        if (text == null) return@LaunchedEffect
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
// The spend ring
// ---------------------------------------------------------------------------------------------

/**
 * What the ring shows. Without a budget the ring is the month's spending, shared out by
 * category. With a budget the ring is the budget: categories fill the used part and the
 * rest is bare track. Past the budget the ring becomes the spending again, the budgeted part
 * in category colours and the overspend in danger.
 */
internal class RingModel(
    val order: List<ExpenseCategory>,
    val fracs: Map<ExpenseCategory, Float>,
    val danger: Float,
    /** Where the budget stands on the ring (0..1), or null without a budget. */
    val knob: Float?,
    val over: Boolean,
) {
    companion object {
        fun of(s: MonthSummary): RingModel {
            val total = s.totalPaise.coerceAtLeast(0L)
            val budget = s.budgetPaise?.takeIf { it > 0L }
            val over = budget != null && total > budget
            val span = when {
                total <= 0L -> 0f
                budget == null -> 1f
                over -> budget.toFloat() / total
                else -> total.toFloat() / budget
            }
            val spending = s.byCategory.filter { it.spentPaise > 0L }
            val fracs = spending.associate { t ->
                t.category to if (total > 0L) t.spentPaise.toFloat() / total * span else 0f
            }
            return RingModel(
                order = spending.map { it.category },
                fracs = fracs,
                danger = if (over) 1f - span else 0f,
                knob = budget?.let { span },
                over = over,
            )
        }
    }
}

private const val RING_DONE = 100_000f

/**
 * The segmented ring, drawn in the language of the Home dial: a thick groove on raised2 with
 * category segments laid round it from twelve o'clock, separated by hairline gaps.
 * On entry the segments sweep in one after another and the budget knob rides the sweep to
 * its place; afterwards every change glides on a spring. [highlight] dims the other segments.
 */
@Composable
internal fun SpendRing(
    summary: MonthSummary,
    modifier: Modifier = Modifier,
    stroke: Dp = 22.dp,
    gap: Dp = 3.dp,
    knobRadius: Dp = 9.dp,
    inset: Dp = 8.dp,
    highlight: ExpenseCategory? = null,
    ticks: Boolean = false,
    stepMs: Int = 90,
    segmentMs: Int = 560,
    playIntro: Boolean = true,
    onIntroStarted: () -> Unit = {},
) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val model = remember(summary) { RingModel.of(summary) }
    val spec: AnimationSpec<Float> = if (reduced) snap() else Motion.gentle()

    // One animated share per category; the count never changes so the calls stay stable.
    val fracs = ExpenseCategory.entries.map { cat ->
        animateFloatAsState(model.fracs[cat] ?: 0f, spec, label = "segment").value
    }
    val alphas = ExpenseCategory.entries.map { cat ->
        animateFloatAsState(
            if (highlight == null || highlight == cat) 1f else 0.26f,
            tween(if (reduced) 0 else 280),
            label = "segmentAlpha",
        ).value
    }
    val danger by animateFloatAsState(model.danger, spec, label = "danger")
    val knob by animateFloatAsState(model.knob ?: 0f, spec, label = "knob")
    val hasKnob = model.knob != null

    // Intro clock in milliseconds; jumps to "done" afterwards so later segments draw at once.
    val sweepMs = remember { stepMs * (model.order.size - 1).coerceAtLeast(0) + segmentMs }
    val clock = remember { Animatable(if (reduced || !playIntro) RING_DONE else 0f) }
    LaunchedEffect(Unit) {
        onIntroStarted()
        if (clock.value < RING_DONE) {
            val end = sweepMs + 420
            clock.animateTo(end.toFloat(), tween(end, easing = LinearEasing))
            clock.snapTo(RING_DONE)
        }
    }
    val halo: State<Float>? = if (model.over && !reduced) {
        rememberInfiniteTransition(label = "overPulse").animateFloat(
            0f, 1f, infiniteRepeatable(tween(2200, easing = Motion.EaseOut), RepeatMode.Restart), label = "halo",
        )
    } else null

    Canvas(modifier.clearAndSetSemantics { }) {
        val t = clock.value
        val sw = stroke.toPx()
        val r = size.minDimension / 2f - sw / 2f - inset.toPx()
        val topLeft = Offset(center.x - r, center.y - r)
        val arc = Size(r * 2, r * 2)
        val gapDeg = (gap.toPx() / r) * (180f / PI.toFloat())

        drawCircle(c.raised2, radius = r, center = center, style = Stroke(sw))

        if (ticks) {
            val detail = ((t - 160f) / 420f).coerceIn(0f, 1f)
            val tr = r - sw / 2f - 11.dp.toPx()
            for (k in 0 until 40) {
                val major = k % 10 == 0
                val p = ringPoint(center, tr, -90f + k * 9f)
                drawCircle(
                    (if (major) c.inkMuted else c.raised2).copy(alpha = detail),
                    radius = if (major) 2.4.dp.toPx() else 1.4.dp.toPx(),
                    center = p,
                )
            }
        }

        // Category segments, each growing from its own start, one after another.
        val drawn = model.order + ExpenseCategory.entries.filter { it !in model.order && fracs[it.ordinal] > 0.0005f }
        var start = -90f
        drawn.forEachIndexed { i, cat ->
            val full = fracs[cat.ordinal] * 360f
            val local = Motion.EaseOut.transform(((t - i * stepMs) / segmentMs).coerceIn(0f, 1f))
            val sweep = (full - gapDeg).coerceAtLeast(0f) * local
            if (sweep > 0.15f) {
                drawArc(
                    cat.color(c).copy(alpha = alphas[cat.ordinal]),
                    startAngle = start + gapDeg / 2f, sweepAngle = sweep, useCenter = false,
                    topLeft = topLeft, size = arc, style = Stroke(sw, cap = StrokeCap.Butt),
                )
            }
            start += full
        }

        // Overspend: the rest of the ring in danger.
        if (danger > 0.0005f) {
            val local = Motion.EaseOut.transform(((t - sweepMs * 0.8f) / segmentMs).coerceIn(0f, 1f))
            val sweep = (danger * 360f - gapDeg).coerceAtLeast(0f) * local
            if (sweep > 0.15f) {
                drawArc(
                    c.danger, startAngle = start + gapDeg / 2f, sweepAngle = sweep, useCenter = false,
                    topLeft = topLeft, size = arc, style = Stroke(sw, cap = StrokeCap.Butt),
                )
            }
        }

        // The budget knob rides the sweep, then pops into place.
        if (hasKnob) {
            val ride = Motion.EaseOut.transform((t / sweepMs.toFloat()).coerceIn(0f, 1f))
            val popLocal = (t / 340f).coerceIn(0f, 1f)
            val s = if (reduced) 1f else 0.55f + 0.45f * easeOutBack(popLocal)
            val p = ringPoint(center, r, -90f + knob * 360f * ride)
            val kr = knobRadius.toPx()
            val color = if (model.over) c.danger else c.ink
            halo?.value?.let { h ->
                drawCircle(c.danger.copy(alpha = 0.26f * (1f - h)), radius = kr * (1.3f + 1.6f * h), center = p)
            }
            drawCircle(c.ground, radius = (kr + 4.dp.toPx()) * s, center = p)
            drawCircle(color, radius = kr * s, center = p)
        }
    }
}

private fun DrawScope.ringPoint(center: Offset, radius: Float, angleDeg: Float): Offset {
    val a = Math.toRadians(angleDeg.toDouble())
    return Offset(center.x + radius * cos(a).toFloat(), center.y + radius * sin(a).toFloat())
}

// ---------------------------------------------------------------------------------------------
// Home card
// ---------------------------------------------------------------------------------------------

/**
 * This month's spending on Home: a small segmented ring, the total rolling into place, how it
 * stands against the budget and where most of it went. The whole card opens the Expenses page.
 */
@Composable
fun ExpenseHomeCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val container = LocalContext.current.container
    val c = Hub.colors
    val month = remember { YearMonth.now() }
    val flow = remember(month) { container.expenses.month(month) }
    val summary by flow.collectAsStateWithLifecycle<MonthSummary?>(initialValue = null)
    val s = summary
    val total = s?.totalPaise ?: 0L
    val budget = s?.budgetPaise?.takeIf { it > 0L }
    val over = budget != null && total > budget
    val top = s?.byCategory?.firstOrNull { it.spentPaise > 0L }

    val caption = when {
        s == null -> ""
        over -> "${Money.format(total - (budget ?: 0L))} over budget"
        budget != null -> "of ${Money.format(budget)} budget"
        total == 0L -> "Nothing spent this month"
        else -> "spent this month"
    }
    val captionColor by animateColorAsState(if (over) c.danger else c.inkMuted, tween(240), label = "cardCaption")

    Row(
        modifier
            .fillMaxWidth()
            .pressable(onClick = onOpen, pressedScale = 0.97f, showRipple = false, onClickLabel = "Open expenses")
            .clip(RoundedCornerShape(28.dp))
            .background(c.raised)
            .padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
            if (s != null) {
                SpendRing(
                    s, Modifier.fillMaxSize(),
                    stroke = 9.dp, gap = 2.dp, knobRadius = 4.5.dp, inset = 4.dp,
                    stepMs = 70, segmentMs = 460,
                )
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 9.dp.toPx()
                    drawCircle(c.raised2, radius = size.minDimension / 2f - sw / 2f - 4.dp.toPx(), style = Stroke(sw))
                }
            }
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            RollingText(
                rememberRolledIn(s?.let { Money.format(it.totalPaise) }),
                style = HubType.clockMedium,
                color = c.ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(caption, style = SmallFigures, color = captionColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (top != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(top.category.color(c)))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Most on ${top.category.label.lowercase()}",
                        style = HubType.small, color = c.inkMuted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = c.inkMuted)
    }
}
