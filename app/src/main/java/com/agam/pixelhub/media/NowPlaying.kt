package com.agam.pixelhub.media

import android.app.ActivityOptions
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Exists only so Android lets PixelHub see other apps' media sessions (Spotify, YT Music…).
 * MediaSessionManager.getActiveSessions requires an enabled notification listener; PixelHub
 * never reads or acts on the notifications themselves.
 */
class MediaListener : NotificationListenerService()

/** What the phone is playing right now, from the top media session. */
data class NowPlayingState(
    val controller: MediaController,
    val pkg: String,
    val appLabel: String,
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val playing: Boolean,
    /** 0 when the player doesn't say. */
    val durationMs: Long,
    val positionMs: Long,
    /** SystemClock.elapsedRealtime() when [positionMs] was measured. */
    val positionAt: Long,
    val speed: Float,
) {
    /** Paused tracks stay on the card so they can be resumed; stopped or empty sessions don't. */
    val showable: Boolean get() = title.isNotBlank()

    fun positionNow(elapsedNow: Long = SystemClock.elapsedRealtime()): Long {
        if (!playing || positionMs < 0) return positionMs.coerceAtLeast(0)
        val p = positionMs + ((elapsedNow - positionAt) * speed).toLong()
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }
}

object NowPlaying {
    private fun component(context: Context) = ComponentName(context, MediaListener::class.java)

    /**
     * The latest state any collector saw, kept for the life of the process. Home starts from it,
     * so coming back to the tab doesn't fold the card away and expand it again.
     */
    @Volatile
    var last: NowPlayingState? = null
        private set

    fun hasAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(component(context))

    /** Opens the system switch for PixelHub's notification access (the list, if that screen is missing). */
    fun openAccessSettings(context: Context) {
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(context).flattenToString())
        try {
            context.startActivity(detail)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    /**
     * The top media session, updated live. Emits null when nothing usable is playing or
     * notification access is off. Collect it only while the UI is visible.
     */
    fun flow(context: Context): Flow<NowPlayingState?> = callbackFlow {
        val app = context.applicationContext
        val msm = app.getSystemService(MediaSessionManager::class.java)
        val component = component(app)
        val handler = Handler(Looper.getMainLooper())
        val watcher = Watcher(app, handler, { last = it; trySend(it) }) {
            try { msm.getActiveSessions(component) } catch (_: SecurityException) { emptyList() }
        }
        try {
            msm.addOnActiveSessionsChangedListener(watcher, component, handler)
            watcher.onActiveSessionsChanged(msm.getActiveSessions(component))
        } catch (_: SecurityException) {
            last = null
            trySend(null) // access was revoked between the check and here
        }
        awaitClose {
            msm.removeOnActiveSessionsChangedListener(watcher)
            watcher.release()
        }
    }.conflate()

    fun playPause(state: NowPlayingState) {
        val controls = state.controller.transportControls
        if (state.playing) controls.pause() else controls.play()
    }

    fun next(state: NowPlayingState) = state.controller.transportControls.skipToNext()

    fun previous(state: NowPlayingState) = state.controller.transportControls.skipToPrevious()

    /** Brings the player to the front: its own "now playing" screen if it offers one. */
    fun openPlayer(context: Context, state: NowPlayingState) {
        val session = state.controller.sessionActivity
        if (session != null) {
            try {
                // PixelHub is in the foreground; lend that to the player's PendingIntent (Android 14+).
                // On 16+ only while PixelHub is visible, so another app's PendingIntent never gets
                // a standing right to start activities from the background.
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
                } else {
                    @Suppress("DEPRECATION")
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                }
                val options = ActivityOptions.makeBasic().setPendingIntentBackgroundActivityStartMode(mode)
                session.send(context, 0, null, null, null, null, options.toBundle())
                return
            } catch (_: Exception) {
                // Cancelled or refused; fall back to launching the app.
            }
        }
        context.packageManager.getLaunchIntentForPackage(state.pkg)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?.let { runCatching { context.startActivity(it) } }
    }
}

/**
 * Watches every active session, not just the one on the card: a stopped player that starts
 * again, or a second player starting while the first is paused, both show up at once.
 */
private class Watcher(
    private val app: Context,
    private val handler: Handler,
    private val emit: (NowPlayingState?) -> Unit,
    private val sessions: () -> List<MediaController>,
) : MediaController.Callback(), MediaSessionManager.OnActiveSessionsChangedListener {
    private val observed = HashMap<MediaSession.Token, MediaController>()
    private var current: MediaController? = null

    override fun onActiveSessionsChanged(controllers: List<MediaController>?) = pick(controllers.orEmpty())

    // The callback is shared by every session, so any change re-picks: whichever is playing wins.
    override fun onMetadataChanged(metadata: MediaMetadata?) = pick(sessions())

    override fun onPlaybackStateChanged(state: PlaybackState?) = pick(sessions())

    override fun onSessionDestroyed() = pick(sessions())

    private fun pick(list: List<MediaController>) {
        val live = list.associateBy { it.sessionToken }
        (observed.keys - live.keys).forEach { observed.remove(it)?.unregisterCallback(this) }
        (live.keys - observed.keys).forEach { token ->
            live.getValue(token).let { it.registerCallback(this, handler); observed[token] = it }
        }
        current = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.metadata != null && it.playbackState?.state.let { s -> s != null && s != PlaybackState.STATE_STOPPED && s != PlaybackState.STATE_NONE } }
        publish()
    }

    private fun publish() = emit(current?.let { toState(it) }?.takeIf { it.showable })

    fun release() {
        observed.values.forEach { it.unregisterCallback(this) }
        observed.clear()
        current = null
    }

    private fun toState(c: MediaController): NowPlayingState {
        val md = c.metadata
        val ps = c.playbackState
        val pkg = c.packageName
        val label = try {
            val pm = app.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            pkg
        }
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: md?.description?.title?.toString().orEmpty()
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: md?.description?.subtitle?.toString().orEmpty()
        val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        return NowPlayingState(
            controller = c,
            pkg = pkg,
            appLabel = label,
            title = title,
            artist = artist,
            art = art,
            playing = ps?.state == PlaybackState.STATE_PLAYING,
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0,
            positionMs = ps?.position ?: 0,
            // Some players never set the update time (0): counting from boot would pin the bar at the end.
            positionAt = ps?.lastPositionUpdateTime?.takeIf { it > 0L } ?: SystemClock.elapsedRealtime(),
            speed = ps?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
        )
    }
}
