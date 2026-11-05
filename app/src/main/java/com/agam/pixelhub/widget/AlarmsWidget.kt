package com.agam.pixelhub.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.Switch
import androidx.glance.appwidget.SwitchDefaults
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import com.agam.pixelhub.container
import com.agam.pixelhub.data.Alarm
import com.agam.pixelhub.data.AlarmRepository
import com.agam.pixelhub.scheduling.Days
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
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/** Android's entry point for the Alarms widget. */
class AlarmsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AlarmsWidget()
}

private val AlarmId = ActionParameters.Key<Long>("alarmId")

/** An alarm with when it next rings, worked out once per draw. */
private class Entry(val alarm: Alarm, val next: ZonedDateTime)

class AlarmsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = context.container.alarms
        val first = repo.all.first()
        provideContent {
            // Follows the alarms live: a switch or an edit in the app redraws at once.
            val alarms by remember { repo.all }.collectAsState(first)
            AlarmsContent(context, ordered(alarms), ZonedDateTime.now())
        }
    }

    /** The widget picker shows the real widget with sample alarms. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val samples = listOf(
            Alarm(id = 1, hour = 7, minute = 0, label = "Gym"),
            Alarm(id = 2, hour = 9, minute = 30, daysMask = Days.WEEKDAYS),
            Alarm(id = 3, hour = 18, minute = 0, enabled = false),
        )
        provideContent { AlarmsContent(context, ordered(samples), ZonedDateTime.now()) }
    }
}

/** The switch: exactly what the Alarms screen's switch does (save → reschedule or cancel). */
class AlarmToggleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[AlarmId] ?: return
        val c = context.container
        val a = c.alarms.all.first().firstOrNull { it.id == id } ?: return
        c.alarms.save(a.copy(enabled = !a.enabled))
        // WidgetSync redraws on the change too; this makes it instant.
        AlarmsWidget().update(context, glanceId)
    }
}

/** Alarms that are on, soonest first, then the ones that are off. */
private fun ordered(alarms: List<Alarm>): List<Entry> {
    val now = ZonedDateTime.now()
    return alarms.map { Entry(it, AlarmRepository.nextTrigger(it, now)) }
        .sortedWith(compareByDescending<Entry> { it.alarm.enabled }.thenBy { it.next })
}

@Composable
private fun AlarmsContent(context: Context, entries: List<Entry>, now: ZonedDateTime) {
    val palette = WidgetPalette.of(context, CardTone.DAWN)
    val size = LocalSize.current
    val shape = shapeOf(size)
    val openApp = openTab(context, Tab.ALARMS)
    if (entries.isEmpty()) {
        Notice(palette, "No alarms", "Tap to set one", openApp, shape)
        return
    }
    val first = entries.first()
    val anyOn = first.alarm.enabled
    val description = if (anyOn) {
        "Next alarm ${time(context, first.alarm)}, ${day(first.next, now)}"
    } else {
        "All alarms are off"
    }
    WidgetCard(
        palette, openApp, description,
        padding = if (shape == WidgetShape.TINY) 10.dp else 14.dp,
        verticalAlignment = if (shape in LIST_SHAPES) Alignment.Top else Alignment.CenterVertically,
    ) {
        when (shape) {
            WidgetShape.TINY -> Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                if (anyOn) {
                    Figure(shortTime(first.alarm), palette, 24.sp)
                    Caption(day(first.next, now), palette)
                } else {
                    Caption("All off", palette, strong = true)
                }
            }
            WidgetShape.STRIP_S -> Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Figure(time(context, first.alarm), palette, 20.sp)
                Caption(if (anyOn) day(first.next, now) else "Off", palette, strong = true)
                if (first.alarm.label.isNotBlank()) Caption(first.alarm.label, palette)
            }
            WidgetShape.STRIP_M, WidgetShape.STRIP_L -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight()) {
                    Figure(time(context, first.alarm), palette, 20.sp)
                    Caption(detail(first.alarm), palette)
                }
                Caption(if (anyOn) day(first.next, now) else "Off", palette, strong = true)
                if (shape == WidgetShape.STRIP_L && first.alarm.daysMask != 0) {
                    Spacer(GlanceModifier.width(12.dp))
                    Caption(Days.label(first.alarm.daysMask), palette)
                }
                Spacer(GlanceModifier.width(8.dp))
                AlarmSwitch(context, palette, first.alarm)
            }
            else -> {
                val rows = ((size.height.value - 28f) / 48f).toInt().coerceIn(1, 6)
                entries.take(rows).forEach { e ->
                    Row(GlanceModifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(GlanceModifier.defaultWeight()) {
                            Figure(time(context, e.alarm), palette, 18.sp)
                            Caption(if (e.alarm.enabled) detail(e.alarm) else "Off", palette)
                        }
                        AlarmSwitch(context, palette, e.alarm)
                    }
                }
            }
        }
    }
}

/** Android's own switch, coloured for the violet card. */
@Composable
private fun AlarmSwitch(context: Context, palette: WidgetPalette, a: Alarm) {
    Switch(
        checked = a.enabled,
        onCheckedChange = actionRunCallback<AlarmToggleAction>(actionParametersOf(AlarmId to a.id)),
        colors = SwitchDefaults.colors(
            checkedThumbColor = palette.ink,
            uncheckedThumbColor = palette.muted,
            checkedTrackColor = palette.track,
            uncheckedTrackColor = palette.track,
        ),
        modifier = GlanceModifier.semantics {
            contentDescription = buildString {
                append("Alarm ${time(context, a)}")
                if (a.label.isNotBlank()) append(", ${a.label}")
                append(if (a.enabled) ", on" else ", off")
            }
        },
    )
}

private fun time(context: Context, a: Alarm): String = Fmt.time(context, LocalTime.of(a.hour, a.minute))

/** "7:00" without am/pm, for the smallest tile; the day sits under it. */
private fun shortTime(a: Alarm): String {
    val h = if (a.hour % 12 == 0) 12 else a.hour % 12
    return "%d:%02d".format(h, a.minute)
}

/** The alarm's own line: its label, or its repeat days. */
private fun detail(a: Alarm): String = a.label.ifBlank { if (a.daysMask == 0) "Rings once" else Days.label(a.daysMask) }

/** When it next rings, as a day: "Today", "Tomorrow", "Fri". Never a countdown that goes stale. */
private fun day(next: ZonedDateTime, now: ZonedDateTime): String {
    val d = next.toLocalDate()
    val today = now.toLocalDate()
    return when (d) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }
}
