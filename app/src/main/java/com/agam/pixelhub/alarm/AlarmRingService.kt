package com.agam.pixelhub.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.agam.pixelhub.MainActivity
import com.agam.pixelhub.ui.Tab
import com.agam.pixelhub.R
import com.agam.pixelhub.container
import com.agam.pixelhub.data.Alarm
import com.agam.pixelhub.notify.Channels
import com.agam.pixelhub.scheduling.Scheduler
import com.agam.pixelhub.ui.Fmt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime

/**
 * Plays the alarm (looping sound + vibration) as a foreground service and shows
 * [AlarmRingActivity] full-screen. Uses the systemExempted FGS type, which is allowed
 * for apps holding USE_EXACT_ALARM.
 */
class AlarmRingService : Service() {
    data class Ringing(
        val alarmId: Long,
        val label: String,
        val hour: Int,
        val minute: Int,
        val snoozeMinutes: Int,
        /** Ringing again after a snooze, so the ring screen and notification say so. */
        val snoozed: Boolean = false,
    )

    private var player: MediaPlayer? = null
    private var systemRingtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    /** Nobody answered for [AUTO_STOP_MS]: stop, but leave a "missed" note so it isn't silent. */
    private val autoStop = Runnable {
        ringingState.value?.let { showMissed(it) }
        finishRinging()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRinging(intent)
            ACTION_SNOOZE -> {
                ringingState.value?.let { r ->
                    val at = System.currentTimeMillis() + r.snoozeMinutes * 60_000L
                    container.scheduler.scheduleAlarmSnooze(r.alarmId, at)
                    showSnoozed(r, at)
                }
                finishRinging()
            }
            ACTION_DISMISS -> {
                ringingState.value?.let { container.scheduler.cancelAlarmSnooze(it.alarmId) }
                finishRinging()
            }
            // Android 14+ lets an unlocked user swipe a foreground-service notification away, which
            // left the alarm ringing with no control anywhere. Put it straight back.
            ACTION_REPOST -> ringingState.value?.let { r ->
                runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(r)) }
            }
            else -> if (ringingState.value == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRinging(intent: Intent) {
        val ringing = Ringing(
            alarmId = intent.getLongExtra(EXTRA_ID, -1L),
            label = intent.getStringExtra(EXTRA_LABEL).orEmpty(),
            hour = intent.getIntExtra(EXTRA_HOUR, 0),
            minute = intent.getIntExtra(EXTRA_MINUTE, 0),
            snoozeMinutes = intent.getIntExtra(EXTRA_SNOOZE, 10),
            snoozed = intent.getBooleanExtra(EXTRA_SNOOZED, false),
        )
        // Another alarm is already ringing: snooze it rather than silently dropping it.
        ringingState.value?.takeIf { it.alarmId != ringing.alarmId }?.let { previous ->
            val at = System.currentTimeMillis() + previous.snoozeMinutes * 60_000L
            container.scheduler.scheduleAlarmSnooze(previous.alarmId, at)
            showSnoozed(previous, at)
        }
        stopSound()
        getSystemService(NotificationManager::class.java).cancel(Scheduler.snoozedNotificationId(ringing.alarmId))
        getSystemService(NotificationManager::class.java).cancel(missedNotificationId(ringing.alarmId))
        // Publish state before the full-screen intent can launch the activity.
        ringingState.value = ringing
        startForeground(
            NOTIFICATION_ID, buildNotification(ringing),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
        )
        playSound(intent.getStringExtra(EXTRA_RINGTONE))
        if (intent.getBooleanExtra(EXTRA_VIBRATE, true)) vibrate()
        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, AUTO_STOP_MS)
    }

    private fun buildNotification(r: Ringing): Notification {
        val fullScreen = PendingIntent.getActivity(
            this, 0,
            Intent(this, AlarmRingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, Channels.ALARMS)
            .setSmallIcon(R.drawable.ic_stat_pixelhub)
            .setContentTitle(r.label.ifBlank { "Alarm" })
            .setContentText(
                if (r.snoozed) "Snoozed · set for ${Fmt.time(this, LocalTime.of(r.hour, r.minute))}"
                else Fmt.time(this, LocalTime.of(r.hour, r.minute)),
            )
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDeleteIntent(servicePending(ACTION_REPOST, 3))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .addAction(0, "Snooze ${r.snoozeMinutes} min", servicePending(ACTION_SNOOZE, 1))
            .addAction(0, "Dismiss", servicePending(ACTION_DISMISS, 2))
            .build()
    }

    /**
     * A snoozed one-off alarm is already switched off in the list, so this quiet note is where you
     * see that it will ring again, and where you can call it off.
     */
    private fun showSnoozed(r: Ringing, atMillis: Long) {
        val at = java.time.Instant.ofEpochMilli(atMillis).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
        val n = NotificationCompat.Builder(this, Channels.ALARMS)
            .setSmallIcon(R.drawable.ic_stat_pixelhub)
            .setContentTitle("${r.label.ifBlank { "Alarm" }} snoozed")
            .setContentText("Rings again at ${Fmt.time(this, at)}")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSilent(true)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(0, "Cancel snooze", container.scheduler.cancelSnoozeIntent(r.alarmId))
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(Scheduler.snoozedNotificationId(r.alarmId), n) }
    }

    /** Left when an alarm rang out unanswered; tapping it opens the alarms list. */
    private fun showMissed(r: Ringing) {
        val open = PendingIntent.getActivity(
            this, MISSED_REQUEST,
            // Own action so request code 7 can't merge with reminder #7's notification intent.
            Intent(this, MainActivity::class.java)
                .setAction(Scheduler.ACTION_OPEN_ALARMS)
                .putExtra(MainActivity.EXTRA_TAB, Tab.ALARMS.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(this, Channels.ALARMS)
            .setSmallIcon(R.drawable.ic_stat_pixelhub)
            .setContentTitle("Missed alarm: ${r.label.ifBlank { "Alarm" }}")
            .setContentText("Rang at ${Fmt.time(this, LocalTime.of(r.hour, r.minute))} for 10 minutes")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { getSystemService(NotificationManager::class.java).notify(missedNotificationId(r.alarmId), n) }
    }

    private fun servicePending(action: String, request: Int): PendingIntent = PendingIntent.getService(
        this, request,
        Intent(this, AlarmRingService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun playSound(ringtone: String?) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM) // rings even when the phone is on silent
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val candidates = listOfNotNull(
            ringtone?.toUri(),
            RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM),
            Settings.System.DEFAULT_ALARM_ALERT_URI,
            Settings.System.DEFAULT_RINGTONE_URI,
        )
        for ((i, uri) in candidates.withIndex()) {
            val mp = MediaPlayer()
            try {
                mp.setAudioAttributes(attrs)
                mp.setDataSource(this, uri)
                mp.isLooping = true
                mp.prepare()
                mp.start()
                player = mp
                return
            } catch (e: Exception) {
                mp.release()
                // The chosen sound may live in shared storage PixelHub can't open; Ringtone can
                // hand playback to the system, which can. Only for the user's own pick.
                if (i == 0 && ringtone != null) {
                    val r = runCatching {
                        RingtoneManager.getRingtone(this, uri)?.apply {
                            audioAttributes = attrs
                            isLooping = true
                            play()
                        }
                    }.getOrNull()
                    if (r != null && r.isPlaying) {
                        systemRingtone = r
                        return
                    }
                    runCatching { r?.stop() }
                }
            }
        }
    }

    private fun vibrate() {
        val v = getSystemService(VibratorManager::class.java).defaultVibrator
        v.vibrate(
            VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0),
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
        )
        vibrator = v
    }

    private fun stopSound() {
        player?.let {
            runCatching { it.stop() }
            it.release()
        }
        player = null
        systemRingtone?.let { runCatching { it.stop() } }
        systemRingtone = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun finishRinging() {
        handler.removeCallbacks(autoStop)
        stopSound()
        ringingState.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoStop)
        stopSound()
        ringingState.value = null
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.agam.pixelhub.alarm.START"
        const val ACTION_SNOOZE = "com.agam.pixelhub.alarm.SNOOZE"
        const val ACTION_DISMISS = "com.agam.pixelhub.alarm.DISMISS"
        private const val ACTION_REPOST = "com.agam.pixelhub.alarm.REPOST"
        private const val EXTRA_SNOOZED = "snoozed"
        private const val MISSED_REQUEST = 7

        /** Notification id of the "Missed alarm" note for one alarm. */
        fun missedNotificationId(alarmId: Long): Int = 60_000 + alarmId.toInt()
        private const val EXTRA_ID = "id"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_HOUR = "hour"
        private const val EXTRA_MINUTE = "minute"
        private const val EXTRA_SNOOZE = "snooze"
        private const val EXTRA_RINGTONE = "ringtone"
        private const val EXTRA_VIBRATE = "vibrate"
        private const val NOTIFICATION_ID = 42
        private const val AUTO_STOP_MS = 10 * 60 * 1000L

        private val ringingState = MutableStateFlow<Ringing?>(null)
        /** The alarm ringing right now, or null. */
        val state: StateFlow<Ringing?> = ringingState.asStateFlow()

        fun start(context: Context, alarm: Alarm, snoozed: Boolean = false) {
            val intent = Intent(context, AlarmRingService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_ID, alarm.id)
                .putExtra(EXTRA_LABEL, alarm.label)
                .putExtra(EXTRA_HOUR, alarm.hour)
                .putExtra(EXTRA_MINUTE, alarm.minute)
                .putExtra(EXTRA_SNOOZE, alarm.snoozeMinutes)
                .putExtra(EXTRA_RINGTONE, alarm.ringtoneUri)
                .putExtra(EXTRA_VIBRATE, alarm.vibrate)
                .putExtra(EXTRA_SNOOZED, snoozed)
            context.startForegroundService(intent)
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, AlarmRingService::class.java).setAction(action))
        }
    }
}
