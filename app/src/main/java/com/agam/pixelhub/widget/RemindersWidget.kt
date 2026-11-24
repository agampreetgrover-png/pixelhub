package com.agam.pixelhub.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import com.agam.pixelhub.R
import com.agam.pixelhub.container
import com.agam.pixelhub.data.Reminder
import com.agam.pixelhub.scheduling.Recurrence
import com.agam.pixelhub.scheduling.RepeatType
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.Tab
import com.agam.pixelhub.widget.kit.Caption
import com.agam.pixelhub.widget.kit.CardTone
import com.agam.pixelhub.widget.kit.Figure
import com.agam.pixelhub.widget.kit.Notice
import com.agam.pixelhub.widget.kit.WidgetCard
import com.agam.pixelhub.widget.kit.WidgetPalette
import com.agam.pixelhub.widget.kit.WidgetShape
import com.agam.pixelhub.widget.kit.openTab
import com.agam.pixelhub.widget.kit.LIST_SHAPES
import com.agam.pixelhub.widget.kit.shapeOf
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Android's entry point for the Reminders widget. */
class RemindersWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RemindersWidget()
}

private val ReminderId = ActionParameters.Key<Long>("reminderId")

class RemindersWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = context.container.reminders
        val first = openOf(repo.all.first())
        provideContent {
            // Follows the reminders live: a ✓ or an edit in the app redraws at once.
            val all by remember { repo.all }.collectAsState(null)
            RemindersContent(context, all?.let(::openOf) ?: first, ZonedDateTime.now())
        }
    }

    /** The widget picker shows the real widget with sample reminders. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { RemindersContent(context, sampleReminders(), ZonedDateTime.now()) }
    }
}

/** Open reminders, soonest first, so anything late comes first. */
private fun openOf(all: List<Reminder>): List<Reminder> = all.filter { !it.done }.sortedBy { it.triggerAt }

/** ✓ on a one-off reminder: the same completion as the app's checkbox (cancels its alarm). */
class ReminderDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ReminderId] ?: return
        val c = context.container
        val r = c.reminders.all.first().firstOrNull { it.id == id } ?: return
        if (!r.done && !r.recurrence.isRepeating) c.reminders.setDone(r, true)
        // WidgetSync redraws on the change too; this makes it instant.
        RemindersWidget().update(context, glanceId)
    }
}

@Composable
private fun RemindersContent(context: Context, open: List<Reminder>, now: ZonedDateTime) {
    val palette = WidgetPalette.of(context, CardTone.SUN)
    val size = LocalSize.current
    val shape = shapeOf(size)
    val openApp = openTab(context, Tab.REMINDERS)
    if (open.isEmpty()) {
        Notice(palette, "All clear", "Nothing to remind you about", openApp, shape)
        return
    }
    val today = now.toLocalDate()
    val dueToday = open.count { localDate(it) <= today }
    val first = open.first()
    val description = buildString {
        append(if (dueToday == 1) "1 reminder today" else "$dueToday reminders today")
        append(". Next: ${first.title}, ${whenText(context, first, now)}")
    }
    WidgetCard(
        palette, openApp, description,
        padding = if (shape == WidgetShape.TINY) 10.dp else 14.dp,
        verticalAlignment = if (shape in LIST_SHAPES) Alignment.Top else Alignment.CenterVertically,
    ) {
        when (shape) {
            WidgetShape.TINY -> Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                if (dueToday == 0) {
                    Caption("All clear", palette, strong = true)
                } else {
                    Figure("$dueToday", palette, 30.sp)
                    Caption("today", palette)
                }
            }
            WidgetShape.STRIP_S, WidgetShape.STRIP_M, WidgetShape.STRIP_L -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    Figure(whenText(context, first, now), palette, 18.sp)
                    Caption(first.title, palette, strong = true)
                }
                if (shape == WidgetShape.STRIP_L && open.size > 1) {
                    Caption("+${open.size - 1} more", palette)
                    Spacer(GlanceModifier.width(10.dp))
                }
                if (shape != WidgetShape.STRIP_S) Trailing(context, palette, first)
            }
            else -> {
                Caption(if (dueToday > 0) "Today" else "Next", palette)
                Spacer(GlanceModifier.height(4.dp))
                val rows = ((size.height.value - 28f - 22f) / 42f).toInt().coerceIn(1, 6)
                open.take(rows).forEach { r -> ReminderRow(context, palette, r, now) }
            }
        }
    }
}

/** One reminder in the list: title and when, with ✓ (one-off) or the repeat mark (repeating). */
@Composable
private fun ReminderRow(context: Context, palette: WidgetPalette, r: Reminder, now: ZonedDateTime) {
    Row(GlanceModifier.fillMaxWidth().height(42.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            Caption(r.title, palette, strong = true)
            Caption(whenText(context, r, now), palette)
        }
        Trailing(context, palette, r)
    }
}

/** ✓ for a one-off reminder, a repeat mark for a repeating one (those wait for their next time, as in the app). */
@Composable
private fun Trailing(context: Context, palette: WidgetPalette, r: Reminder) {
    if (r.recurrence.isRepeating) {
        Box(GlanceModifier.size(40.dp), contentAlignment = Alignment.Center) {
            Image(
                ImageProvider(R.drawable.ic_widget_repeat),
                contentDescription = "Repeats",
                colorFilter = ColorFilter.tint(palette.muted),
                modifier = GlanceModifier.size(18.dp),
            )
        }
        return
    }
    Box(
        GlanceModifier
            .size(40.dp)
            .clickable(actionRunCallback<ReminderDoneAction>(actionParametersOf(ReminderId to r.id)))
            .semantics { contentDescription = "Mark ${r.title} done" },
        contentAlignment = Alignment.Center,
    ) {
        Box(GlanceModifier.size(30.dp).cornerRadius(15.dp).background(palette.track), contentAlignment = Alignment.Center) {
            Image(
                ImageProvider(R.drawable.ic_widget_check),
                contentDescription = null,
                colorFilter = ColorFilter.tint(palette.ink),
                modifier = GlanceModifier.size(18.dp),
            )
        }
    }
}

private fun localDate(r: Reminder) = Instant.ofEpochMilli(r.triggerAt).atZone(ZoneId.systemDefault()).toLocalDate()

private val DayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/**
 * "6:00 pm", "Late, 9:00 am", "Late, yesterday 9:00 am", "Late, 12 Nov 9:00 am",
 * "Tomorrow 7:00 am", "Fri 9:00 am", "12 Nov 9:00 am".
 */
private fun whenText(context: Context, r: Reminder, now: ZonedDateTime): String {
    val at = Instant.ofEpochMilli(r.triggerAt).atZone(now.zone)
    val time = Fmt.time(context, at.toLocalTime())
    val today = now.toLocalDate()
    val date = at.toLocalDate()
    return when {
        !r.recurrence.isRepeating && r.triggerAt < now.toInstant().toEpochMilli() -> when (date) {
            today -> "Late, $time"
            today.minusDays(1) -> "Late, yesterday $time"
            else -> "Late, ${at.format(DayMonth)} $time"
        }
        date == today -> time
        date == today.plusDays(1) -> "Tomorrow $time"
        // Only ahead: a missed repeating time from an earlier day mustn't read as next week.
        date > today && date < today.plusDays(7) -> "${at.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} $time"
        else -> "${at.format(DayMonth)} $time"
    }
}

private fun sampleReminders(): List<Reminder> {
    val now = LocalDateTime.now().withSecond(0).withNano(0)
    fun at(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return listOf(
        Reminder(id = 1, title = "Call mom", anchor = now.plusHours(2), triggerAt = at(now.plusHours(2))),
        Reminder(id = 2, title = "Pay rent", anchor = now.plusHours(4), triggerAt = at(now.plusHours(4))),
        Reminder(
            id = 3, title = "Gym", anchor = now.plusDays(1).withHour(7).withMinute(0),
            triggerAt = at(now.plusDays(1).withHour(7).withMinute(0)), recurrence = Recurrence(RepeatType.DAILY),
        ),
    )
}
