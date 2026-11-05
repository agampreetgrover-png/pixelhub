package com.agam.pixelhub.ui.calendar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.calendar.CalendarAgenda
import com.agam.pixelhub.calendar.CalendarEvent
import com.agam.pixelhub.ui.BackButton
import com.agam.pixelhub.ui.CardRadius
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.Gutter
import com.agam.pixelhub.ui.PrimaryButton
import com.agam.pixelhub.ui.QuietButton
import com.agam.pixelhub.ui.ScreenScaffold
import com.agam.pixelhub.ui.SectionHeader
import com.agam.pixelhub.ui.hubCard
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.motion.rememberStagger
import com.agam.pixelhub.ui.motion.staggerIn
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val LOOKAHEAD_DAYS = 30L
private const val STRIP_DAYS = 14L
private const val SEP = " • "

/**
 * Every calendar on the phone (Google Calendar included, through Android's sync) for the
 * next 30 days: the next event on a violet card, two weeks of days to filter by, and an
 * agenda with a card per day. Events open in the calendar app; "New event" opens its editor.
 */
@Composable
fun CalendarScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val reduced = LocalReducedMotion.current
    val events = rememberCalendarEvents()
    val access = rememberCalendarAccess(events)
    val now = rememberNow()
    val is24h = rememberIs24h()
    val zone = remember { ZoneId.systemDefault() }
    val today = CalendarAgenda.date(now, zone)
    val lastDay = today.plusDays(LOOKAHEAD_DAYS - 1)

    // One query per day (and per grant); the minute tick only trims what has ended.
    val flow = remember(access.granted, today) {
        events.observe(
            CalendarAgenda.startOfDay(today, zone),
            CalendarAgenda.startOfDay(today.plusDays(LOOKAHEAD_DAYS), zone),
        )
    }
    val all by flow.collectAsStateWithLifecycle<List<CalendarEvent>?>(initialValue = null)
    val live = remember(all, now) { all?.filter { maxOf(it.end, it.begin + 1) > now } }
    val byDay = remember(live, today) {
        live?.let { CalendarAgenda.groupByDay(it, zone, today, lastDay) }.orEmpty()
    }
    val next = remember(live, now) { live?.let { CalendarAgenda.nextEvent(it, now) } }

    // Null shows every day; a date filters the agenda to it. Resets at midnight.
    var selected by remember(today) { mutableStateOf<LocalDate?>(null) }
    val shownDays: List<LocalDate> = selected?.let { listOf(it) } ?: byDay.keys.toList()

    val stagger = rememberStagger()
    val list = rememberLazyListState()
    val placement = if (reduced) null else tween<IntOffset>(250, easing = Motion.EaseOut)

    ScreenScaffold(title = "Calendar", navigationIcon = { BackButton(onBack) }) { padding ->
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
        ) {
            if (!access.granted) {
                item(key = "access") {
                    AccessCard(
                        onAllow = access.request,
                        modifier = Modifier.padding(horizontal = Gutter).staggerIn(stagger, "access", 0),
                    )
                }
                return@LazyColumn
            }

            item(key = "hero") {
                CalendarHero(
                    next = next,
                    loading = all == null,
                    now = now,
                    zone = zone,
                    is24h = is24h,
                    onOpen = { e -> openEvent(context, events, e) },
                    onNewEvent = { newEvent(context, events, CalendarAgenda.suggestedStart(selected ?: today, now, zone)) },
                    modifier = Modifier.padding(horizontal = Gutter).staggerIn(stagger, "hero", 0),
                )
            }
            item(key = "strip") {
                WeekStrip(
                    today = today,
                    selected = selected,
                    counts = { d -> byDay[d]?.size ?: 0 },
                    onSelect = { d -> selected = if (selected == d) null else d },
                    modifier = Modifier.padding(top = 18.dp).staggerIn(stagger, "strip", 1),
                )
            }

            if (all == null) return@LazyColumn

            if (byDay.isEmpty() && selected == null) {
                item(key = "empty") {
                    EmptyCard(
                        onNewEvent = { newEvent(context, events, CalendarAgenda.suggestedStart(today, now, zone)) },
                        modifier = Modifier.padding(horizontal = Gutter).padding(top = 18.dp).staggerIn(stagger, "empty", 2),
                    )
                }
                return@LazyColumn
            }

            item(key = "header") {
                val sel = selected
                SectionHeader(
                    text = if (sel == null) "Next 30 days" else "One day",
                    action = if (sel == null) null else "Show all",
                    onAction = if (sel == null) null else ({ selected = null }),
                    modifier = Modifier.animateItem(placementSpec = placement),
                )
            }
            shownDays.forEachIndexed { i, day ->
                item(key = "day-$day") {
                    DayCard(
                        day = day,
                        today = today,
                        events = byDay[day].orEmpty(),
                        zone = zone,
                        is24h = is24h,
                        onOpen = { e -> openEvent(context, events, e) },
                        onNewEvent = { newEvent(context, events, CalendarAgenda.suggestedStart(day, now, zone)) },
                        modifier = Modifier
                            .animateItem(fadeInSpec = tween(200), placementSpec = placement, fadeOutSpec = tween(150))
                            .padding(horizontal = Gutter)
                            .padding(bottom = 12.dp)
                            .staggerIn(stagger, "day-$day", 2 + i),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// No access
// ---------------------------------------------------------------------------------------------

@Composable
private fun AccessCard(onAllow: () -> Unit, modifier: Modifier = Modifier) {
    val c = Hub.colors
    Column(modifier.fillMaxWidth().hubCard(c.raised).padding(20.dp)) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(c.dawn), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = c.onDawn, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("See your calendar here", style = HubType.section, color = c.ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "PixelHub reads the calendars on this phone, Google Calendar included, to show what's coming up. " +
                "It only reads them here; nothing is sent anywhere.",
            style = HubType.body,
            color = c.inkMuted,
        )
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Allow calendar access", onClick = onAllow, modifier = Modifier.fillMaxWidth())
    }
}

// ---------------------------------------------------------------------------------------------
// Hero: the next event on the violet card, "New event" seated in the notched corner
// ---------------------------------------------------------------------------------------------

private val HeroHeight = 204.dp
private val NotchWidth = 158.dp
private val NotchHeight = 64.dp
private val NotchGap = 6.dp

@Composable
private fun CalendarHero(
    next: CalendarEvent?,
    loading: Boolean,
    now: Long,
    zone: ZoneId,
    is24h: Boolean,
    onOpen: (CalendarEvent) -> Unit,
    onNewEvent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Hub.colors
    val notch = remember { CalendarNotchShape(CardRadius, 18.dp, NotchWidth, NotchHeight) }
    val today = CalendarAgenda.date(now, zone)
    Box(modifier.fillMaxWidth().height(HeroHeight)) {
        Column(
            Modifier
                .fillMaxSize()
                .then(
                    if (next != null) {
                        Modifier.pressable(onClick = { onOpen(next) }, pressedScale = 0.98f, showRipple = false, onClickLabel = "Open in calendar")
                    } else Modifier,
                )
                .clip(notch)
                .background(c.dawn)
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 14.dp),
        ) {
            if (!loading) HeroContent(next, now, zone, today, is24h)
        }
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .width(NotchWidth - NotchGap)
                .height(NotchHeight - NotchGap)
                .clip(CircleShape)
                .background(c.chrome)
                .pressable(onClick = onNewEvent, pressedScale = 0.95f, onClickLabel = "New event")
                .padding(start = 5.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(c.onChrome), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Add, contentDescription = null, tint = c.chrome, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("New event", style = HubType.button, color = c.onChrome, maxLines = 1)
        }
    }
}

@Composable
private fun ColumnScope.HeroContent(next: CalendarEvent?, now: Long, zone: ZoneId, today: LocalDate, is24h: Boolean) {
    val c = Hub.colors
    val on = c.onDawn
    val eyebrowStyle = HubType.small.copy(fontWeight = FontWeight.Medium)
    if (next == null) {
        Text("Next 30 days", style = eyebrowStyle, color = on)
        Text("All clear", style = HubType.clockLarge, color = on, maxLines = 1)
        Spacer(Modifier.weight(1f))
        Text(
            "No events coming up",
            style = HubType.small,
            color = on,
            maxLines = 2,
            modifier = Modifier.padding(end = NotchWidth - 12.dp),
        )
        return
    }
    val ongoing = next.begin <= now && now < next.end
    val startDate = CalendarAgenda.date(next.begin, zone)
    val eyebrow = when {
        next.allDay -> CalendarAgenda.dayLabel(if (ongoing) today else startDate, today)
        ongoing -> "Happening now"
        else -> "Next up" + SEP + CalendarAgenda.dayLabel(startDate, today)
    }
    Text(eyebrow, style = eyebrowStyle, color = on, maxLines = 1, overflow = TextOverflow.Ellipsis)
    when {
        next.allDay -> Text("All day", style = HubType.clockLarge, color = on, maxLines = 1)
        ongoing -> Text("Now", style = HubType.clockLarge, color = on, maxLines = 1)
        else -> {
            val (figure, suffix) = CalendarAgenda.clockParts(
                java.time.Instant.ofEpochMilli(next.begin).atZone(zone).toLocalTime(), is24h,
            )
            Row {
                Text(figure, style = HubType.clockLarge, color = on, maxLines = 1, modifier = Modifier.alignByBaseline())
                if (suffix != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(suffix, style = HubType.section, color = on, maxLines = 1, modifier = Modifier.alignByBaseline())
                }
            }
        }
    }
    Text(next.title, style = HubType.bodyStrong, color = on, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.weight(1f))
    val countdown = when {
        next.allDay && ongoing -> "All day today"
        ongoing -> "ends in " + Fmt.untilText(next.end, now)
        else -> "in " + Fmt.untilText(next.begin, now)
    }
    val where = listOf(next.calendarName, next.location).filter { it.isNotBlank() }.joinToString(SEP)
    Column(Modifier.padding(end = NotchWidth - 12.dp)) {
        Text(countdown, style = HubType.bodyStrong.copy(fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = "tnum"), color = on, maxLines = 1)
        if (where.isNotEmpty()) {
            Text(where, style = HubType.small, color = on, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * A rounded card with its bottom-right corner cut away: convex corners where the card meets
 * the cut, a pill-round inner corner that hugs the button seated there.
 */
private class CalendarNotchShape(
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
// Two weeks of days
// ---------------------------------------------------------------------------------------------

@Composable
private fun WeekStrip(
    today: LocalDate,
    selected: LocalDate?,
    counts: (LocalDate) -> Int,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val days = remember(today) { (0 until STRIP_DAYS).map { today.plusDays(it) } }
    LazyRow(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Gutter - 4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(days, key = { it.toEpochDay() }) { day ->
            DayWell(
                day = day,
                isToday = day == today,
                isSelected = day == selected,
                count = counts(day),
                onClick = { onSelect(day) },
            )
        }
    }
}

@Composable
private fun DayWell(day: LocalDate, isToday: Boolean, isSelected: Boolean, count: Int, onClick: () -> Unit) {
    val c = Hub.colors
    val locale = Locale.getDefault()
    val weekday = remember(day, locale) { day.format(DateTimeFormatter.ofPattern("EEE", locale)) }
    val spoken = remember(day, count, isToday, locale) {
        val date = day.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
        val events = when (count) {
            0 -> "no events"
            1 -> "1 event"
            else -> "$count events"
        }
        (if (isToday) "Today, " else "") + "$date, $events"
    }
    val fill by animateColorAsState(if (isSelected) c.ink else c.raised, tween(180), label = "dayFill")
    val figure by animateColorAsState(if (isSelected) c.ground else c.ink, tween(180), label = "dayFigure")
    Column(
        Modifier
            .width(50.dp)
            .clip(CircleShape)
            .pressable(onClick = onClick, pressedScale = 0.92f, showRipple = false, role = null)
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                role = Role.Tab
                this.selected = isSelected
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (isToday) "Today" else weekday,
            style = HubType.small.copy(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = if (isToday) FontWeight.Medium else FontWeight.Normal),
            color = if (isToday) c.ink else c.inkMuted,
            maxLines = 1,
        )
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(fill)
                // Today keeps a ring when not selected, so it reads without relying on colour.
                .then(if (isToday && !isSelected) Modifier.border(1.5.dp, c.ink, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${day.dayOfMonth}",
                style = HubType.button.copy(fontFeatureSettings = "tnum"),
                color = figure,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (count > 0) c.ink else Color.Transparent),
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Agenda
// ---------------------------------------------------------------------------------------------

@Composable
private fun DayCard(
    day: LocalDate,
    today: LocalDate,
    events: List<CalendarEvent>,
    zone: ZoneId,
    is24h: Boolean,
    onOpen: (CalendarEvent) -> Unit,
    onNewEvent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Hub.colors
    val label = CalendarAgenda.dayLabel(day, today)
    val isNear = day == today || day == today.plusDays(1)
    Column(modifier.fillMaxWidth().hubCard(c.raised).padding(horizontal = 6.dp, vertical = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = HubType.section, color = c.ink, modifier = Modifier.weight(1f), maxLines = 1)
            if (isNear) {
                Text(
                    day.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())),
                    style = HubType.small,
                    color = c.inkMuted,
                    maxLines = 1,
                )
            }
        }
        if (events.isEmpty()) {
            Text(
                if (day == today) "Nothing else today" else "Nothing planned",
                style = HubType.body,
                color = c.inkMuted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            QuietButton(
                "New event",
                onClick = onNewEvent,
                icon = Icons.Outlined.Add,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        } else {
            events.forEach { e ->
                val time = CalendarAgenda.timeLabel(e, day, zone, is24h)
                val meta = if (e.calendarName.isBlank()) time else time + SEP + e.calendarName
                CalendarEventRow(event = e, meta = meta, onClick = { onOpen(e) })
            }
        }
    }
}

@Composable
private fun EmptyCard(onNewEvent: () -> Unit, modifier: Modifier = Modifier) {
    val c = Hub.colors
    Column(
        modifier.fillMaxWidth().hubCard(c.raised).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(c.well), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.EventAvailable, contentDescription = null, tint = c.ink, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text("No events in the next 30 days", style = HubType.section, color = c.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(
            "Events you add in Google Calendar show up here.",
            style = HubType.body,
            color = c.inkMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        PrimaryButton("New event", onClick = onNewEvent, icon = Icons.Outlined.Add)
    }
}
