package com.agam.pixelhub.widget

import android.content.Context
import android.os.PowerManager
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import com.agam.pixelhub.AppContainer
import com.agam.pixelhub.calendar.CalendarEvents
import com.agam.pixelhub.media.NowPlaying
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.YearMonth

private const val HOUR_MS = 60 * 60_000L
private const val DAY_MS = 24 * HOUR_MS

/**
 * Keeps home-screen widgets current while PixelHub's process is alive: each widget redraws
 * when its data changes, wherever the change came from. The 30-minute system update is
 * the fallback for the rest of the time.
 */
class WidgetSync(private val context: Context, private val scope: CoroutineScope) {
    @OptIn(ExperimentalCoroutinesApi::class)
    fun start(c: AppContainer) {
        // Health: every new reading the app makes goes to the widget's cache and redraws it,
        // only when a figure actually changed (the Health screen reads every minute).
        scope.launch {
            c.health.latest.filterNotNull()
                .distinctUntilChangedBy { listOf(it.steps, it.exerciseMinutes, it.activeKcal.toLong(), it.distanceMeters.toLong()) }
                .collect {
                    HealthCache.save(context, it)
                    HealthWidget().updateAll(context)
                }
        }
        // Reminders: any change to any reminder (app, notification Done, the widget's ✓) redraws it.
        scope.launch { c.reminders.all.distinctUntilChanged().collect { RemindersWidget().updateAll(context) } }
        // Alarms: added, edited, deleted, switched, or turned off after ringing.
        scope.launch { c.alarms.all.distinctUntilChanged().collect { AlarmsWidget().updateAll(context) } }
        // Money: any expense or budget change in the current month, moving to the new month on the 1st.
        scope.launch {
            hourly { YearMonth.now() }.distinctUntilChanged()
                .flatMapLatest { c.expenses.month(it) }
                .distinctUntilChanged()
                .collect { MoneyWidget().updateAll(context) }
        }
        // Calendar: redraws when the phone's calendars change (a sync, an edit in Google Calendar).
        // Only with permission; without it the widget shows its "allow access" state.
        val calendar = CalendarEvents(context)
        if (calendar.hasPermission()) scope.launch {
            val now = System.currentTimeMillis()
            // The window only signals "something changed"; the widget re-reads with the current time.
            calendar.observe(now, now + 2 * DAY_MS).drop(1).collect { CalendarWidget().updateAll(context) }
        }
        // Music: redraw the moment the track, artist, art or play/pause changes, from any app
        // (Spotify, YT Music, a Bluetooth button, the lock screen…). PixelHub's media listener
        // keeps the process alive while notification access is on, so this runs with the app closed.
        if (NowPlaying.hasAccess(context)) {
            scope.launch {
                NowPlaying.flow(context)
                    .distinctUntilChangedBy { s -> listOf(s?.pkg, s?.title, s?.artist, s?.playing, s?.art?.generationId) }
                    .collect { MusicWidget().updateAll(context) }
            }
            // While music plays and the screen is on, redraw every 5 s so the progress bar moves.
            // Stops when paused or the screen is off, so it costs nothing while idle.
            val power = context.getSystemService(PowerManager::class.java)
            scope.launch {
                NowPlaying.flow(context).map { it?.playing == true }.distinctUntilChanged()
                    .flatMapLatest { playing -> if (playing) every(5_000L) else emptyFlow() }
                    .collect { if (power.isInteractive) MusicWidget().updateAll(context) }
            }
        }
        scope.launch { publishPreviews() }
    }

    /** Ticks every [ms] while collected. */
    private fun every(ms: Long): Flow<Unit> = flow {
        while (true) {
            delay(ms)
            emit(Unit)
        }
    }

    /** [value] now, and again every hour. */
    private fun <T> hourly(value: () -> T): Flow<T> = flow {
        while (true) {
            emit(value())
            delay(HOUR_MS)
        }
    }

    /** Hands Android 15's widget picker the live previews, once per app version (it's rate-limited). */
    private suspend fun publishPreviews() {
        val version = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        val prefs = context.getSharedPreferences("widget_sync", Context.MODE_PRIVATE)
        // One record per widget, so a widget added in a later build still gets its preview.
        val receivers = listOf(
            HealthWidgetReceiver::class, RemindersWidgetReceiver::class, AlarmsWidgetReceiver::class,
            MoneyWidgetReceiver::class, CalendarWidgetReceiver::class, MusicWidgetReceiver::class,
        )
        for (receiver in receivers) {
            val key = "preview_${receiver.simpleName}"
            if (prefs.getLong(key, -1) == version) continue
            runCatching { GlanceAppWidgetManager(context).setWidgetPreviews(receiver) }
                .onSuccess { prefs.edit().putLong(key, version).apply() }
        }
    }
}
