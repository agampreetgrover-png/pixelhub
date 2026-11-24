package com.agam.pixelhub.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
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
        /** Alarms due at the same time that this one took over: they share its ring, Snooze and Dismiss. */
        val others: List<Ringing> = emptyList(),
    ) {
        /** This alarm and the ones ringing with it. */
        val all: List<Ringing> get() = listOf(this) + others

        /** Every ringing alarm's label, for the ring screen and notification. */
        val labels: String get() = all.joinToString(" · ") { it.label.ifBlank { "Alarm" } }
    }

    private var player: MediaPlayer? = null
    private var systemRingtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var focusRequest: AudioFocusRequest? = null
    /** When the current alarm started ringing (elapsedRealtime). */
    private var ringStartedAt = 0L
    private val handler = Handler(Looper.getMainLooper())
    /** Nobody answered for [AUTO_STOP_MS]: stop, but leave a "missed" note so it isn't silent. */
    private val autoStop = Runnable {
        ringingState.value?.all?.forEach { r ->
            notifyMissed(this, r.alarmId, r.label, "Rang at ${Fmt.time(this, LocalTime.of(r.hour, r.minute))} for 10 minutes")
        }
        finishRinging()
    }

    /**
     * The newest start this service has seen. Stopping with it, not a bare stopSelf(), keeps the
     * service alive for another alarm's start that is already queued (else Android kills the app
     * for not calling startForeground, and that alarm never rings).
     */
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_START -> startRinging(intent)
            ACTION_SNOOZE -> {
                ringingState.value?.all?.forEach { snooze(it) }
                finishRinging()
            }
            ACTION_DISMISS -> {
                ringingState.value?.all?.forEach { container.scheduler.cancelAlarmSnooze(it.alarmId) }
                finishRinging()
            }
            // Android 14+ lets an unlocked user swipe a foreground-service notification away, which
            // left the alarm ringing with no control anywhere. Put it straight back.
            ACTION_REPOST -> ringingState.value?.let { r ->
                runCatching { getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(r)) }
            }
            else -> if (ringingState.value == null) stopSelfResult(startId)
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
        // Other alarms already ringing (not this one again).
        val previous = ringingState.value?.all.orEmpty()
            .map { it.copy(others = emptyList()) }
            .filter { it.alarmId != ringing.alarmId }
        // One that started moments ago was due at the same time: ring them together, so one Dismiss
        // covers both while Snooze and the missed note still cover each. An older one is snoozed
        // rather than silently dropped.
        val current = if (SystemClock.elapsedRealtime() - ringStartedAt <= TAKEOVER_GRACE_MS) {
            ringing.copy(others = previous)
        } else {
            previous.forEach { snooze(it) }
            ringing
        }
        stopSound()
        getSystemService(NotificationManager::class.java).cancel(Scheduler.snoozedNotificationId(ringing.alarmId))
        getSystemService(NotificationManager::class.java).cancel(missedNotificationId(ringing.alarmId))
        // Publish state before the full-screen intent can launch the activity.
        ringingState.value = current
        ringStartedAt = SystemClock.elapsedRealtime()
        startForeground(
            NOTIFICATION_ID, buildNotification(current),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,
        )
        playSound(intent.getStringExtra(EXTRA_RINGTONE))
        if (intent.getBooleanExtra(EXTRA_VIBRATE, true)) vibrate()
        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, AUTO_STOP_MS)
        // Ringing as a foreground service now: the hand-off is over.
        releaseHandoffWake()
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
            .setContentTitle(r.labels)
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

    /** Rings [r] again after its snooze time. */
    private fun snooze(r: Ringing) {
        val at = System.currentTimeMillis() + r.snoozeMinutes * 60_000L
        container.scheduler.scheduleAlarmSnooze(r.alarmId, at)
        showSnoozed(r, at)
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
        // Pause or duck music and podcasts. Best effort: focus is denied in a call, and the alarm must still ring.
        // Kept while one alarm takes over from another, so the music doesn't resume in between.
        // Only a granted request is kept, so a takeover after a denial (say, in a call) asks again.
        if (focusRequest == null) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).build()
            val res = runCatching { getSystemService(AudioManager::class.java).requestAudioFocus(req) }.getOrNull()
            if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) focusRequest = req
        }
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

    /** Lets paused music play again; only once no alarm is ringing. */
    private fun releaseFocus() {
        focusRequest?.let { req -> runCatching { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(req) } }
        focusRequest = null
    }

    private fun finishRinging() {
        handler.removeCallbacks(autoStop)
        stopSound()
        releaseFocus()
        ringingState.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelfResult(lastStartId)
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoStop)
        stopSound()
        releaseFocus()
        ringingState.value = null
        releaseHandoffWake()
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

        /** The "Missed alarm" note (rang out unanswered, or due while the phone was off); tapping it opens the alarms list. */
        fun notifyMissed(context: Context, alarmId: Long, label: String, detail: String) {
            val open = PendingIntent.getActivity(
                context, MISSED_REQUEST,
                // Own action so request code 7 can't merge with reminder #7's notification intent.
                Intent(context, MainActivity::class.java)
                    .setAction(Scheduler.ACTION_OPEN_ALARMS)
                    .putExtra(MainActivity.EXTRA_TAB, Tab.ALARMS.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, Channels.ALARMS)
                .setSmallIcon(R.drawable.ic_stat_pixelhub)
                .setContentTitle("Missed alarm: ${label.ifBlank { "Alarm" }}")
                .setContentText(detail)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setSilent(true)
                .setAutoCancel(true)
                .setContentIntent(open)
                .build()
            runCatching { context.getSystemService(NotificationManager::class.java).notify(missedNotificationId(alarmId), n) }
        }
        private const val EXTRA_ID = "id"
        private const val EXTRA_LABEL = "label"
        private const val EXTRA_HOUR = "hour"
        private const val EXTRA_MINUTE = "minute"
        private const val EXTRA_SNOOZE = "snooze"
        private const val EXTRA_RINGTONE = "ringtone"
        private const val EXTRA_VIBRATE = "vibrate"
        private const val NOTIFICATION_ID = 42
        private const val AUTO_STOP_MS = 10 * 60 * 1000L
        private const val HANDOFF_WAKE_MS = 60_000L
        /** A second alarm within this long of the first takes over without snoozing the first. */
        private const val TAKEOVER_GRACE_MS = 60_000L

        private val ringingState = MutableStateFlow<Ringing?>(null)
        /** The alarm ringing right now, or null. */
        val state: StateFlow<Ringing?> = ringingState.asStateFlow()

        /** Whether alarm [id] is ringing now, on its own or together with another. */
        fun isRinging(id: Long): Boolean = ringingState.value?.all?.any { it.alarmId == id } == true

        /**
         * Keeps the CPU awake from the alarm broadcast until the service is ringing. The
         * broadcast's own wake lock ends when the receiver finishes, and starting a service is
         * asynchronous: with the screen off the phone could sleep in between and ring late.
         */
        private var handoffWake: PowerManager.WakeLock? = null

        private fun releaseHandoffWake() {
            handoffWake?.takeIf { it.isHeld }?.release()
            handoffWake = null
        }

        fun start(context: Context, alarm: Alarm, snoozed: Boolean = false) {
            handoffWake?.takeIf { it.isHeld }?.release()
            handoffWake = context.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PixelHub:alarm-handoff")
                .apply { setReferenceCounted(false); acquire(HANDOFF_WAKE_MS) }
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
