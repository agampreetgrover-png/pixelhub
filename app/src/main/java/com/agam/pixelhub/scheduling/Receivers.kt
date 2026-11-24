package com.agam.pixelhub.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.agam.pixelhub.alarm.AlarmRingService
import com.agam.pixelhub.container
import com.agam.pixelhub.health.NudgeCheck
import com.agam.pixelhub.notify.HealthNotifier
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
            intent.action?.let { c.scheduler.snoozeFired(it, id) }
            when (intent.action) {
                Scheduler.ACTION_REMINDER ->
                    c.reminders.onFired(id)?.let { ReminderNotifier.show(context, it) }
                Scheduler.ACTION_REMINDER_SNOOZE ->
                    c.reminders.get(id)?.takeIf { !it.done }?.let { ReminderNotifier.show(context, it) }
                // Next occurrence first: if the ring service fails to start, tomorrow still rings.
                Scheduler.ACTION_ALARM ->
                    c.alarms.onFired(id)?.let { AlarmRingService.start(context, it) }
                Scheduler.ACTION_ALARM_SNOOZE_CANCEL -> c.scheduler.cancelAlarmSnooze(id)
                Scheduler.ACTION_ALARM_SNOOZE -> {
                    val alarm = c.alarms.get(id)
                    if (alarm != null) {
                        AlarmRingService.start(context, alarm, snoozed = true)
                    } else {
                        // Deleted while ringing: nothing rings, so clear its ongoing (unswipeable)
                        // "Snoozed" note, which only the next ring would have removed.
                        c.scheduler.cancelAlarmSnooze(id)
                    }
                }
                Scheduler.ACTION_MONTHLY_REPORT -> c.onMonthlyReport()
                Scheduler.ACTION_HEALTH_NUDGE ->
                    c.healthNudges.onFired(id)?.let { nudge ->
                        when (val check = c.health.check(nudge)) {
                            is NudgeCheck.Behind -> HealthNotifier.showNudge(context, nudge, check.current)
                            NudgeCheck.NoAccess -> HealthNotifier.showNoAccess(context)
                            NudgeCheck.OnTrack -> Unit
                        }
                    }
            }
        }
    }
}

/** AlarmManager forgets everything on reboot; time changes shift wall-clock schedules. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Exported for the system's broadcasts, so any app could also call it by name. These
        // actions are protected: only the system can send them.
        if (intent.action !in HANDLED) return
        val c = context.container
        runAsync {
            // A clock or time-zone change keeps AlarmManager's triggers; overdue ones are delivered as usual.
            c.rearmAll(lostTriggers = intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED)
            // Reminders are wall-clock times: move their calendar copies to the new zone.
            if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) c.reminders.resyncCalendar()
        }
    }

    private companion object {
        // Keep in step with BootReceiver's intent filter in AndroidManifest.xml.
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, // "android.intent.action.TIME_SET"
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}
