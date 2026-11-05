package com.agam.pixelhub.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import com.agam.pixelhub.R
import com.agam.pixelhub.calendar.CalendarEvent
import com.agam.pixelhub.calendar.CalendarEvents
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.Tab
import com.agam.pixelhub.ui.theme.NightColors
import com.agam.pixelhub.widget.kit.CardTone
import com.agam.pixelhub.widget.kit.Notice
import com.agam.pixelhub.widget.kit.WidgetCard
import com.agam.pixelhub.widget.kit.WidgetPalette
import com.agam.pixelhub.widget.kit.WidgetShape
import com.agam.pixelhub.widget.kit.isStrip
import com.agam.pixelhub.widget.kit.openTab
import com.agam.pixelhub.widget.kit.ringBitmap
import com.agam.pixelhub.widget.kit.shapeOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import androidx.glance.color.ColorProvider as DayNightColor
import androidx.glance.text.TextStyle as GlanceTextStyle

/** Android's entry point for the Calendar widget. */
class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget()
}

private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS

/** The fixed dark look of this widget, the same in every theme (the user's choice). */
private object Cal {
    val Container = Color(0xFF1F2123)
    val Card = Color(0xFF2D3135)
    val White = Color(0xFFFFFFFF)
    val OffWhite = Color(0xFFE8E8EA)
    /** Lighter than solid-card grey: on glass over a bright wallpaper it still keeps ~5:1. */
    val Muted = Color(0xFFCFCFD4)
    /** The ring's track: glass, like the cards. */
    val Track = Color(0x24FFFFFF)
    /** "in 45m": a step softer than white. */
    val SoftText = Color(0xFFC4C4C9)
    val Mint = Color(0xFF4ADE80)
    val Ring = Color(0xFF34D399)
    /** The highlighted card's time line. */
    val MintTime = Color(0xFF8FD9AE)

    /** The kit's palette in these colours, so the empty and no-access notices match. */
    val palette = NightColors.copy(raised = Container, raised2 = Card, ink = White, inkMuted = Muted)
        .let { WidgetPalette(it, it, CardTone.RAISED, isNight = true) }
}

private fun fixed(c: Color): ColorProvider = DayNightColor(c, c)

/** The glass panel behind everything (widgets can't blur, so this is clear tinted glass). */
private val Glass get() = ImageProvider(R.drawable.widget_glass_container)

/** What the widget can show: the coming events and the day's progress, or that it may not read the calendar. */
private sealed interface CalendarState {
    /**
     * [soon]: now until the end of tomorrow, timed events in order then all-day ones. [next]: the
     * first timed event within a week (under way or coming). [doneToday]/[totalToday]: today's
     * timed events that have ended, out of all of them (the ring).
     */
    data class Ready(
        val soon: List<CalendarEvent>,
        val next: CalendarEvent?,
        val doneToday: Int,
        val totalToday: Int,
    ) : CalendarState
    data object NoAccess : CalendarState
}

class CalendarWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = load(context)
        provideContent {
            // Re-reads when the calendars change, and every 5 minutes so events that end drop off.
            val state by remember { live(context) }.collectAsState(first)
            CalendarContent(context, state)
        }
    }

    /** The widget picker shows the real widget with sample events. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { CalendarContent(context, sampleState()) }
    }
}

/** The same event from several calendars (a holiday three times) shows once; back-to-back sessions stay separate. */
private fun List<CalendarEvent>.unique() = distinctBy { it.title.trim().lowercase() to it.begin }

/** The widget's state, re-read on every calendar change and every 5 minutes. */
private fun live(context: Context): Flow<CalendarState> {
    val events = CalendarEvents(context)
    if (!events.hasPermission()) return flowOf(CalendarState.NoAccess)
    val now = System.currentTimeMillis()
    val changes = events.observe(now, now + 2 * DAY_MS).map { }
    val ticks = flow {
        while (true) {
            delay(5 * MINUTE_MS)
            emit(Unit)
        }
    }
    return merge(changes, ticks).map { load(context) }
}

private suspend fun load(context: Context): CalendarState {
    val events = CalendarEvents(context)
    if (!events.hasPermission()) return CalendarState.NoAccess
    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val startOfToday = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
    val endOfToday = LocalDate.now().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val endOfTomorrow = LocalDate.now().plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
    val week = runCatching { events.observe(now, now + 7 * DAY_MS).first() }.getOrDefault(emptyList())
        .filter { it.end > now }
        .unique()
        .sortedWith(compareBy<CalendarEvent>({ dayOf(it) }, { it.allDay }, { it.begin }))
    val today = runCatching { events.observe(startOfToday, endOfToday).first() }.getOrDefault(emptyList())
        .filter { !it.allDay && it.begin >= startOfToday && it.begin < endOfToday }
        .unique()
    return CalendarState.Ready(
        soon = week.filter { it.begin < endOfTomorrow },
        next = week.firstOrNull { !it.allDay },
        doneToday = today.count { it.end <= now },
        totalToday = today.size,
    )
}

/** The day an event belongs to in the list: today for anything already under way. */
private fun dayOf(e: CalendarEvent): LocalDate {
    val begin = Instant.ofEpochMilli(e.begin).atZone(ZoneId.systemDefault()).toLocalDate()
    return maxOf(begin, LocalDate.now())
}

@Composable
private fun CalendarContent(context: Context, state: CalendarState) {
    val palette = Cal.palette
    val size = LocalSize.current
    val shape = shapeOf(size)
    val open = openTab(context, Tab.CALENDAR)
    if (state is CalendarState.NoAccess) {
        Notice(palette, "Calendar", "Tap to allow calendar access", open, shape, background = Glass)
        return
    }
    val ready = state as CalendarState.Ready
    val events = CalendarEvents(context)
    val today = LocalDate.now()
    val countdown = countdown(ready.next)
    val description = buildString {
        append("${today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${today.dayOfMonth}.")
        ready.next?.let { append(" Next: ${it.title}${countdown?.let { c -> " $c" }.orEmpty().replace("m", " minutes").replace("h", " hours")}.") }
        if (ready.totalToday > 0) append(" ${ready.doneToday} of ${ready.totalToday} classes done.")
    }

    if (shape == WidgetShape.TINY) {
        WidgetCard(palette, open, description, padding = 10.dp, background = Glass) {
            Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                Label("${today.dayOfMonth}", Cal.White, 28.sp, FontWeight.Bold)
                countdown?.let { Countdown(it) }
            }
        }
        return
    }
    if (ready.soon.isEmpty()) {
        Notice(palette, "Free today", "Nothing on your calendar today or tomorrow", open, shape, background = Glass)
        return
    }
    // The highlighted card: the event under way, or the next one.
    val highlight = ready.soon.firstOrNull { !it.allDay }
    WidgetCard(
        palette, open, description, padding = 16.dp, background = Glass,
        verticalAlignment = if (shape.isStrip()) Alignment.CenterVertically else Alignment.Top,
    ) {
        when (shape) {
            WidgetShape.STRIP_S, WidgetShape.STRIP_M -> EventCard(context, events, ready.soon.first(), ready.soon.first() == highlight, GlanceModifier.fillMaxWidth())
            WidgetShape.STRIP_L -> Row(GlanceModifier.fillMaxWidth()) {
                EventCard(context, events, ready.soon.first(), ready.soon.first() == highlight, GlanceModifier.defaultWeight())
                ready.soon.getOrNull(1)?.let { second ->
                    Spacer(GlanceModifier.width(6.dp))
                    EventCard(context, events, second, second == highlight, GlanceModifier.defaultWeight())
                }
            }
            WidgetShape.SQUARE -> CardList(context, events, ready.soon, highlight)
            else -> Row(GlanceModifier.fillMaxSize()) {
                LeftColumn(context, ready, countdown, (size.width.value * 0.19f).coerceIn(56f, 68f).dp)
                Spacer(GlanceModifier.width(6.dp))
                Column(GlanceModifier.defaultWeight()) {
                    CardList(context, events, ready.soon, highlight)
                }
            }
        }
    }
}

@Composable
private fun Label(text: String, color: Color, size: TextUnit, weight: FontWeight = FontWeight.Normal, maxLines: Int = 1) {
    Text(text, maxLines = maxLines, style = GlanceTextStyle(color = fixed(color), fontSize = size, fontWeight = weight))
}

/** Mint ↗ and "in 45m". */
@Composable
private fun Countdown(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            ImageProvider(R.drawable.ic_widget_arrow_up_right),
            contentDescription = null,
            colorFilter = ColorFilter.tint(fixed(Cal.Mint)),
            modifier = GlanceModifier.size(13.dp),
        )
        Spacer(GlanceModifier.width(3.dp))
        Label(text, Cal.SoftText, 12.sp, FontWeight.Medium, maxLines = 2)
    }
}

/** Day, date, "↗ in 45m", then the ring of the day's classes done ("1/4"), stacked as one group. */
@Composable
private fun LeftColumn(context: Context, ready: CalendarState.Ready, countdown: String?, width: Dp) {
    val today = LocalDate.now()
    Column(GlanceModifier.width(width).fillMaxHeight()) {
        Label(today.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), Cal.Muted, 15.sp, FontWeight.Bold)
        Label("${today.dayOfMonth}", Cal.White, 32.sp, FontWeight.Bold)
        Spacer(GlanceModifier.height(4.dp))
        countdown?.let { Countdown(it) }
        // The ring follows straight on: day, date, countdown and ring read as one status group.
        Spacer(GlanceModifier.height(10.dp))
        if (ready.totalToday > 0) {
            val fraction = ready.doneToday.toFloat() / ready.totalToday
            Box(GlanceModifier.size(40.dp), contentAlignment = Alignment.Center) {
                Image(
                    ImageProvider(ringBitmap(context, 40.dp, fraction, Cal.Track, Cal.Ring, stroke = 4.dp)),
                    contentDescription = null,
                    modifier = GlanceModifier.size(40.dp),
                )
                Label("${ready.doneToday}/${ready.totalToday}", Cal.White, 12.sp, FontWeight.Bold)
            }
        }
    }
}

/** The gap between cards. */
private const val CARD_GAP_DP = 6f

/** Every event today and tomorrow, as a scrollable list (Glance's LazyColumn). */
@Composable
private fun CardList(context: Context, events: CalendarEvents, list: List<CalendarEvent>, highlight: CalendarEvent?) {
    LazyColumn(GlanceModifier.fillMaxSize()) {
        // Stable ids keep scrolling smooth when the list redraws.
        items(list, itemId = { it.eventId * 31 + it.begin }) { e ->
            Column(GlanceModifier.fillMaxWidth().padding(bottom = CARD_GAP_DP.dp)) {
                EventCard(context, events, e, e == highlight, GlanceModifier.fillMaxWidth())
            }
        }
    }
}

/** One event: its name on line 1, its time on line 2. The highlighted one is dark emerald with a mint time. */
@Composable
private fun EventCard(context: Context, events: CalendarEvents, e: CalendarEvent, highlighted: Boolean, modifier: GlanceModifier) {
    val fill = if (highlighted) {
        modifier.background(ImageProvider(R.drawable.widget_cal_highlight))
    } else {
        modifier.background(ImageProvider(R.drawable.widget_glass_card))
    }
    Column(
        fill
            .cornerRadius(12.dp)
            .clickable(actionStartActivity(events.openIntent(e)))
            .semantics { contentDescription = "${e.title}, ${cardTime(context, e)}. Opens in your calendar." }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Label(e.title, if (highlighted) Cal.White else Cal.OffWhite, 14.sp, FontWeight.Medium)
        Label(cardTime(context, e), if (highlighted) Cal.MintTime else Cal.Muted, 12.sp)
    }
}

/** "now" during an event; "in 45m", "in 2h 15m", "in 8h" (hours only beyond 3h), "in 2d". */
private fun countdown(next: CalendarEvent?): String? {
    next ?: return null
    val now = System.currentTimeMillis()
    if (next.begin <= now) return if (next.end > now) "now" else null
    val minutes = ((next.begin - now + MINUTE_MS - 1) / MINUTE_MS)
    val hours = minutes / 60
    return when {
        minutes < 60 -> "in ${minutes}m"
        hours < 3 -> if (minutes % 60 == 0L) "in ${hours}h" else "in ${hours}h ${minutes % 60}m"
        hours < 24 -> "in ${hours}h"
        else -> "in ${hours / 24}d"
    }
}

private fun startTime(context: Context, e: CalendarEvent): String =
    Fmt.time(context, Instant.ofEpochMilli(e.begin).atZone(ZoneId.systemDefault()).toLocalTime())

/** "9:15 – 10:10 am", "11:15 am – 12:10 pm", or "All day". */
private fun timeText(context: Context, e: CalendarEvent): String {
    if (e.allDay) return "All day"
    val end = Fmt.time(context, Instant.ofEpochMilli(e.end).atZone(ZoneId.systemDefault()).toLocalTime())
    // Drop the first am/pm when both times share it.
    val start = startTime(context, e)
    val suffix = end.substringAfterLast(' ', "").takeIf { it.isNotEmpty() && it.all(Char::isLetter) }
    return if (suffix != null && start.endsWith(" $suffix")) "${start.removeSuffix(" $suffix")} – $end" else "$start – $end"
}

/** A card's second line: "9:15 – 10:10 am", "Tomorrow 9:15 – 10:10 am", "All day", "All day tomorrow". */
private fun cardTime(context: Context, e: CalendarEvent): String {
    val day = dayOf(e)
    val today = LocalDate.now()
    return when {
        e.allDay && day == today -> "All day"
        e.allDay -> "All day ${dayWord(day).lowercase()}"
        day == today -> timeText(context, e)
        else -> "${dayWord(day)} ${timeText(context, e)}"
    }
}

/** "Today", "Tomorrow", or the weekday. */
private fun dayWord(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }
}

private fun sampleState(): CalendarState {
    val today = LocalDateTime.now().toLocalDate()
    fun at(d: LocalDate, h: Int, m: Int) = d.atTime(h, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val soonStart = System.currentTimeMillis() + 45 * MINUTE_MS
    val events = listOf(
        CalendarEvent(1, "Main job work session", soonStart, soonStart + 4 * HOUR_MS, false, 0, "Work", ""),
        CalendarEvent(2, "UI Demo", at(today, 17, 30), at(today, 18, 0), false, 0, "Work", ""),
        CalendarEvent(3, "Daily", at(today, 19, 0), at(today, 19, 15), false, 0, "Work", ""),
    )
    return CalendarState.Ready(events, events.first(), doneToday = 4, totalToday = 5)
}
