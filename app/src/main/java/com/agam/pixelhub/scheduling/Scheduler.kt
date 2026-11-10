package com.agam.pixelhub.scheduling

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.agam.pixelhub.MainActivity
import com.agam.pixelhub.notify.ReminderNotifier
import com.agam.pixelhub.ui.Tab

/** Every AlarmManager call in the app goes through here. */
class Scheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /**
     * Snooze times, kept on disk: AlarmManager forgets everything on reboot, and a clock or
     * time-zone change reschedules every alarm, so without this a snooze in progress was lost.
     * Keys are "a<id>" (alarm) and "r<id>" (reminder); values are the trigger time.
     */
    private val snoozes = context.getSharedPreferences("snoozes", Context.MODE_PRIVATE)

    /**
     * When each alarm is next due to ring, by alarm id. AlarmManager forgets on reboot, so this is
     * how a reboot tells an alarm that was due while the phone was off from one still ahead.
     */
    private val alarmTimes = context.getSharedPreferences("alarm_times", Context.MODE_PRIVATE)

    fun canScheduleExact(): Boolean = alarmManager.canScheduleExactAlarms()

    fun scheduleReminder(id: Long, atMillis: Long) = setExact(atMillis, trigger(ACTION_REMINDER, id))
    fun scheduleReminderSnooze(id: Long, atMillis: Long) {
        snoozes.edit().putLong("r$id", atMillis).apply()
        setExact(atMillis, trigger(ACTION_REMINDER_SNOOZE, id))
    }

    /**
     * Stops a reminder: its trigger, any snooze, and a notification still showing for it (so an
     * edited reminder can't be completed or snoozed from the notification of its old time).
     */
    fun cancelReminder(id: Long) {
        cancel(ACTION_REMINDER, id)
        cancelReminderSnooze(id)
        ReminderNotifier.cancel(context, id)
    }
    fun cancelReminderSnooze(id: Long) {
        cancel(ACTION_REMINDER_SNOOZE, id)
        snoozes.edit().remove("r$id").apply()
    }

    fun scheduleAlarm(id: Long, atMillis: Long) {
        alarmTimes.edit().putLong(id.toString(), atMillis).apply()
        setAlarmClock(atMillis, trigger(ACTION_ALARM, id))
    }

    /** When alarm [id] was last set to ring, or null if it isn't set. */
    fun alarmDueAt(id: Long): Long? = alarmTimes.getLong(id.toString(), -1L).takeIf { it >= 0 }
    fun scheduleAlarmSnooze(id: Long, atMillis: Long) {
        snoozes.edit().putLong("a$id", atMillis).apply()
        setAlarmClock(atMillis, trigger(ACTION_ALARM_SNOOZE, id))
    }
    fun cancelAlarm(id: Long) {
        cancelAlarmTrigger(id)
        cancelAlarmSnooze(id)
    }
    /** True while a snooze of this alarm is waiting to ring. */
    fun hasAlarmSnooze(id: Long): Boolean = snoozes.contains("a$id")

    /** Only the alarm's own trigger; a snooze in progress keeps going. */
    fun cancelAlarmTrigger(id: Long) {
        cancel(ACTION_ALARM, id)
        alarmTimes.edit().remove(id.toString()).apply()
    }
    fun cancelAlarmSnooze(id: Long) {
        cancel(ACTION_ALARM_SNOOZE, id)
        snoozes.edit().remove("a$id").apply()
        context.getSystemService(NotificationManager::class.java).cancel(snoozedNotificationId(id))
    }

    /** Tapping "Cancel snooze" on the "Snoozed until" notification. */
    fun cancelSnoozeIntent(id: Long): PendingIntent = PendingIntent.getBroadcast(
        context, id.toInt(), intent(ACTION_ALARM_SNOOZE_CANCEL, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** A snooze went off; it no longer needs restoring. */
    fun snoozeFired(action: String, id: Long) {
        val key = when (action) {
            ACTION_ALARM_SNOOZE -> "a$id"
            ACTION_REMINDER_SNOOZE -> "r$id"
            else -> return
        }
        snoozes.edit().remove(key).apply()
    }

    /**
     * After a reboot or clock change: re-arms every snooze. One that came due while the phone was
     * off goes off now, late rather than never (a fired snooze removes its own record, so anything
     * left here never went off).
     */
    fun restoreSnoozes() {
        val now = System.currentTimeMillis()
        val edit = snoozes.edit()
        snoozes.all.forEach { (key, value) ->
            val at = (value as? Long)?.coerceAtLeast(now)
            val id = key.drop(1).toLongOrNull()
            when {
                at == null || id == null -> edit.remove(key)
                key.first() == 'a' -> setAlarmClock(at, trigger(ACTION_ALARM_SNOOZE, id))
                key.first() == 'r' -> setExact(at, trigger(ACTION_REMINDER_SNOOZE, id))
                else -> edit.remove(key)
            }
        }
        edit.apply()
    }

    /** Health nudges are quiet checks, so no status-bar alarm icon: plain exact alarm. */
    fun scheduleNudge(id: Long, atMillis: Long) = setExact(atMillis, trigger(ACTION_HEALTH_NUDGE, id))
    fun cancelNudge(id: Long) = cancel(ACTION_HEALTH_NUDGE, id)

    /** Last month's report on the 1st: a quiet note, so a plain exact alarm. One at a time. */
    fun scheduleMonthlyReport(atMillis: Long) = setExact(atMillis, trigger(ACTION_MONTHLY_REPORT, 0))

    private fun setExact(atMillis: Long, operation: PendingIntent) {
        if (canScheduleExact()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
        }
    }

    /** setAlarmClock is exempt from Doze and shows the alarm icon in the status bar. */
    private fun setAlarmClock(atMillis: Long, operation: PendingIntent) {
        if (!canScheduleExact()) return setExact(atMillis, operation)
        val show = PendingIntent.getActivity(
            context, SHOW_REQUEST,
            // Its own action: PendingIntents match on action and component, not extras, so without
            // one this shared request code 1 with reminder #1's notification and one overwrote the
            // other's tab (tapping that reminder opened Alarms).
            Intent(context, MainActivity::class.java).setAction(ACTION_OPEN_ALARMS)
                .putExtra(MainActivity.EXTRA_TAB, Tab.ALARMS.name),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(atMillis, show), operation)
    }

    private fun cancel(action: String, id: Long) {
        PendingIntent.getBroadcast(
            context, id.toInt(), intent(action, id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun trigger(action: String, id: Long): PendingIntent = PendingIntent.getBroadcast(
        context, id.toInt(), intent(action, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun intent(action: String, id: Long) =
        Intent(context, TriggerReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id)

    companion object {
        const val ACTION_REMINDER = "com.agam.pixelhub.action.REMINDER"
        const val ACTION_REMINDER_SNOOZE = "com.agam.pixelhub.action.REMINDER_SNOOZE"
        const val ACTION_ALARM = "com.agam.pixelhub.action.ALARM"
        const val ACTION_ALARM_SNOOZE = "com.agam.pixelhub.action.ALARM_SNOOZE"
        const val ACTION_ALARM_SNOOZE_CANCEL = "com.agam.pixelhub.action.ALARM_SNOOZE_CANCEL"
        const val ACTION_HEALTH_NUDGE = "com.agam.pixelhub.action.HEALTH_NUDGE"
        const val ACTION_MONTHLY_REPORT = "com.agam.pixelhub.action.MONTHLY_REPORT"
        const val EXTRA_ID = "id"
        /** Opens the Alarms tab (status-bar alarm icon, "Missed alarm" note). */
        const val ACTION_OPEN_ALARMS = "com.agam.pixelhub.open.ALARMS"
        private const val SHOW_REQUEST = 1

        /** Notification id of the "Snoozed until" note for one alarm. */
        fun snoozedNotificationId(alarmId: Long): Int = 50_000 + alarmId.toInt()
    }
}
