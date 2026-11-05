package com.agam.pixelhub.ui.settings

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.agam.pixelhub.notify.Channels

enum class CheckKind { NOTIFICATIONS, EXACT_ALARMS, FULL_SCREEN, BATTERY, ALARM_CHANNEL }

data class SetupCheck(
    val kind: CheckKind,
    val title: String,
    val detail: String,
    val ok: Boolean,
    /** System screen that fixes it. */
    val fix: Intent,
)

object SetupChecks {
    fun evaluate(context: Context): List<SetupCheck> {
        val pkg = context.packageName
        val pkgUri = "package:$pkg".toUri()
        val nm = context.getSystemService(NotificationManager::class.java)
        val am = context.getSystemService(AlarmManager::class.java)
        val pm = context.getSystemService(PowerManager::class.java)
        val alarmChannelOn = nm.getNotificationChannel(Channels.ALARMS)
            ?.let { it.importance != NotificationManager.IMPORTANCE_NONE } ?: true

        return listOf(
            SetupCheck(
                CheckKind.NOTIFICATIONS, "Notifications",
                "Needed to show reminders and alarms.",
                nm.areNotificationsEnabled(),
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg),
            ),
            SetupCheck(
                CheckKind.EXACT_ALARMS, "Alarms & reminders",
                "Lets alarms and reminders fire on the exact minute.",
                am.canScheduleExactAlarms(),
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkgUri),
            ),
            SetupCheck(
                CheckKind.FULL_SCREEN, "Full-screen alarms",
                "Shows a ringing alarm over the lock screen.",
                nm.canUseFullScreenIntent(),
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, pkgUri),
            ),
            SetupCheck(
                CheckKind.BATTERY, "Unrestricted battery",
                "Stops Android from delaying PixelHub in the background.",
                pm.isIgnoringBatteryOptimizations(pkg),
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkgUri),
            ),
            SetupCheck(
                CheckKind.ALARM_CHANNEL, "Alarm notifications on",
                "The \"Alarms\" notification category must stay enabled.",
                alarmChannelOn,
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, Channels.ALARMS),
            ),
        )
    }
}

/**
 * Remembers which runtime permissions PixelHub has already asked for. Android stops showing its
 * prompt after repeated denials, and shouldShowRequestPermissionRationale is also false before
 * the first ask, so "asked before and no rationale" is the only safe sign that a tap should open
 * system settings instead of asking. Decided before launching, never from the result.
 */
internal object PermissionAsks {
    private const val PREFS = "permission_asks"

    fun askedBefore(context: Context, key: String): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, false)

    fun markAsked(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, true).apply()
    }
}

/** Re-evaluated every time the screen resumes, e.g. after coming back from system settings. */
@Composable
fun rememberSetupChecks(): List<SetupCheck> {
    val context = LocalContext.current
    var checks by remember { mutableStateOf(SetupChecks.evaluate(context)) }
    LifecycleResumeEffect(Unit) {
        checks = SetupChecks.evaluate(context)
        onPauseOrDispose { }
    }
    return checks
}
