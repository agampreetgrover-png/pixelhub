package com.agam.pixelhub.data

import com.agam.pixelhub.calendar.CalendarSync
import com.agam.pixelhub.scheduling.Days
import com.agam.pixelhub.scheduling.Recurrence
import com.agam.pixelhub.scheduling.RecurrenceCalc
import com.agam.pixelhub.scheduling.Scheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.ZonedDateTime

/** How late past its time a reminder or alarm must be, at a reschedule, to count as missed rather than going off right now. */
private const val MISSED_GRACE_MS = 60_000L

private fun ZonedDateTime.millis() = toInstant().toEpochMilli()

class ReminderRepository(
    private val dao: ReminderDao,
    private val scheduler: Scheduler,
    private val calendar: CalendarSync,
) {
    val all: Flow<List<Reminder>> = dao.observeAll()

    suspend fun get(id: Long): Reminder? = dao.get(id)

    /**
     * Inserts (id = 0) or updates a reminder, (re)schedules it and copies it to the calendar.
     * [done] null keeps the reminder's current state, so fixing a typo in a completed reminder
     * doesn't reopen it; pass false to reopen.
     */
    suspend fun save(
        id: Long,
        title: String,
        notes: String,
        anchor: LocalDateTime,
        recurrence: Recurrence,
        done: Boolean? = null,
        previous: Reminder? = null,
    ): Reminder {
        val now = ZonedDateTime.now()
        val existing = if (id == 0L) null else dao.get(id)
        // The row as it was (on an undo, the deleted one): a one-off that already went off stays
        // fired, or a DST repeated hour or a move west would show it a second time.
        val fired = (existing ?: previous)?.takeIf { !it.recurrence.isRepeating && it.notifiedAt > 0L }
        val next = RecurrenceCalc.nextTriggerOnSave(recurrence, anchor, now, fired?.anchor)
        val trigger = RecurrenceCalc.storedTriggerOnSave(next, anchor, now, fired?.anchor, fired?.triggerAt)
        val isDone = done ?: existing?.done ?: false
        val draft = Reminder(
            id = id, title = title.trim(), notes = notes.trim(),
            anchor = anchor, triggerAt = trigger, recurrence = recurrence,
            calendarEventId = existing?.calendarEventId,
            done = isDone,
            // Upcoming: not fired yet. Already past (an undo, an un-tick): count it as shown, or a
            // reboot would pop it up as "missed while the phone was off".
            notifiedAt = if (next != null) 0L else existing?.notifiedAt?.takeIf { it > 0L } ?: now.millis(),
        )
        val savedId = if (id == 0L) dao.insert(draft) else id.also { dao.editKeepingEvent(draft) }
        // Only the text changed: keep a snooze in progress and the notification already showing
        // (the main trigger is replaced in place below). A new time, repeat or state starts over.
        val sameTiming = existing != null && existing.anchor == anchor &&
            existing.recurrence == recurrence && existing.done == isDone
        if (!sameTiming) scheduler.cancelReminder(savedId)
        if (next != null && !isDone) scheduler.scheduleReminder(savedId, trigger)
        return syncToCalendar(draft.copy(id = savedId))
    }

    /**
     * Chooses the calendar reminders are copied to (null = stop syncing; existing events stay).
     * Choosing a calendar copies every open reminder, moving any that live in a previous calendar.
     */
    suspend fun setCalendar(calendarId: Long?) {
        calendarLock.withLock {
            calendar.select(calendarId)
            if (calendarId != null) dao.active().forEach { syncLocked(it) }
            cleanupLocked()
        }
    }

    /**
     * After a time-zone change (and rescheduleAll): moves the calendar copies to the reminders'
     * wall-clock times in the new zone. A one-off that already went off keeps its event at the
     * time it actually fired, as rescheduleAll keeps it fired.
     */
    suspend fun resyncCalendar() {
        calendarLock.withLock {
            dao.active().filter { it.recurrence.isRepeating || it.notifiedAt == 0L }.forEach { syncLocked(it) }
        }
    }

    /**
     * Removes calendar events that couldn't be removed while calendar access was off: those of
     * deleted reminders, and of finished repeating ones (which would otherwise repeat forever).
     */
    suspend fun retryCalendarCleanup() {
        calendarLock.withLock { cleanupLocked() }
    }

    private suspend fun cleanupLocked() {
        if (!calendar.hasPermission()) return
        calendar.retryPendingRemovals()
        dao.doneWithEvent().filter { it.recurrence.isRepeating }.forEach { syncLocked(it) }
    }

    /**
     * One calendar write at a time. Two overlapping syncs (a double tap on a calendar, or a save
     * during "copy every reminder") would both see no event yet and create two, and the loser's
     * event would be left behind in Google Calendar.
     */
    private val calendarLock = Mutex()

    private suspend fun syncToCalendar(r: Reminder): Reminder = calendarLock.withLock { syncLocked(r) }

    private suspend fun syncLocked(stale: Reminder): Reminder {
        // Fresh row: sees an event id another sync just stored, and doesn't write back old text.
        val r = dao.get(stale.id) ?: return stale
        // A finished repeating reminder would otherwise keep repeating in Google Calendar forever;
        // un-ticking it saves again and copies it back as a fresh event.
        if (r.done && r.recurrence.isRepeating) {
            val eventId = r.calendarEventId ?: return r
            // Not removed (no calendar access): keep the id so retryCalendarCleanup can try again.
            if (!calendar.remove(eventId)) return r
            dao.setCalendarEventId(r.id, null)
            return r.copy(calendarEventId = null)
        }
        val eventId = calendar.push(r)
        if (eventId == r.calendarEventId) return r
        dao.setCalendarEventId(r.id, eventId)
        return r.copy(calendarEventId = eventId)
    }

    suspend fun setDone(reminder: Reminder, done: Boolean) {
        if (done) {
            scheduler.cancelReminder(reminder.id)
            // The saved row, not the screen's copy: that may predate the calendar sync storing its
            // event id, and writing it back would make the next sync add a second event.
            val finished = calendarLock.withLock {
                dao.markDone(reminder.id)
                dao.get(reminder.id) ?: return
            }
            syncToCalendar(finished)
        } else {
            save(reminder.id, reminder.title, reminder.notes, reminder.anchor, reminder.recurrence, done = false)
        }
    }

    /** Puts back a reminder that was just deleted (Undo). It gets a new id and calendar event. */
    suspend fun restore(r: Reminder): Reminder {
        return save(0, r.title, r.notes, r.anchor, r.recurrence, done = r.done, previous = r)
    }

    suspend fun delete(reminder: Reminder) {
        scheduler.cancelReminder(reminder.id)
        // Under the sync's lock and from the saved row: a sync still adding this reminder's event
        // finishes first, so its event id is known here and the event doesn't outlive the reminder.
        calendarLock.withLock {
            val eventId = dao.get(reminder.id)?.calendarEventId ?: reminder.calendarEventId
            dao.delete(reminder)
            eventId?.let { calendar.removeOrQueue(it) }
        }
    }

    /** Main trigger fired: record it, schedule the next occurrence, return what to show. */
    suspend fun onFired(id: Long): Reminder? {
        val r = dao.get(id) ?: return null
        if (r.done) return null
        val now = ZonedDateTime.now()
        // A one-off notifies once, whichever of this and rescheduleAll's "missed" gets there first.
        if (!r.recurrence.isRepeating) return r.takeIf { dao.claimNotify(id, now.millis()) == 1 }
        // A stale trigger (a reschedule re-armed an occurrence already handled here): this replaced
        // the real next trigger, so put that back and show nothing.
        if (r.triggerAt > now.millis() + 30_000) {
            scheduler.scheduleReminder(id, r.triggerAt)
            return null
        }
        var triggerAt = r.triggerAt
        // Look slightly ahead so an early delivery can't re-schedule the same occurrence.
        RecurrenceCalc.nextTrigger(r.recurrence, r.anchor, now.plusSeconds(30))?.let { next ->
            triggerAt = next.millis()
            scheduler.scheduleReminder(id, triggerAt)
        }
        dao.setTiming(id, triggerAt, now.millis())
        return r
    }

    /** Notification "Done": one-off reminders are completed; repeating ones wait for the next occurrence. */
    suspend fun completeFromNotification(id: Long) {
        val r = dao.get(id) ?: return
        scheduler.cancelReminderSnooze(id)
        if (!r.recurrence.isRepeating) dao.markDone(id)
    }

    fun snooze(id: Long, minutes: Int) {
        scheduler.scheduleReminderSnooze(id, System.currentTimeMillis() + minutes * 60_000L)
    }

    /**
     * After boot or a clock/time-zone change. Returns reminders whose time passed while the phone was off.
     * [lostTriggers] false (a clock change): AlarmManager still holds every trigger and delivers
     * overdue ones itself, so those aren't shown here as missed.
     */
    suspend fun rescheduleAll(lostTriggers: Boolean = true): List<Reminder> {
        val now = ZonedDateTime.now()
        val missed = mutableListOf<Reminder>()
        for (r in dao.active()) {
            // onFired always moves a repeating reminder on to its next time, so one still in the
            // past never went off. Due within the last minute (or with its trigger still held):
            // fire it now rather than skip to the next occurrence. A past time fires at once.
            if (r.recurrence.isRepeating && r.triggerAt in 1..now.millis() &&
                (!lostTriggers || r.triggerAt >= now.millis() - MISSED_GRACE_MS)
            ) {
                scheduler.scheduleReminder(r.id, r.triggerAt)
                continue
            }
            val next = RecurrenceCalc.nextTrigger(r.recurrence, r.anchor, now)
            // A one-off that already went off stays fired, even if a time-zone change or the
            // clock going back puts its wall-clock time in the future again.
            if (next != null && (r.recurrence.isRepeating || r.notifiedAt == 0L)) {
                val t = next.millis()
                // Still in the past by more than the grace: it never went off (phone off), so
                // show it late instead of skipping to tomorrow.
                val late = r.recurrence.isRepeating && r.triggerAt in 1 until now.millis() - MISSED_GRACE_MS
                val notifiedAt = if (late) now.millis() else r.notifiedAt
                if (t != r.triggerAt || notifiedAt != r.notifiedAt) dao.setTiming(r.id, t, notifiedAt)
                scheduler.scheduleReminder(r.id, t)
                if (late) missed += r.copy(triggerAt = t, notifiedAt = notifiedAt)
            } else if (!r.recurrence.isRepeating && r.notifiedAt == 0L) {
                // Claimed, so its still-armed trigger (onFired) can't show it a second time.
                if (dao.claimNotify(r.id, now.millis()) == 1) missed += r.copy(notifiedAt = now.millis())
            }
        }
        return missed
    }

    /** Writes everything but the calendar event id, which only the calendar sync owns. */
    private suspend fun ReminderDao.editKeepingEvent(r: Reminder) = edit(
        r.id, r.title, r.notes, r.anchor, r.triggerAt,
        r.recurrence.type.name, r.recurrence.daysMask, r.recurrence.interval, r.done, r.notifiedAt,
    )
}

class AlarmRepository(private val dao: AlarmDao, private val scheduler: Scheduler) {
    val all: Flow<List<Alarm>> = dao.observeAll()

    suspend fun get(id: Long): Alarm? = dao.get(id)

    suspend fun save(alarm: Alarm): Alarm {
        val before = if (alarm.id == 0L) null else dao.get(alarm.id)
        val id = if (alarm.id == 0L) dao.insert(alarm) else alarm.id.also { dao.update(alarm) }
        val saved = alarm.copy(id = id)
        // A rename or a plain re-save keeps a snooze in progress; a new time, new days or
        // switching the alarm off ends it.
        val keepSnooze = saved.enabled && before != null && before.hour == saved.hour &&
            before.minute == saved.minute && before.daysMask == saved.daysMask
        apply(saved, keepSnooze)
        return saved
    }

    suspend fun delete(alarm: Alarm) {
        scheduler.cancelAlarm(alarm.id)
        dao.delete(alarm)
    }

    /** Alarm went off: one-shot alarms switch off, repeating ones schedule their next day. */
    suspend fun onFired(id: Long): Alarm? {
        val a = dao.get(id) ?: return null
        if (!a.enabled) return null
        if (a.daysMask == 0) {
            dao.update(a.copy(enabled = false))
        } else {
            scheduler.scheduleAlarm(id, nextTrigger(a, ZonedDateTime.now().plusMinutes(1)).millis())
        }
        return a
    }

    /**
     * After boot or a clock change. Keeps a snooze in progress (the scheduler restores it).
     * Returns alarms that were due while the phone was off: a one-time one is switched off
     * rather than ringing a day late; a repeating one just moves on to its next day.
     * [lostTriggers] false (a clock change): AlarmManager still holds every trigger and rings
     * overdue ones itself, so those are left alone.
     */
    suspend fun rescheduleAll(lostTriggers: Boolean = true): List<Alarm> {
        val now = System.currentTimeMillis()
        val missed = mutableListOf<Alarm>()
        dao.enabled().forEach { a ->
            val due = scheduler.alarmDueAt(a.id)
            if (due != null && due <= now) {
                // Being delivered now; onFired switches a one-time alarm off or moves a repeating one on.
                if (!lostTriggers) return@forEach
                if (due >= now - MISSED_GRACE_MS) {
                    // Due within the last minute and not handled yet (onFired would have moved
                    // alarm_times on): ring now rather than skip to its next day.
                    scheduler.scheduleAlarm(a.id, due)
                    return@forEach
                }
                missed += a
                if (a.daysMask == 0) {
                    dao.update(a.copy(enabled = false))
                    scheduler.cancelAlarmTrigger(a.id)
                    return@forEach
                }
            }
            apply(a, keepSnooze = true)
        }
        return missed
    }

    private fun apply(a: Alarm, keepSnooze: Boolean = false) {
        if (keepSnooze) scheduler.cancelAlarmTrigger(a.id) else scheduler.cancelAlarm(a.id)
        if (a.enabled) scheduler.scheduleAlarm(a.id, nextTrigger(a).millis())
    }

    companion object {
        fun nextTrigger(a: Alarm, after: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime =
            RecurrenceCalc.nextAlarm(a.hour, a.minute, a.daysMask, after, once = (a.daysMask and Days.ALL) == 0)
    }
}

class HealthNudgeRepository(private val dao: HealthNudgeDao, private val scheduler: Scheduler) {
    val all: Flow<List<HealthNudge>> = dao.observeAll()

    suspend fun get(id: Long): HealthNudge? = dao.get(id)

    suspend fun save(nudge: HealthNudge): HealthNudge {
        val id = if (nudge.id == 0L) dao.insert(nudge) else nudge.id.also { dao.update(nudge) }
        val saved = nudge.copy(id = id)
        apply(saved)
        return saved
    }

    suspend fun delete(nudge: HealthNudge) {
        scheduler.cancelNudge(nudge.id)
        dao.delete(nudge)
    }

    /** Check time reached: schedule the next check and return the nudge to evaluate. */
    suspend fun onFired(id: Long): HealthNudge? {
        val n = dao.get(id)?.takeIf { it.enabled } ?: return null
        scheduler.scheduleNudge(id, nextTrigger(n, ZonedDateTime.now().plusMinutes(1)).millis())
        return n
    }

    suspend fun rescheduleAll() {
        dao.enabled().forEach { apply(it) }
    }

    private fun apply(n: HealthNudge) {
        scheduler.cancelNudge(n.id)
        if (n.enabled) scheduler.scheduleNudge(n.id, nextTrigger(n).millis())
    }

    companion object {
        /** Nudges always repeat; daysMask 0 means every day. */
        fun nextTrigger(n: HealthNudge, after: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime =
            RecurrenceCalc.nextAlarm(n.hour, n.minute, n.daysMask, after)
    }
}

class ShortcutRepository(private val dao: ShortcutDao) {
    val all: Flow<List<AppShortcut>> = dao.observeAll()

    suspend fun list(): List<AppShortcut> = dao.all()

    /** Adds a shortcut; an app that is already pinned isn't added twice (a quick double tap). */
    suspend fun add(shortcut: AppShortcut): Long {
        if (shortcut.kind == ShortcutKind.APP) {
            dao.all().firstOrNull { it.kind == ShortcutKind.APP && it.packageName == shortcut.packageName }
                ?.let { return it.id }
        }
        return dao.insert(shortcut.copy(id = 0, position = (dao.maxPosition() ?: -1) + 1))
    }

    suspend fun delete(shortcut: AppShortcut) = dao.delete(shortcut)
}
