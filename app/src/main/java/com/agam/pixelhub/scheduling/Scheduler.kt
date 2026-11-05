package com.agam.pixelhub.scheduling

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.agam.pixelhub.MainActivity
import com.agam.pixelhub.ui.Tab

/** Every AlarmManager call in the app goes through here. */
class Scheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun canScheduleExact(): Boolean = alarmManager.canScheduleExactAlarms()

    fun scheduleReminder(id: Long, atMillis: Long) = setExact(atMillis, trigger(ACTION_REMINDER, id))
    fun scheduleReminderSnooze(id: Long, atMillis: Long) = setExact(atMillis, trigger(ACTION_REMINDER_SNOOZE, id))
    fun cancelReminder(id: Long) {
        cancel(ACTION_REMINDER, id)
        cancel(ACTION_REMINDER_SNOOZE, id)
    }
    fun cancelReminderSnooze(id: Long) = cancel(ACTION_REMINDER_SNOOZE, id)

    fun scheduleAlarm(id: Long, atMillis: Long) = setAlarmClock(atMillis, trigger(ACTION_ALARM, id))
    fun scheduleAlarmSnooze(id: Long, atMillis: Long) = setAlarmClock(atMillis, trigger(ACTION_ALARM_SNOOZE, id))
    fun cancelAlarm(id: Long) {
        cancel(ACTION_ALARM, id)
        cancelAlarmSnooze(id)
    }
    fun cancelAlarmSnooze(id: Long) {
        cancel(ACTION_ALARM_SNOOZE, id)
        context.getSystemService(NotificationManager::class.java).cancel(snoozedNotificationId(id))
    }

    /** Tapping "Cancel snooze" on the "Snoozed until" notification. */
    fun cancelSnoozeIntent(id: Long): PendingIntent = PendingIntent.getBroadcast(
        context, id.toInt(), intent(ACTION_ALARM_SNOOZE_CANCEL, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Health nudges are quiet checks, so no status-bar alarm icon: plain exact alarm. */
    fun scheduleNudge(id: Long, atMillis: Long) = setExact(atMillis, trigger(ACTION_HEALTH_NUDGE, id))
    fun cancelNudge(id: Long) = cancel(ACTION_HEALTH_NUDGE, id)

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
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, Tab.ALARMS.name),
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
        const val EXTRA_ID = "id"
        private const val SHOW_REQUEST = 1

        /** Notification id of the "Snoozed until" note for one alarm. */
        fun snoozedNotificationId(alarmId: Long): Int = 50_000 + alarmId.toInt()
    }
}
