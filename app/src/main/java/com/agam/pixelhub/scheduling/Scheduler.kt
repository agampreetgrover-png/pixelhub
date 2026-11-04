package com.agam.pixelhub.scheduling

import android.app.AlarmManager
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
        cancel(ACTION_ALARM_SNOOZE, id)
    }
    fun cancelAlarmSnooze(id: Long) = cancel(ACTION_ALARM_SNOOZE, id)

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
        const val EXTRA_ID = "id"
        private const val SHOW_REQUEST = 1
    }
}
