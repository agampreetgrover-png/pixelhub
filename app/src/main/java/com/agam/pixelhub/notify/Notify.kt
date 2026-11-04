package com.agam.pixelhub.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.agam.pixelhub.MainActivity
import com.agam.pixelhub.R
import com.agam.pixelhub.container
import com.agam.pixelhub.data.Reminder
import com.agam.pixelhub.scheduling.runAsync
import com.agam.pixelhub.ui.Tab

object Channels {
    const val ALARMS = "alarms"
    const val REMINDERS = "reminders"

    fun create(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(ALARMS, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Ringing alarms"
                setSound(null, null) // AlarmRingService plays the sound itself
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminder notifications"
                enableVibration(true)
            },
        )
    }
}

object ReminderNotifier {
    @SuppressLint("MissingPermission") // checked below
    fun show(context: Context, reminder: Reminder) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val request = reminder.id.toInt()
        val open = PendingIntent.getActivity(
            context, request,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_TAB, Tab.REMINDERS.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = reminder.notes.ifBlank { reminder.recurrence.describe().ifBlank { "Reminder" } }
        val notification = NotificationCompat.Builder(context, Channels.REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_pixelhub)
            .setContentTitle(reminder.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())
            .addAction(0, "Done", action(context, ReminderActionReceiver.ACTION_DONE, reminder.id))
            .addAction(
                0, "Snooze ${ReminderActionReceiver.SNOOZE_MINUTES} min",
                action(context, ReminderActionReceiver.ACTION_SNOOZE, reminder.id),
            )
            .build()
        NotificationManagerCompat.from(context).notify(notificationId(reminder.id), notification)
    }

    fun cancel(context: Context, reminderId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(reminderId))
    }

    private fun notificationId(reminderId: Long) = 10_000 + reminderId.toInt()

    private fun action(context: Context, action: String, id: Long): PendingIntent = PendingIntent.getBroadcast(
        context, id.toInt(),
        Intent(context, ReminderActionReceiver::class.java).setAction(action).putExtra(ReminderActionReceiver.EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Handles the Done / Snooze buttons on reminder notifications. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        if (id < 0) return
        ReminderNotifier.cancel(context, id)
        val c = context.container
        runAsync {
            when (intent.action) {
                ACTION_DONE -> c.reminders.completeFromNotification(id)
                ACTION_SNOOZE -> c.reminders.snooze(id, SNOOZE_MINUTES)
            }
        }
    }

    companion object {
        const val ACTION_DONE = "com.agam.pixelhub.action.REMINDER_DONE"
        const val ACTION_SNOOZE = "com.agam.pixelhub.action.REMINDER_SNOOZE_TAP"
        const val EXTRA_ID = "id"
        const val SNOOZE_MINUTES = 10
    }
}
