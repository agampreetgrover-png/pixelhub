package com.agam.pixelhub.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.agam.pixelhub.alarm.AlarmRingService
import com.agam.pixelhub.container
import com.agam.pixelhub.notify.ReminderNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Runs [block] off the main thread while keeping the broadcast alive. */
internal fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            block()
        } catch (e: Exception) {
            Log.e("PixelHub", "Receiver work failed", e)
        } finally {
            pending.finish()
        }
    }
}

/** Receives every AlarmManager trigger scheduled by [Scheduler]. */
class TriggerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(Scheduler.EXTRA_ID, -1L)
        if (id < 0) return
        val c = context.container
        runAsync {
            when (intent.action) {
                Scheduler.ACTION_REMINDER ->
                    c.reminders.onFired(id)?.let { ReminderNotifier.show(context, it) }
                Scheduler.ACTION_REMINDER_SNOOZE ->
                    c.reminders.get(id)?.takeIf { !it.done }?.let { ReminderNotifier.show(context, it) }
                Scheduler.ACTION_ALARM ->
                    c.alarms.get(id)?.takeIf { it.enabled }?.let {
                        AlarmRingService.start(context, it)
                        c.alarms.onFired(id)
                    }
                Scheduler.ACTION_ALARM_SNOOZE ->
                    c.alarms.get(id)?.let { AlarmRingService.start(context, it) }
            }
        }
    }
}

/** AlarmManager forgets everything on reboot; time changes shift wall-clock schedules. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val c = context.container
        runAsync {
            c.alarms.rescheduleAll()
            c.reminders.rescheduleAll().forEach { ReminderNotifier.show(context, it) }
        }
    }
}
