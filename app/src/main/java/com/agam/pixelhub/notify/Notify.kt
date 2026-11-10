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
import com.agam.pixelhub.data.HealthNudge
import com.agam.pixelhub.data.Reminder
import com.agam.pixelhub.expenses.BudgetAlert
import com.agam.pixelhub.expenses.Money
import com.agam.pixelhub.expenses.MonthReport
import com.agam.pixelhub.expenses.oneLine
import com.agam.pixelhub.health.NudgeMath
import com.agam.pixelhub.scheduling.runAsync
import com.agam.pixelhub.ui.Tab
import java.time.format.DateTimeFormatter
import java.util.Locale

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
        nm.createNotificationChannel(
            NotificationChannel(HEALTH, "Activity nudges", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Reminders to move when you're behind your step or activity goal"
            },
        )
        createBudget(context)
        nm.createNotificationChannel(
            NotificationChannel(REPORTS, "Monthly report", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Last month's spending, on the 1st of each month"
            },
        )
    }

    const val HEALTH = "health"
    const val BUDGET = "budget"
    const val REPORTS = "reports"

    internal fun createBudget(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(BUDGET, "Budget alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When this month's spending reaches 80% or 100% of a budget"
            },
        )
    }
}

object BudgetNotifier {
    @SuppressLint("MissingPermission") // checked below
    fun show(context: Context, alert: BudgetAlert) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val what = alert.category?.label ?: "monthly budget"
        val title = if (alert.percent >= 100) "Over your $what" else "${alert.percent}% of your $what used"
        val body = "${Money.format(alert.spentPaise)} of ${Money.format(alert.limitPaise)} spent this month."
        val id = 30_000 + (alert.category?.ordinal ?: 99)
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java)
                .setAction("com.agam.pixelhub.open.BUDGET")
                .putExtra(MainActivity.EXTRA_TAB, EXPENSES_TAB)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, Channels.BUDGET)
            .setSmallIcon(R.drawable.ic_stat_pixelhub)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(open)
            .setAutoCancel(true)
            // Spending figures stay off the lock screen; they show once the phone is unlocked.
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /** Tab.EXPENSES.name (the redesign adds that tab); unknown names fall back to Home. */
    private const val EXPENSES_TAB = "EXPENSES"
}

/** Last month in one note on the 1st; tapping it opens that month's report on the Money page. */
object ReportNotifier {
    private const val ID = 31_000

    @SuppressLint("MissingPermission") // checked below
    fun show(context: Context, report: MonthReport) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val s = report.summary
        val name = report.month.format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault()))
        val body = buildString {
            append(report.oneLine())
            if (s.incomePaise > 0) append("\nReceived +").append(Money.format(s.incomePaise))
            report.top.firstOrNull()?.let { append("\nMost on ").append(it.category.label).append(": ").append(Money.format(it.paise)) }
        }
        val open = PendingIntent.getActivity(
            context, ID,
            Intent(context, MainActivity::class.java)
                .setAction("com.agam.pixelhub.open.REPORT")
                .putExtra(MainActivity.EXTRA_TAB, Tab.EXPENSES.name)
                .putExtra(MainActivity.EXTRA_REPORT_MONTH, report.month.toString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, Channels.REPORTS)
            .setSmallIcon(R.drawable.ic_stat_pixelhub)
            .setContentTitle("Your $name report")
            .setContentText(report.oneLine())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            // Money figures stay off the lock screen; they show once the phone is unlocked.
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
        NotificationManagerCompat.from(context).notify(ID, notification)
    }
}

object HealthNotifier {
    private const val NO_ACCESS_ID = 29_999

    @SuppressLint("MissingPermission") // checked in canNotify
    fun showNudge(context: Context, nudge: HealthNudge, current: Long) {
        if (!canNotify(context)) return
        val (title, body) = NudgeMath.message(nudge.metric, current, nudge.goal)
        val notification = NotificationCompat.Builder(context, Channels.HEALTH)
            .setSmallIcon(R.drawable.ic_stat_pixelhub)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp(context, Tab.HOME, 20_000 + nudge.id.toInt()))
            .setAutoCancel(true)
            // Health figures stay off the lock screen; they show once the phone is unlocked.
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
        NotificationManagerCompat.from(context).notify(20_000 + nudge.id.toInt(), notification)
    }

    /** A nudge couldn't read Health Connect (permission revoked or background access off). */
    @SuppressLint("MissingPermission")
    fun showNoAccess(context: Context) {
        if (!canNotify(context)) return
        val notification = NotificationCompat.Builder(context, Channels.HEALTH)
            .setSmallIcon(R.drawable.ic_stat_pixelhub)
            .setContentTitle("Activity nudge skipped")
            .setContentText("PixelHub can't read your steps in the background. Tap to reconnect Health Connect.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("PixelHub can't read your steps in the background. Tap to reconnect Health Connect."),
            )
            .setContentIntent(openApp(context, Tab.SETTINGS, NO_ACCESS_ID))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(NO_ACCESS_ID, notification)
    }

    private fun canNotify(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context, tab: Tab, request: Int): PendingIntent = PendingIntent.getActivity(
        context, request,
        Intent(context, MainActivity::class.java)
            .setAction("com.agam.pixelhub.open.HEALTH")
            .putExtra(MainActivity.EXTRA_TAB, tab.name)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
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
            // Each notifier opens MainActivity with its own action: PendingIntents match on action
            // and component, not extras, so a shared request code (reminder #1 vs the alarm icon,
            // #7 vs "Missed alarm") made one overwrite the other's tab.
            Intent(context, MainActivity::class.java)
                .setAction("com.agam.pixelhub.open.REMINDERS")
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
