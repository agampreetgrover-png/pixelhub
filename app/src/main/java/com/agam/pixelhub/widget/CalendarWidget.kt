package com.agam.pixelhub.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import com.agam.pixelhub.calendar.CalendarEvent
import com.agam.pixelhub.calendar.CalendarEvents
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.Tab
import com.agam.pixelhub.ui.theme.NightColors
import com.agam.pixelhub.widget.kit.Caption
import com.agam.pixelhub.widget.kit.CardTone
import com.agam.pixelhub.widget.kit.Figure
import com.agam.pixelhub.widget.kit.Notice
import com.agam.pixelhub.widget.kit.WidgetCard
import com.agam.pixelhub.widget.kit.WidgetPalette
import com.agam.pixelhub.widget.kit.WidgetShape
import com.agam.pixelhub.widget.kit.openTab
import com.agam.pixelhub.widget.kit.isStrip
import com.agam.pixelhub.widget.kit.shapeOf
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import androidx.glance.color.ColorProvider as DayNightColor

/** Android's entry point for the Calendar widget. */
class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget()
}

private const val DAY_MS = 24 * 60 * 60_000L

/** What the widget can show: the coming events, or that it may not read the calendar. */
private sealed interface CalendarState {
    /** [soon]: now until the end of tomorrow. [next]: the first event within a week, for the 1×1. */
    data class Ready(val soon: List<CalendarEvent>, val next: CalendarEvent?) : CalendarState
    data object NoAccess : CalendarState
}

class CalendarWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = load(context)
        provideContent { CalendarContent(context, state) }
    }

    /** The widget picker shows the real widget with sample events. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { CalendarContent(context, sampleState()) }
    }
}

private suspend fun load(context: Context): CalendarState {
    val events = CalendarEvents(context)
    if (!events.hasPermission()) return CalendarState.NoAccess
    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val endOfTomorrow = LocalDate.now().plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
    val week = runCatching { events.observe(now, now + 7 * DAY_MS).first() }.getOrDefault(emptyList())
        .filter { it.end > now }
        // The same event from several calendars (a holiday three times) shows once. Same title
        // AND same start: back-to-back sessions of one class stay separate.
        .distinctBy { it.title.trim().lowercase() to it.begin }
        .sortedWith(compareBy<CalendarEvent> { !it.allDay }.thenBy { it.begin })
        .sortedBy { dayOf(it) }
    return CalendarState.Ready(soon = week.filter { it.begin < endOfTomorrow }, next = week.firstOrNull())
}

/** The day an event belongs to in the list: today for anything already under way. */
private fun dayOf(e: CalendarEvent): LocalDate {
    val begin = Instant.ofEpochMilli(e.begin).atZone(ZoneId.systemDefault()).toLocalDate()
    return maxOf(begin, LocalDate.now())
}

@Composable
private fun CalendarContent(context: Context, state: CalendarState) {
    val palette = WidgetPalette.of(context, CardTone.RAISED)
    val size = LocalSize.current
    val shape = shapeOf(size)
    val open = openTab(context, Tab.CALENDAR)
    if (state is CalendarState.NoAccess) {
        Notice(palette, "Calendar", "Tap to allow calendar access", open, shape)
        return
    }
    val ready = state as CalendarState.Ready
    val events = CalendarEvents(context)
    if (shape == WidgetShape.TINY) {
        val next = ready.next
        if (next == null) {
            Notice(palette, "Free", "Nothing this week", open, shape)
            return
        }
        WidgetCard(palette, actionStartActivity(events.openIntent(next)), "Next: ${next.title}, ${dayWord(dayOf(next))} ${timeText(context, next)}", padding = 10.dp) {
            Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                Figure(if (next.allDay) "All day" else startTime(context, next), palette, if (next.allDay) 16.sp else 20.sp)
                Caption(dayWord(dayOf(next)), palette)
            }
        }
        return
    }
    if (ready.soon.isEmpty()) {
        Notice(palette, "Free today", "Nothing on your calendar today or tomorrow", open, shape)
        return
    }
    val first = ready.soon.first()
    val description = "${ready.soon.size} events today and tomorrow. Next: ${first.title}, ${timeText(context, first)}"
    WidgetCard(
        palette, open, description,
        verticalAlignment = if (shape.isStrip()) Alignment.CenterVertically else Alignment.Top,
    ) {
        when (shape) {
            WidgetShape.STRIP_S, WidgetShape.STRIP_M, WidgetShape.STRIP_L -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                EventLine(context, palette, events, first, showDay = dayOf(first) != LocalDate.now(), modifier = GlanceModifier.defaultWeight())
                if (shape != WidgetShape.STRIP_S && first.location.isNotBlank()) {
                    Spacer(GlanceModifier.width(12.dp))
                    Caption(first.location, palette)
                }
                if (shape == WidgetShape.STRIP_L) ready.soon.getOrNull(1)?.let { second ->
                    Spacer(GlanceModifier.width(16.dp))
                    EventLine(context, palette, events, second, showDay = dayOf(second) != LocalDate.now(), modifier = GlanceModifier.defaultWeight())
                }
            }
            else -> {
                // Headers and rows share the height; a row is never cut off.
                var budget = size.height.value - 28f
                var lastDay: LocalDate? = null
                for (e in ready.soon) {
                    val day = dayOf(e)
                    val needsHeader = day != lastDay
                    val cost = 40f + if (needsHeader) 20f else 0f
                    if (cost > budget) break
                    budget -= cost
                    if (needsHeader) {
                        Caption(dayWord(day), palette)
                        lastDay = day
                    }
                    EventRow(context, palette, events, e, wide = shape != WidgetShape.SQUARE)
                }
            }
        }
    }
}

/** The event's own colour as a thin bar, the way the Calendar screen marks events. */
@Composable
private fun ColorBar(e: CalendarEvent, height: Int) {
    val c = if (e.color == 0) NightColors.dawn else Color(e.color).copy(alpha = 1f)
    Box(GlanceModifier.width(4.dp).height(height.dp).cornerRadius(2.dp).background(DayNightColor(c, c))) {}
}

/** Title over time, beside its colour bar: the strips. */
@Composable
private fun EventLine(context: Context, palette: WidgetPalette, events: CalendarEvents, e: CalendarEvent, showDay: Boolean, modifier: GlanceModifier) {
    Row(modifier.clickable(actionStartActivity(events.openIntent(e))), verticalAlignment = Alignment.CenterVertically) {
        ColorBar(e, 32)
        Spacer(GlanceModifier.width(8.dp))
        Column {
            Caption(e.title, palette, strong = true)
            Caption(if (showDay) "${dayWord(dayOf(e))} ${timeText(context, e)}" else timeText(context, e), palette)
        }
    }
}

/** One row of the list: colour bar, title, and the time (beside it when there's room). */
@Composable
private fun EventRow(context: Context, palette: WidgetPalette, events: CalendarEvents, e: CalendarEvent, wide: Boolean) {
    Row(
        GlanceModifier.fillMaxWidth().height(40.dp)
            .clickable(actionStartActivity(events.openIntent(e)))
            .semantics { contentDescription = "${e.title}, ${timeText(context, e)}. Opens in your calendar." },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorBar(e, 28)
        Spacer(GlanceModifier.width(8.dp))
        if (wide) {
            // The title takes what's left and ends in "…"; the time is always shown whole.
            Box(GlanceModifier.defaultWeight()) { Caption(e.title, palette, strong = true) }
            Spacer(GlanceModifier.width(10.dp))
            Caption(timeText(context, e), palette)
        } else {
            Column {
                Caption(e.title, palette, strong = true)
                Caption(timeText(context, e), palette)
            }
        }
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
    val tomorrow = today.plusDays(1)
    val events = listOf(
        CalendarEvent(1, "DBMS lab", at(today, 14, 15), at(today, 15, 10), false, 0xFF4285F4.toInt(), "Timetable", "E3-3LA1"),
        CalendarEvent(2, "Cognitive abilities", at(today, 16, 15), at(today, 17, 10), false, 0xFF4285F4.toInt(), "Timetable", "E3-214"),
        CalendarEvent(3, "Diwali", at(tomorrow, 0, 0), at(tomorrow.plusDays(1), 0, 0), true, 0xFF0B8043.toInt(), "Holidays", ""),
    )
    return CalendarState.Ready(events, events.first())
}
