package com.agam.pixelhub.ui.calendar

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.calendar.CalendarAgenda
import com.agam.pixelhub.calendar.CalendarEvent
import com.agam.pixelhub.calendar.CalendarEvents
import com.agam.pixelhub.ui.hubCard
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.openSafely
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubColors
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.delay
import java.time.ZoneId

/** Row title and meta, as on the reference list rows. */
internal val EventTitleStyle = HubType.bodyStrong.copy(fontSize = 14.sp, lineHeight = 20.sp)
internal val EventMetaStyle = HubType.small.copy(fontSize = 11.sp, lineHeight = 16.sp, fontFeatureSettings = "tnum")

private const val DAY_MS = 86_400_000L

// ---------------------------------------------------------------------------------------------
// Shared by the Home card and the Calendar page
// ---------------------------------------------------------------------------------------------

@Composable
internal fun rememberCalendarEvents(): CalendarEvents {
    val context = LocalContext.current
    return remember { CalendarEvents(context.applicationContext) }
}

/** Whether calendar access is granted (re-checked on every resume) and how to ask for it. */
internal class CalendarAccess(val granted: Boolean, val request: () -> Unit)

@Composable
internal fun rememberCalendarAccess(events: CalendarEvents): CalendarAccess {
    val context = LocalContext.current
    val activity = LocalActivity.current
    var granted by remember { mutableStateOf(events.hasPermission()) }
    // Picks up a grant made in system settings while PixelHub was in the background.
    LifecycleResumeEffect(Unit) {
        granted = events.hasPermission()
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        // Denied twice: Android stops asking, so the button would do nothing. Open the app's
        // settings page instead, where calendar access can be turned on.
        if (!ok && activity?.shouldShowRequestPermissionRationale(Manifest.permission.READ_CALENDAR) != true) {
            Toast.makeText(context, "Turn on Calendar under Permissions", Toast.LENGTH_LONG).show()
            openSafely(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
        }
    }
    return CalendarAccess(granted) { launcher.launch(Manifest.permission.READ_CALENDAR) }
}

/** The current time, refreshed on each minute boundary and on resume. */
@Composable
internal fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleResumeEffect(Unit) {
        now = System.currentTimeMillis()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
            now = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
internal fun rememberIs24h(): Boolean {
    val context = LocalContext.current
    return DateFormat.is24HourFormat(context)
}

/** The event's calendar colour, opaque; violet when the calendar has none. */
internal fun eventColor(event: CalendarEvent, c: HubColors): Color =
    if (event.color == 0) c.dawn else Color(event.color).copy(alpha = 1f)

/** Starts [intent], or says so in a toast when no app can handle it. */
internal fun startOrToast(context: Context, intent: Intent, failure: String) {
    try {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, failure, Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        Toast.makeText(context, failure, Toast.LENGTH_SHORT).show()
    }
}

internal fun openEvent(context: Context, events: CalendarEvents, event: CalendarEvent) =
    startOrToast(context, events.openIntent(event), "No calendar app to open this event")

internal fun newEvent(context: Context, events: CalendarEvents, startMillis: Long) =
    startOrToast(context, events.newEventIntent(startMillis), "No calendar app to add an event")

/**
 * One event: a round well washed with its calendar's colour, the title, a muted line with
 * when and which calendar, and the place when there is one. Opens the event when tapped.
 */
@Composable
internal fun CalendarEventRow(
    event: CalendarEvent,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showLocation: Boolean = true,
) {
    val c = Hub.colors
    val tone = eventColor(event, c)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .pressable(onClick = onClick, pressedScale = 0.98f, onClickLabel = "Open in calendar")
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tinted, not filled, so the ink icon keeps its contrast on any calendar colour; the
        // calendar's name in the meta line carries the meaning.
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(tone.copy(alpha = if (c.isDark) 0.30f else 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (event.allDay) Icons.Outlined.CalendarToday else Icons.Outlined.Event,
                contentDescription = null,
                tint = c.ink,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(event.title, style = EventTitleStyle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = EventMetaStyle, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (showLocation && event.location.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Place, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(event.location, style = EventMetaStyle, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** A compact ink pill for a card row ("Connect"). 48dp to the touch. */
@Composable
internal fun SmallInkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Hub.colors
    Box(
        modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(c.ink)
            .pressable(onClick = onClick, pressedScale = 0.94f)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = HubType.button, color = c.ground, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// Home card
// ---------------------------------------------------------------------------------------------

/**
 * The next three events in the coming week on Home, from every calendar on the phone.
 * "View all" opens the Calendar page; each row opens its event in the calendar app.
 */
@Composable
fun UpcomingEventsCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val events = rememberCalendarEvents()
    val access = rememberCalendarAccess(events)
    val now = rememberNow()
    val is24h = rememberIs24h()
    val zone = remember { ZoneId.systemDefault() }
    val today = CalendarAgenda.date(now, zone)

    // One query per day; the minute tick only trims what has ended.
    val flow = remember(access.granted, today) {
        val from = CalendarAgenda.startOfDay(today, zone)
        events.observe(from, from + 8 * DAY_MS)
    }
    val all by flow.collectAsStateWithLifecycle<List<CalendarEvent>?>(initialValue = null)
    val upcoming = all?.filter { it.end > now && it.begin < now + 7 * DAY_MS }?.take(3)

    Column(
        modifier
            .fillMaxWidth()
            .hubCard(c.raised)
            .animateContentSize(tween(if (reduced) 0 else 220))
            .padding(horizontal = 6.dp, vertical = 10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Calendar", style = HubType.section, color = c.ink, modifier = Modifier.weight(1f))
            Text(
                "View all",
                style = HubType.small,
                color = c.inkMuted,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .pressable(onClick = onOpen, onClickLabel = "Open calendar")
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        when {
            !access.granted -> Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(c.well), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Connect your calendar", style = EventTitleStyle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("See your Google Calendar here", style = EventMetaStyle, color = c.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                SmallInkButton("Connect", onClick = access.request)
            }
            upcoming == null -> Spacer(Modifier.heightIn(min = 60.dp))
            upcoming.isEmpty() -> Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(c.well), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.EventAvailable, contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text("Nothing on your calendar this week", style = EventTitleStyle, color = c.ink, modifier = Modifier.weight(1f))
            }
            else -> upcoming.forEach { e ->
                CalendarEventRow(
                    event = e,
                    meta = CalendarAgenda.upcomingLabel(e, now, zone, is24h),
                    onClick = { openEvent(context, events, e) },
                    showLocation = false,
                )
            }
        }
    }
}
