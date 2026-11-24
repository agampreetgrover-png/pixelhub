package com.agam.pixelhub.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * One occurrence of an event from a calendar on the phone (Google Calendar events arrive
 * here through Android's own Calendar sync). For all-day events [begin] and [end] are local
 * midnights, [end] exclusive, so they land on the right day in any time zone.
 */
data class CalendarEvent(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val calendarName: String,
    val location: String,
) {
    /** Stable across re-queries; repeating events share an eventId, so the start is part of it. */
    val key: String get() = "$eventId@$begin"
}

/**
 * Reads the phone's calendars through CalendarContract. Read-only: no sign-in or API key,
 * only the READ_CALENDAR permission. PixelHub's own reminder events are left out, since
 * Home already shows those reminders.
 */
class CalendarEvents(context: Context) {
    private val context = context.applicationContext
    private val resolver get() = context.contentResolver

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /**
     * Event occurrences overlapping [fromMillis, toMillis), earliest first, from visible
     * calendars. Re-queries whenever any calendar data changes. Emits an empty list without
     * permission; collect again (with a new flow) once permission is granted.
     */
    fun observe(fromMillis: Long, toMillis: Long): Flow<List<CalendarEvent>> = callbackFlow {
        if (!hasPermission()) {
            send(emptyList())
            awaitClose()
            return@callbackFlow
        }
        val changes = Channel<Unit>(Channel.CONFLATED)
        val observer = object : ContentObserver(null as Handler?) {
            override fun onChange(selfChange: Boolean) {
                changes.trySend(Unit)
            }
        }
        val registered = runCatching {
            resolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        }.isSuccess
        changes.trySend(Unit)
        val worker = launch {
            var first = true
            for (change in changes) {
                // A sync fires a burst of changes; let it settle before reading again.
                if (!first) delay(REQUERY_DEBOUNCE_MS)
                first = false
                send(query(fromMillis, toMillis))
            }
        }
        awaitClose {
            worker.cancel()
            if (registered) resolver.unregisterContentObserver(observer)
        }
    }.flowOn(Dispatchers.IO).distinctUntilChanged()

    /** Opens this occurrence in the calendar app (Google Calendar's event page). */
    fun openIntent(event: CalendarEvent): Intent {
        val zone = ZoneId.systemDefault()
        // The calendar app expects all-day instances in UTC midnights, as they are stored.
        val begin = if (event.allDay) CalendarAgenda.localToAllDayUtc(event.begin, zone) else event.begin
        val end = if (event.allDay) CalendarAgenda.localToAllDayUtc(event.end, zone) else event.end
        return Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(Events.CONTENT_URI, event.eventId))
            // Own identity per occurrence: extras don't count, so widget PendingIntents for two
            // occurrences of one repeating event would otherwise collide and share the later's times.
            .setIdentifier(event.key)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
    }

    /** Opens the calendar app's own new-event editor, starting at [startMillis] for an hour. */
    fun newEventIntent(startMillis: Long): Intent =
        Intent(Intent.ACTION_INSERT, Events.CONTENT_URI)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, startMillis + HOUR_MS)

    private fun query(fromMillis: Long, toMillis: Long): List<CalendarEvent> {
        if (!hasPermission()) return emptyList()
        val zone = ZoneId.systemDefault()
        // All-day instances are stored at UTC midnights, up to a day off local time, so read a
        // day either side and trim after converting them.
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, fromMillis - DAY_MS)
            ContentUris.appendId(it, toMillis + DAY_MS)
        }.build()
        val result = ArrayList<CalendarEvent>()
        try {
            resolver.query(uri, PROJECTION, SELECTION, null, "${Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    if (CalendarAgenda.isFromPixelHub(c.getString(COL_DESCRIPTION))) continue
                    val allDay = !c.isNull(COL_ALL_DAY) && c.getInt(COL_ALL_DAY) == 1
                    val rawBegin = c.getLong(COL_BEGIN)
                    val rawEnd = if (c.isNull(COL_END)) rawBegin else c.getLong(COL_END)
                    val begin = if (allDay) CalendarAgenda.allDayUtcToLocal(rawBegin, zone) else rawBegin
                    val end = (if (allDay) CalendarAgenda.allDayUtcToLocal(rawEnd, zone) else rawEnd).coerceAtLeast(begin)
                    if (!CalendarAgenda.overlaps(begin, end, fromMillis, toMillis)) continue
                    result += CalendarEvent(
                        eventId = c.getLong(COL_EVENT_ID),
                        title = c.getString(COL_TITLE)?.trim()?.takeIf { it.isNotEmpty() } ?: NO_TITLE,
                        begin = begin,
                        end = end,
                        allDay = allDay,
                        color = if (c.isNull(COL_COLOR)) 0 else c.getInt(COL_COLOR),
                        calendarName = c.getString(COL_CALENDAR).orEmpty(),
                        location = c.getString(COL_LOCATION)?.trim().orEmpty(),
                    )
                }
            }
        } catch (e: SecurityException) {
            return emptyList()
        } catch (e: Exception) {
            Log.w("PixelHub", "Calendar query failed", e)
            return emptyList()
        }
        return CalendarAgenda.withoutDuplicates(
            result.sortedWith(compareBy<CalendarEvent> { it.begin }.thenByDescending { it.allDay }),
        )
    }

    private companion object {
        const val REQUERY_DEBOUNCE_MS = 300L
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 86_400_000L
        const val NO_TITLE = "(No title)"

        // Visible calendars only, and not what the user won't attend: a cancelled event or a
        // meeting they declined would otherwise headline Home as their "next event".
        val SELECTION = "${Instances.VISIBLE} = 1" +
            " AND (${Instances.STATUS} IS NULL OR ${Instances.STATUS} != ${Events.STATUS_CANCELED})" +
            " AND (${Instances.SELF_ATTENDEE_STATUS} IS NULL OR ${Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED})"

        val PROJECTION = arrayOf(
            Instances.EVENT_ID,
            Instances.TITLE,
            Instances.BEGIN,
            Instances.END,
            Instances.ALL_DAY,
            Instances.DISPLAY_COLOR,
            Instances.CALENDAR_DISPLAY_NAME,
            Instances.EVENT_LOCATION,
            Instances.DESCRIPTION,
        )
        const val COL_EVENT_ID = 0
        const val COL_TITLE = 1
        const val COL_BEGIN = 2
        const val COL_END = 3
        const val COL_ALL_DAY = 4
        const val COL_COLOR = 5
        const val COL_CALENDAR = 6
        const val COL_LOCATION = 7
        const val COL_DESCRIPTION = 8
    }
}

/** Pure date and label logic for the calendar views (unit-tested). */
object CalendarAgenda {
    private const val DASH = "–"
    private const val DOT = "•"

    /** The description footer PixelHub writes on its own reminder events. */
    private val pixelHubFooter: String = CalendarRules.description("")

    /**
     * One entry per real-world event. The same event is often on the phone several times: an
     * invite sent to two synced Google accounts, or "Holidays in India" subscribed in more than
     * one account or region. Same title, start, end and all-day flag counts as the same event;
     * the first one (in [events]' order) is kept.
     */
    fun withoutDuplicates(events: List<CalendarEvent>): List<CalendarEvent> =
        events.distinctBy { e ->
            listOf(e.title.trim().lowercase().replace(Regex("\\s+"), " "), e.begin, e.end, e.allDay)
        }

    /** True for an event PixelHub created from a reminder. */
    fun isFromPixelHub(description: String?): Boolean =
        description != null && description.trimEnd().endsWith(pixelHubFooter)

    /** Whether [begin, end) touches [from, to); an instant event counts at its start. */
    fun overlaps(begin: Long, end: Long, from: Long, to: Long): Boolean =
        begin < to && maxOf(end, begin + 1) > from

    /** An all-day instant (UTC midnight) moved to local midnight of the same calendar date. */
    fun allDayUtcToLocal(utcMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()

    /** The reverse of [allDayUtcToLocal]. */
    fun localToAllDayUtc(localMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(localMillis).atZone(zone).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    fun date(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun startOfDay(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    /** The first local date the event covers. */
    fun firstDate(e: CalendarEvent, zone: ZoneId): LocalDate = date(e.begin, zone)

    /** The last local date the event covers (its end is exclusive). */
    fun lastDate(e: CalendarEvent, zone: ZoneId): LocalDate =
        if (e.end > e.begin) date(e.end - 1, zone) else date(e.begin, zone)

    /**
     * Events by the local dates they cover between [from] and [to] (both inclusive), dates in
     * order. A multi-day event appears on each of its days. Within a day, all-day events come
     * first, then by start time.
     */
    fun groupByDay(
        events: List<CalendarEvent>,
        zone: ZoneId,
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, List<CalendarEvent>> {
        val days = sortedMapOf<LocalDate, MutableList<CalendarEvent>>()
        for (e in events) {
            var d = maxOf(firstDate(e, zone), from)
            val last = minOf(lastDate(e, zone), to)
            while (!d.isAfter(last)) {
                days.getOrPut(d) { mutableListOf() } += e
                d = d.plusDays(1)
            }
        }
        val order = compareByDescending<CalendarEvent> { it.allDay }.thenBy { it.begin }.thenBy { it.title }
        return days.mapValues { (_, list) -> list.sortedWith(order) }
    }

    /**
     * When the event happens on [day]: "9:00 – 10:30 am", "11:00 am – 1:00 pm", "All day",
     * "From 9 pm" (starts here, ends on a later day) or "Until 5 pm" (started on an earlier day).
     */
    fun timeLabel(e: CalendarEvent, day: LocalDate, zone: ZoneId, is24h: Boolean): String {
        if (e.allDay) return "All day"
        val first = firstDate(e, zone)
        val last = lastDate(e, zone)
        val start = Instant.ofEpochMilli(e.begin).atZone(zone).toLocalTime()
        val end = Instant.ofEpochMilli(e.end).atZone(zone).toLocalTime()
        val startsToday = first == day
        val endsToday = last == day
        // Ends at the midnight that closes the day: the day is covered to its end.
        val endsAtDayEnd = e.end == startOfDay(day.plusDays(1), zone)
        return when {
            startsToday && endsToday ->
                if (e.end <= e.begin) clock(start, is24h) else range(start, end, is24h)
            startsToday -> "From ${clock(start, is24h, compact = true)}"
            endsToday && !endsAtDayEnd -> "Until ${clock(end, is24h, compact = true)}"
            else -> "All day"
        }
    }

    /**
     * A short line for the next few events: "2:00 pm • Today", "All day • Tomorrow",
     * "Now • until 3 pm" for one under way.
     */
    fun upcomingLabel(e: CalendarEvent, now: Long, zone: ZoneId, is24h: Boolean, locale: Locale = Locale.getDefault()): String {
        val today = date(now, zone)
        val ongoing = e.begin <= now && now < e.end
        return when {
            e.allDay -> "All day $DOT ${dayLabel(if (ongoing) today else firstDate(e, zone), today, locale)}"
            ongoing -> {
                val last = lastDate(e, zone)
                val until = if (last == today) {
                    clock(Instant.ofEpochMilli(e.end).atZone(zone).toLocalTime(), is24h, compact = true)
                } else {
                    dayLabel(last, today, locale)
                }
                "Now $DOT until $until"
            }
            else -> "${clock(Instant.ofEpochMilli(e.begin).atZone(zone).toLocalTime(), is24h)} $DOT ${dayLabel(firstDate(e, zone), today, locale)}"
        }
    }

    /** "Today", "Tomorrow", "Fri 7 Nov", or "Fri 7 Nov 2027" outside this year. */
    fun dayLabel(date: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(
            DateTimeFormatter.ofPattern(if (date.year == today.year) "EEE d MMM" else "EEE d MMM yyyy", locale),
        )
    }

    /** "9:30 am", "21:30"; [compact] drops ":00" ("5 pm"). */
    fun clock(t: LocalTime, is24h: Boolean, compact: Boolean = false): String {
        val (figure, suffix) = clockParts(t, is24h, compact)
        return if (suffix == null) figure else "$figure $suffix"
    }

    /** The figure and the am/pm suffix apart ("9:30" to "am"), so a big time can set them in two sizes. */
    fun clockParts(t: LocalTime, is24h: Boolean, compact: Boolean = false): Pair<String, String?> {
        if (is24h) return "%02d:%02d".format(Locale.ROOT, t.hour, t.minute) to null
        val h = (t.hour % 12).let { if (it == 0) 12 else it }
        val figure = if (compact && t.minute == 0) "$h" else "%d:%02d".format(Locale.ROOT, h, t.minute)
        return figure to if (t.hour < 12) "am" else "pm"
    }

    /** "9:00 – 10:30 am" when both share am/pm, else "11:00 am – 1:00 pm"; "09:00 – 10:30" in 24h. */
    fun range(start: LocalTime, end: LocalTime, is24h: Boolean): String {
        if (is24h) return "${clock(start, true)} $DASH ${clock(end, true)}"
        val (a, sa) = clockParts(start, false)
        val (b, sb) = clockParts(end, false)
        return if (sa == sb) "$a $DASH $b $sb" else "$a $sa $DASH $b $sb"
    }

    /**
     * The event to feature: the next one still to start, else one under way (an all-day event
     * or one that has begun). Null when nothing is left.
     */
    fun nextEvent(events: List<CalendarEvent>, now: Long): CalendarEvent? =
        events.filter { !it.allDay && it.begin >= now }.minByOrNull { it.begin }
            ?: events.filter { it.end > now }.minByOrNull { it.begin }

    /**
     * Where a new event on [day] should start: the next whole hour today (in the last hour, the
     * current quarter hour, so it stays on [day]), else 9:00 that day.
     */
    fun suggestedStart(day: LocalDate, now: Long, zone: ZoneId): Long {
        val nowAt = Instant.ofEpochMilli(now).atZone(zone)
        return if (day == nowAt.toLocalDate()) {
            val hourStart = nowAt.truncatedTo(ChronoUnit.HOURS)
            val next = hourStart.plusHours(1)
            (if (next.toLocalDate() == day) next else hourStart.withMinute(nowAt.minute / 15 * 15))
                .toInstant().toEpochMilli()
        } else {
            day.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        }
    }
}
