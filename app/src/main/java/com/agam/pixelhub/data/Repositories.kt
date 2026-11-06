package com.agam.pixelhub.data

import com.agam.pixelhub.calendar.CalendarSync
import com.agam.pixelhub.scheduling.Recurrence
import com.agam.pixelhub.scheduling.RecurrenceCalc
import com.agam.pixelhub.scheduling.Scheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime
import java.time.ZonedDateTime

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
    ): Reminder {
        val now = ZonedDateTime.now()
        val next = RecurrenceCalc.nextTrigger(recurrence, anchor, now)
        val trigger = (next ?: anchor.atZone(now.zone)).millis()
        val existing = if (id == 0L) null else dao.get(id)
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
        val savedId = if (id == 0L) dao.insert(draft) else id.also { dao.update(draft) }
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
        }
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
            calendar.remove(eventId)
            return r.copy(calendarEventId = null).also { dao.update(it) }
        }
        val eventId = calendar.push(r)
        if (eventId == r.calendarEventId) return r
        return r.copy(calendarEventId = eventId).also { dao.update(it) }
    }

    suspend fun setDone(reminder: Reminder, done: Boolean) {
        if (done) {
            scheduler.cancelReminder(reminder.id)
            val finished = reminder.copy(done = true)
            dao.update(finished)
            syncToCalendar(finished)
        } else {
            save(reminder.id, reminder.title, reminder.notes, reminder.anchor, reminder.recurrence, done = false)
        }
    }

    /** Puts back a reminder that was just deleted (Undo). It gets a new id and calendar event. */
    suspend fun restore(r: Reminder): Reminder {
        return save(0, r.title, r.notes, r.anchor, r.recurrence, done = r.done)
    }

    suspend fun delete(reminder: Reminder) {
        scheduler.cancelReminder(reminder.id)
        dao.delete(reminder)
        reminder.calendarEventId?.let { calendar.remove(it) }
    }

    /** Main trigger fired: record it, schedule the next occurrence, return what to show. */
    suspend fun onFired(id: Long): Reminder? {
        val r = dao.get(id) ?: return null
        if (r.done) return null
        val now = ZonedDateTime.now()
        var updated = r.copy(notifiedAt = now.millis())
        if (r.recurrence.isRepeating) {
            // Look slightly ahead so an early delivery can't re-schedule the same occurrence.
            RecurrenceCalc.nextTrigger(r.recurrence, r.anchor, now.plusSeconds(30))?.let { next ->
                updated = updated.copy(triggerAt = next.millis())
                scheduler.scheduleReminder(id, updated.triggerAt)
            }
        }
        dao.update(updated)
        return r
    }

    /** Notification "Done": one-off reminders are completed; repeating ones wait for the next occurrence. */
    suspend fun completeFromNotification(id: Long) {
        val r = dao.get(id) ?: return
        scheduler.cancelReminderSnooze(id)
        if (!r.recurrence.isRepeating) dao.update(r.copy(done = true))
    }

    fun snooze(id: Long, minutes: Int) {
        scheduler.scheduleReminderSnooze(id, System.currentTimeMillis() + minutes * 60_000L)
    }

    /** After boot or a clock/time-zone change. Returns one-off reminders missed while the phone was off. */
    suspend fun rescheduleAll(): List<Reminder> {
        val now = ZonedDateTime.now()
        val missed = mutableListOf<Reminder>()
        for (r in dao.active()) {
            val next = RecurrenceCalc.nextTrigger(r.recurrence, r.anchor, now)
            // A one-off that already went off stays fired, even if a time-zone change or the
            // clock going back puts its wall-clock time in the future again.
            if (next != null && (r.recurrence.isRepeating || r.notifiedAt == 0L)) {
                val t = next.millis()
                if (t != r.triggerAt) dao.update(r.copy(triggerAt = t))
                scheduler.scheduleReminder(r.id, t)
            } else if (!r.recurrence.isRepeating && r.notifiedAt == 0L) {
                val u = r.copy(notifiedAt = now.millis())
                dao.update(u)
                missed += u
            }
        }
        return missed
    }
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

    /** After boot or a clock change. Keeps a snooze in progress (the scheduler restores it). */
    suspend fun rescheduleAll() {
        dao.enabled().forEach { apply(it, keepSnooze = true) }
    }

    private fun apply(a: Alarm, keepSnooze: Boolean = false) {
        if (keepSnooze) scheduler.cancelAlarmTrigger(a.id) else scheduler.cancelAlarm(a.id)
        if (a.enabled) scheduler.scheduleAlarm(a.id, nextTrigger(a).millis())
    }

    companion object {
        fun nextTrigger(a: Alarm, after: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime =
            RecurrenceCalc.nextAlarm(a.hour, a.minute, a.daysMask, after)
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
