package com.agam.pixelhub.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import com.agam.pixelhub.container
import com.agam.pixelhub.health.DailyActivity
import com.agam.pixelhub.health.DayActivity
import com.agam.pixelhub.health.HealthStatus
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.Tab
import com.agam.pixelhub.ui.health.grouped
import com.agam.pixelhub.ui.health.kcal
import com.agam.pixelhub.ui.health.km
import com.agam.pixelhub.widget.kit.Caption
import com.agam.pixelhub.widget.kit.CardTone
import com.agam.pixelhub.widget.kit.Figure
import com.agam.pixelhub.widget.kit.Notice
import com.agam.pixelhub.widget.kit.WidgetCard
import com.agam.pixelhub.widget.kit.WidgetPalette
import com.agam.pixelhub.widget.kit.WidgetShape
import com.agam.pixelhub.widget.kit.openTab
import com.agam.pixelhub.widget.kit.ringBitmap
import com.agam.pixelhub.widget.kit.shapeOf
import kotlinx.coroutines.flow.first
import kotlin.coroutines.cancellation.CancellationException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.min

/** Android's entry point for the Health widget. */
class HealthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HealthWidget()
}

/** What the widget can show: today's figures (fresh or cached), or why it can't. */
private sealed interface HealthState {
    data class Ready(val today: DailyActivity, val goal: Long, val week: List<DayActivity>, val stale: Boolean) : HealthState
    data object NotConnected : HealthState
    data object NotLoaded : HealthState
}

/** Figures older than this get their time shown. */
private const val STALE_AFTER_MS = 30 * 60_000L

class HealthWidget : GlanceAppWidget() {
    /** Real dp: shapeOf() picks the layout from the room the launcher actually gives. */
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = load(context)
        provideContent { HealthContent(context, state) }
    }

    /** The widget picker shows the real widget with sample figures. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { HealthContent(context, HealthState.Ready(sampleToday(), 8_000, sampleWeek(), stale = false)) }
    }
}

/**
 * Reads Health Connect directly. In the background that needs "Background reading"; without
 * it, the last figures the app saw are shown with their time. Yesterday's figures never pose
 * as today's.
 */
private suspend fun load(context: Context): HealthState {
    val c = context.container
    // null: the check itself failed (Health Connect briefly unreachable). Only a definite "no"
    // shows "Connect Health"; otherwise fall through to the cached figures.
    val connected = if (c.health.status() == HealthStatus.AVAILABLE) {
        runCatching { c.health.isConnected() }.getOrNull()
    } else {
        false
    }
    if (connected == false) return HealthState.NotConnected
    val fresh = runCatching { c.health.today() }.getOrNull()?.also { HealthCache.save(context, it) }
    val today = (fresh ?: HealthCache.load(context))
        ?.takeIf { it.asOf.atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now() }
        ?: return HealthState.NotLoaded
    val week = runCatching { c.health.history(7) }.getOrDefault(emptyList())
    val stale = System.currentTimeMillis() - today.asOf.toEpochMilli() > STALE_AFTER_MS
    return HealthState.Ready(today, c.settings.stepGoal.first(), week, stale)
}

/** The last figures read (by the widget or the app), so the widget never starts blank. */
internal object HealthCache {
    private const val PREFS = "widget_health"

    fun save(context: Context, a: DailyActivity) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("steps", a.steps)
            .putFloat("meters", a.distanceMeters.toFloat())
            .putFloat("kcal", a.activeKcal.toFloat())
            .putLong("minutes", a.exerciseMinutes)
            .putLong("asOf", a.asOf.toEpochMilli())
            .apply()
    }

    fun load(context: Context): DailyActivity? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("asOf")) return null
        return DailyActivity(
            steps = p.getLong("steps", 0L),
            distanceMeters = p.getFloat("meters", 0f).toDouble(),
            activeKcal = p.getFloat("kcal", 0f).toDouble(),
            exerciseMinutes = p.getLong("minutes", 0L),
            asOf = Instant.ofEpochMilli(p.getLong("asOf", 0L)),
        )
    }
}

/**
 * New figures reach the widget through WidgetSync; a new step goal doesn't, so the Health page
 * redraws the widget itself when the goal changes.
 */
internal object HealthWidgetSync {
    /** Redraws every placed Health widget; a no-op when none is on the home screen. */
    suspend fun refresh(context: Context) {
        try {
            HealthWidget().updateAll(context.applicationContext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("PixelHub", "Health widget refresh failed", e)
        }
    }
}

@Composable
private fun HealthContent(context: Context, state: HealthState) {
    val palette = WidgetPalette.of(context, CardTone.SUN)
    val size = LocalSize.current
    val shape = shapeOf(size)
    val open = openTab(context, Tab.HEALTH)
    when (state) {
        HealthState.NotConnected -> Notice(palette, "Connect Health", "Tap to see your steps here", open, shape)
        HealthState.NotLoaded -> Notice(palette, "Steps", "Open PixelHub to load today's steps", open, shape)
        is HealthState.Ready -> {
            val f = Figures(context, state)
            WidgetCard(palette, open, f.description, padding = if (shape == WidgetShape.TINY) 10.dp else 14.dp) {
                when (shape) {
                    WidgetShape.TINY -> Tiny(context, palette, f, size)
                    // Stats by real width, not the shared strip sizes: below these the weighted
                    // steps column was squeezed to nothing and the step count vanished.
                    WidgetShape.STRIP_S, WidgetShape.STRIP_M, WidgetShape.STRIP_L -> Strip(
                        context, palette, f, size,
                        stats = when {
                            size.width < 300.dp -> 0
                            size.width < 390.dp -> 2
                            else -> 3
                        },
                    )
                    WidgetShape.SQUARE -> Square(context, palette, f, size)
                    WidgetShape.PANEL -> Panel(context, palette, f, panelRing(size), wide = size.width >= 260.dp)
                    // Without the week (no background reading) the chart would leave half the card empty.
                    WidgetShape.LARGE -> if (f.week.isEmpty()) {
                        Panel(context, palette, f, panelRing(size), wide = true)
                    } else {
                        Panel(context, palette, f, 96.dp, wide = true)
                        Spacer(GlanceModifier.height(12.dp))
                        WeekBars(palette, f, chart = (size.height - 28.dp - 96.dp - 12.dp - 18.dp).coerceAtLeast(24.dp))
                    }
                }
            }
        }
    }
}

/** The panel's ring: as tall as the card allows, but never so wide the step count gets cut. */
private fun panelRing(size: DpSize): Dp = minOf(size.height - 28.dp, (size.width - 28.dp) * 0.35f)

/** Everything a layout prints, worked out once. */
private class Figures(context: Context, s: HealthState.Ready) {
    val steps = s.today.steps
    val goal = s.goal.coerceAtLeast(1)
    val fraction = steps.toFloat() / goal
    val reached = steps >= goal
    val stepsText: String = grouped(steps)
    val minutes = "${s.today.exerciseMinutes} min"
    val energy = "${grouped(kcal(s.today))} kcal"
    val distance = "${km(s.today.distanceMeters)} km"
    val week = s.week
    /** The line under the steps: how far to go, or, when the figures are old, when they're from. */
    val second: String = when {
        s.stale -> "at ${Fmt.time(context, s.today.asOf.atZone(ZoneId.systemDefault()).toLocalTime())}"
        reached -> "Goal reached"
        else -> "${grouped(goal - steps)} to go"
    }
    val description: String = buildString {
        append("$stepsText of ${grouped(goal)} steps today, ")
        append("${s.today.exerciseMinutes} minutes exercise, ${grouped(kcal(s.today))} kcal")
        if (s.stale) append(", updated $second")
    }
}

/** "6.2k", "12k": steps in the smallest tile. */
private fun compact(n: Long): String = when {
    n < 1_000 -> n.toString()
    n < 10_000 -> String.format(Locale.getDefault(), "%.1fk", n / 1_000.0)
    else -> "${n / 1_000}k"
}

@Composable
private fun Ring(context: Context, palette: WidgetPalette, fraction: Float, d: Dp, content: @Composable () -> Unit = {}) {
    Box(GlanceModifier.size(d), contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(ringBitmap(context, d, fraction, palette.nowTrack(), palette.nowInk(), stroke = (d.value * 0.1f).coerceIn(4f, 10f).dp)),
            contentDescription = null,
            modifier = GlanceModifier.size(d),
        )
        content()
    }
}

/** 1×1: the ring fills the tile, the steps sit inside it. */
@Composable
private fun Tiny(context: Context, palette: WidgetPalette, f: Figures, size: DpSize) {
    val d = (min(size.width.value, size.height.value) - 20f).coerceAtLeast(32f).dp
    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Ring(context, palette, f.fraction, d) {
            Figure(compact(f.steps), palette, (d.value * 0.22f).sp)
        }
    }
}

/** One row: ring, the steps and their second line, then [stats] small figures. */
@Composable
private fun Strip(context: Context, palette: WidgetPalette, f: Figures, size: DpSize, stats: Int) {
    val d = (size.height.value - 28f).coerceIn(32f, 52f).dp
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Ring(context, palette, f.fraction, d)
        Spacer(GlanceModifier.width(12.dp))
        Column(GlanceModifier.defaultWeight()) {
            Figure(f.stepsText, palette, 22.sp)
            Caption(f.second, palette)
        }
        if (stats >= 2) {
            Stat(palette, f.minutes, "exercise")
            Spacer(GlanceModifier.width(14.dp))
            Stat(palette, f.energy, "energy")
        }
        if (stats >= 3) {
            Spacer(GlanceModifier.width(14.dp))
            Stat(palette, f.distance, "distance")
        }
    }
}

@Composable
private fun Stat(palette: WidgetPalette, value: String, label: String) {
    Column {
        Caption(value, palette, strong = true)
        Caption(label, palette)
    }
}

/** 2×2: a big ring with the steps inside, the second line under it. */
@Composable
private fun Square(context: Context, palette: WidgetPalette, f: Figures, size: DpSize) {
    val d = min(size.width.value - 28f, size.height.value - 28f - 22f).coerceAtLeast(48f).dp
    Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
        Ring(context, palette, f.fraction, d) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Figure(f.stepsText, palette, (d.value * 0.18f).coerceIn(16f, 30f).sp)
                Caption("steps", palette)
            }
        }
        Spacer(GlanceModifier.height(6.dp))
        Caption(f.second, palette, strong = true)
    }
}

/** 3×2 and 4×2: the ring beside the steps, the second line, and the day's other figures. */
@Composable
private fun Panel(context: Context, palette: WidgetPalette, f: Figures, height: Dp, wide: Boolean) {
    val d = height.value.coerceIn(48f, 110f).dp
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Ring(context, palette, f.fraction, d)
        Spacer(GlanceModifier.width(16.dp))
        Column(GlanceModifier.defaultWeight()) {
            Caption("Steps today", palette)
            Figure(f.stepsText, palette, 30.sp)
            Caption(f.second, palette)
            Spacer(GlanceModifier.height(6.dp))
            Row {
                Caption(f.minutes, palette, strong = true)
                Spacer(GlanceModifier.width(12.dp))
                Caption(f.energy, palette, strong = true)
                if (wide) {
                    Spacer(GlanceModifier.width(12.dp))
                    Caption(f.distance, palette, strong = true)
                }
            }
        }
    }
}

/** The week as bars: full ink on days the goal was reached, softer on the rest; today's letter bold. */
@Composable
private fun WeekBars(palette: WidgetPalette, f: Figures, chart: Dp) {
    if (f.week.isEmpty()) return
    val top = maxOf(f.week.maxOf { it.steps }, f.goal).toFloat()
    val today = LocalDate.now()
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        f.week.forEach { day ->
            Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                val h = (chart.value * day.steps / top).coerceAtLeast(4f).dp
                Box(
                    GlanceModifier.width(14.dp).height(h).cornerRadius(4.dp)
                        .background(if (day.steps >= f.goal) palette.ink else palette.muted),
                ) {}
                Spacer(GlanceModifier.height(4.dp))
                Caption(
                    day.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.getDefault()),
                    palette,
                    strong = day.date == today,
                )
            }
        }
    }
}

private fun sampleToday() = DailyActivity(
    steps = 6_240, distanceMeters = 4_100.0, activeKcal = 310.0, exerciseMinutes = 42, asOf = Instant.now(),
)

private fun sampleWeek(): List<DayActivity> {
    val today = LocalDate.now()
    return listOf(7_200L, 9_100L, 5_400L, 8_600L, 10_200L, 4_300L, 6_240L).mapIndexed { i, steps ->
        DayActivity(today.minusDays((6 - i).toLong()), steps, 0.0, 0.0, 0L)
    }
}
