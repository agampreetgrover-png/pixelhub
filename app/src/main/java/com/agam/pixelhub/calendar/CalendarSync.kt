package com.agam.pixelhub.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.util.Log
import androidx.core.content.ContextCompat
import com.agam.pixelhub.data.Reminder
import com.agam.pixelhub.data.SettingsStore
import com.agam.pixelhub.scheduling.Days
import com.agam.pixelhub.scheduling.Recurrence
import com.agam.pixelhub.scheduling.RecurrenceCalc
import com.agam.pixelhub.scheduling.RepeatType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

/** A calendar on the phone that PixelHub may write to. */
data class CalendarInfo(
    val id: Long,
    val name: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    val isPrimary: Boolean,
) {
    val isGoogle: Boolean get() = accountType == GOOGLE_ACCOUNT_TYPE

    companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}

/**
 * One-way copy of reminders into a phone calendar through CalendarContract. When that
 * calendar belongs to a Google account, Android's Calendar sync pushes the events to
 * Google Calendar. No sign-in or API key needed, only the Calendar permission.
 */
class CalendarSync(private val context: Context, private val settings: SettingsStore) {
    private val resolver get() = context.contentResolver

    val permissions = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    /** The chosen calendar's id, or null when sync is off. */
    val selectedCalendarId: Flow<Long?> = settings.calendarId

    /** Use ReminderRepository.setCalendar instead, which also copies existing reminders. */
    internal suspend fun select(calendarId: Long?) = settings.setCalendarId(calendarId)

    fun hasPermission(): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Writable, visible calendars; Google accounts and primary calendars first. */
    suspend fun listCalendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val projection = arrayOf(
            Calendars._ID, Calendars.CALENDAR_DISPLAY_NAME, Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE, Calendars.CALENDAR_COLOR, Calendars.IS_PRIMARY,
        )
        val selection = "${Calendars.VISIBLE} = 1 AND ${Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val args = arrayOf(Calendars.CAL_ACCESS_CONTRIBUTOR.toString())
        val result = mutableListOf<CalendarInfo>()
        // Access can be revoked between the check above and the query; Settings collects this
        // from a composition, where a throw would close the app.
        try {
            resolver.query(Calendars.CONTENT_URI, projection, selection, args, null)?.use { c ->
                while (c.moveToNext()) {
                    result += CalendarInfo(
                        id = c.getLong(0),
                        name = c.getString(1).orEmpty(),
                        accountName = c.getString(2).orEmpty(),
                        accountType = c.getString(3).orEmpty(),
                        color = c.getInt(4),
                        isPrimary = !c.isNull(5) && c.getInt(5) == 1,
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("PixelHub", "Listing calendars failed", e)
            return@withContext emptyList()
        }
        result.sortedWith(compareByDescending<CalendarInfo> { it.isGoogle }.thenByDescending { it.isPrimary }.thenBy { it.name })
    }

    /**
     * Creates or updates the event for [r] and returns its event id. Returns
     * r.calendarEventId unchanged when sync is off or not permitted, so a calendar
     * problem never blocks saving a reminder.
     */
    suspend fun push(r: Reminder): Long? {
        val calendarId = settings.calendarId.first() ?: return r.calendarEventId
        if (!hasPermission()) return r.calendarEventId
        return withContext(Dispatchers.IO) {
            try {
                upsert(calendarId, r)
            } catch (e: Exception) {
                Log.w("PixelHub", "Calendar push failed for reminder ${r.id}", e)
                r.calendarEventId
            }
        }
    }

    /**
     * Deletes an event PixelHub created (Calendar sync removes it from Google too). True when the
     * event is gone (or was never ours); false when it may still be there (no calendar access, or
     * the provider failed), so the caller keeps the id and tries again later.
     */
    suspend fun remove(eventId: Long): Boolean {
        if (!hasPermission()) return false
        return withContext(Dispatchers.IO) {
            try {
                if (ourCalendarId(eventId) != null) resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), null, null)
                true
            } catch (e: Exception) {
                Log.w("PixelHub", "Calendar delete failed for event $eventId", e)
                false
            }
        }
    }

    /** For a reminder that is being deleted: removes its event now, or keeps the id to retry once access is back. */
    suspend fun removeOrQueue(eventId: Long) {
        if (!remove(eventId)) settings.addPendingCalendarDelete(eventId)
    }

    /** Retries the deletions [removeOrQueue] couldn't do (calendar access was off at the time). */
    suspend fun retryPendingRemovals() {
        if (!hasPermission()) return
        val removed = settings.pendingCalendarDeletes().filter { remove(it) }
        if (removed.isNotEmpty()) settings.removePendingCalendarDeletes(removed.toSet())
    }

    /**
     * An event id saved in the database can point at someone else's event after a restore onto
     * a new phone, or after Calendar Storage was cleared: ids are per-device. Only events that
     * still exist and carry PixelHub's footer are ever changed or deleted. Returns the calendar
     * the event lives in, or null when it isn't ours (or is gone).
     */
    private fun ourCalendarId(eventId: Long): Long? =
        resolver.query(
            ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
            arrayOf(Events.DESCRIPTION, Events.CALENDAR_ID), "${Events.DELETED} = 0", null, null,
        )?.use { c -> if (c.moveToFirst() && CalendarAgenda.isFromPixelHub(c.getString(0))) c.getLong(1) else null }

    private fun upsert(calendarId: Long, r: Reminder): Long {
        val values = CalendarRules.eventValues(r, ZoneId.systemDefault())
        r.calendarEventId?.let { eventId ->
            // Not ours (or gone): leave it alone and create a fresh event below.
            val current = ourCalendarId(eventId) ?: return@let
            val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId)
            // No selection: the provider rejects one on a single-event URI (IllegalArgumentException).
            if (current == calendarId) {
                if (resolver.update(uri, values, null, null) > 0) return eventId
            } else {
                // The user switched calendars: remove the old copy and insert into the new one.
                resolver.delete(uri, null, null)
            }
        }
        values.put(Events.CALENDAR_ID, calendarId)
        val inserted = requireNotNull(resolver.insert(Events.CONTENT_URI, values)) { "Calendar insert returned null" }
        return ContentUris.parseId(inserted)
    }
}

/** Pure mapping from a reminder to calendar event fields (unit-tested). */
object CalendarRules {
    const val DURATION_MINUTES = 15L
    private const val FOOTER = "Created by PixelHub"

    /** RFC 5545 RRULE for a repeating reminder, or null for a one-off. */
    fun rrule(rec: Recurrence): String? = when (rec.type) {
        RepeatType.NONE -> null
        RepeatType.DAILY -> "FREQ=DAILY"
        RepeatType.WEEKLY -> {
            val days = DayOfWeek.entries.filter { Days.contains(rec.daysMask, it) }.joinToString(",") { it.name.take(2) }
            if (days.isEmpty()) "FREQ=WEEKLY" else "FREQ=WEEKLY;BYDAY=$days"
        }
        RepeatType.EVERY_N_DAYS -> if (rec.interval <= 1) "FREQ=DAILY" else "FREQ=DAILY;INTERVAL=${rec.interval}"
        RepeatType.EVERY_N_WEEKS -> if (rec.interval <= 1) "FREQ=WEEKLY" else "FREQ=WEEKLY;INTERVAL=${rec.interval}"
    }

    /**
     * The first time the reminder actually happens. For "Mon/Wed" starting on a Sunday that's
     * Monday, so the calendar doesn't show a stray Sunday event.
     */
    fun firstOccurrence(anchor: LocalDateTime, rec: Recurrence, zone: ZoneId): Long {
        val anchorAt = anchor.atZone(zone)
        val first = if (rec.isRepeating) RecurrenceCalc.nextTrigger(rec, anchor, anchorAt.minusSeconds(1)) else null
        return (first ?: anchorAt).toInstant().toEpochMilli()
    }

    fun description(notes: String): String = if (notes.isBlank()) FOOTER else "$notes\n\n$FOOTER"

    fun eventValues(r: Reminder, zone: ZoneId): ContentValues {
        val start = firstOccurrence(r.anchor, r.recurrence, zone)
        val rule = rrule(r.recurrence)
        return ContentValues().apply {
            put(Events.TITLE, r.title)
            put(Events.DESCRIPTION, description(r.notes))
            put(Events.DTSTART, start)
            put(Events.EVENT_TIMEZONE, zone.id)
            put(Events.AVAILABILITY, Events.AVAILABILITY_FREE)
            put(Events.HAS_ALARM, 0) // PixelHub notifies; avoid double alerts
            if (rule == null) {
                put(Events.DTEND, start + DURATION_MINUTES * 60_000)
                putNull(Events.DURATION)
                putNull(Events.RRULE)
            } else {
                // The provider requires DURATION (not DTEND) for repeating events.
                putNull(Events.DTEND)
                put(Events.DURATION, "PT${DURATION_MINUTES}M")
                put(Events.RRULE, rule)
            }
        }
    }
}
