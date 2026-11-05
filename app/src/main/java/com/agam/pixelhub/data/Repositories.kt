package com.agam.pixelhub.data

import com.agam.pixelhub.calendar.CalendarSync
import com.agam.pixelhub.scheduling.Recurrence
import com.agam.pixelhub.scheduling.RecurrenceCalc
import com.agam.pixelhub.scheduling.Scheduler
import kotlinx.coroutines.flow.Flow
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

    /** Inserts (id = 0) or updates a reminder, (re)schedules it and copies it to the calendar. */
    suspend fun save(id: Long, title: String, notes: String, anchor: LocalDateTime, recurrence: Recurrence): Reminder {
        val now = ZonedDateTime.now()
        val next = RecurrenceCalc.nextTrigger(recurrence, anchor, now)
        val trigger = (next ?: anchor.atZone(now.zone)).millis()
        val existing = if (id == 0L) null else dao.get(id)
        val draft = Reminder(
            id = id, title = title.trim(), notes = notes.trim(),
            anchor = anchor, triggerAt = trigger, recurrence = recurrence,
            calendarEventId = existing?.calendarEventId,
        )
        val savedId = if (id == 0L) dao.insert(draft) else id.also { dao.update(draft) }
        scheduler.cancelReminder(savedId)
        if (next != null) scheduler.scheduleReminder(savedId, trigger)
        return syncToCalendar(draft.copy(id = savedId))
    }

    /**
     * Chooses the calendar reminders are copied to (null = stop syncing; existing events stay).
     * Choosing a calendar copies every open reminder, moving any that live in a previous calendar.
     */
    suspend fun setCalendar(calendarId: Long?) {
        calendar.select(calendarId)
        if (calendarId != null) dao.active().forEach { syncToCalendar(it) }
    }

    private suspend fun syncToCalendar(r: Reminder): Reminder {
        val eventId = calendar.push(r)
        if (eventId == r.calendarEventId) return r
        return r.copy(calendarEventId = eventId).also { dao.update(it) }
    }

    suspend fun setDone(reminder: Reminder, done: Boolean) {
        if (done) {
            scheduler.cancelReminder(reminder.id)
            dao.update(reminder.copy(done = true))
        } else {
            save(reminder.id, reminder.title, reminder.notes, reminder.anchor, reminder.recurrence)
        }
    }

    /** Puts back a reminder that was just deleted (Undo). It gets a new id and calendar event. */
    suspend fun restore(r: Reminder): Reminder {
        val saved = save(0, r.title, r.notes, r.anchor, r.recurrence)
        if (r.done) setDone(saved, true)
        return saved
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
            if (next != null) {
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
        val id = if (alarm.id == 0L) dao.insert(alarm) else alarm.id.also { dao.update(alarm) }
        val saved = alarm.copy(id = id)
        apply(saved)
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

    suspend fun rescheduleAll() {
        dao.enabled().forEach { apply(it) }
    }

    private fun apply(a: Alarm) {
        scheduler.cancelAlarm(a.id)
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
