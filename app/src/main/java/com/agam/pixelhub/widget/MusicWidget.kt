package com.agam.pixelhub.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import com.agam.pixelhub.MainActivity
import com.agam.pixelhub.R
import com.agam.pixelhub.media.NowPlaying
import com.agam.pixelhub.media.NowPlayingState
import com.agam.pixelhub.widget.kit.WidgetShape
import com.agam.pixelhub.widget.kit.shapeOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withTimeoutOrNull
import androidx.glance.color.ColorProvider as DayNightColor
import androidx.glance.text.TextStyle as GlanceTextStyle

/** Android's entry point for the music widget. */
class MusicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MusicWidget()
}

/** The fixed colours of the three reference designs. */
private object M {
    val Black = Color(0xFF000000)
    val White = Color(0xFFFFFFFF)
    val Grey = Color(0xFF9A9AA0)
    val Track = Color(0x40FFFFFF)
    val Green = Color(0xFF1ED760)
    val BarsIdle = Color(0xFF6B6B70)
    val CardInk = Color(0xFF16161A)
    val CardMuted = Color(0xFF6E6E76)
    val CardTrack = Color(0xFFD9D9DE)
    val Blue = Color(0xFF1D4ED8)
    val Button = Color(0xFF2A2A2A)
    val Soft = Color(0xCCFFFFFF)
}

private fun fixed(c: Color): ColorProvider = DayNightColor(c, c)

/** What the widget draws, taken from the media session (or made up for the picker's preview). */
private class MusicView(
    val title: String,
    val artist: String,
    val appLabel: String,
    val pkg: String,
    val art: Bitmap?,
    val appIcon: Bitmap?,
    val playing: Boolean,
    val durationMs: Long,
    /** Position at [positionAt] (SystemClock.elapsedRealtime). */
    val positionMs: Long,
    val positionAt: Long,
    val speed: Float,
    val muted: Boolean,
) {
    fun positionNow(): Long {
        val now = SystemClock.elapsedRealtime()
        // Live streams report -1: show 0 rather than a clock counting up from nothing.
        if (!playing || positionMs < 0) return positionMs.coerceAtLeast(0)
        val p = positionMs + ((now - positionAt) * speed).toLong()
        return if (durationMs > 0) p.coerceIn(0, durationMs) else p.coerceAtLeast(0)
    }

    /**
     * A copy measured now. The layouts only redraw when they're given a different MusicView
     * (Compose skips a composable whose arguments are the same objects), so each progress tick
     * passes a fresh one.
     */
    fun now(): MusicView = MusicView(
        title, artist, appLabel, pkg, art, appIcon, playing, durationMs,
        positionMs = positionNow(), positionAt = SystemClock.elapsedRealtime(), speed = speed, muted = muted,
    )
}

/**
 * How often the times and progress bar move while a song plays with the screen on: every second,
 * so the clock reads like the player's. The art is scaled once per song, so a tick only resends
 * bitmaps that are already built.
 */
private const val TICK_MS = 1_000L

private sealed interface MusicState {
    data class Playing(val view: MusicView) : MusicState
    data object Nothing : MusicState
    data object NoAccess : MusicState
}

class MusicWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val first = load(context)
        provideContent {
            // Follows the session live: a song change redraws at once, not when the session ends.
            val state by remember { live(context) }.collectAsState(first)
            MusicContent(context, state)
        }
    }

    /** The widget picker shows the real widget with a sample track. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        val sample = MusicView(
            title = "Blinding Lights", artist = "The Weeknd", appLabel = "Spotify", pkg = "com.spotify.music",
            art = null, appIcon = null, playing = true, durationMs = 200_000, positionMs = 51_000,
            positionAt = SystemClock.elapsedRealtime(), speed = 1f, muted = false,
        )
        provideContent { MusicContent(context, MusicState.Playing(sample)) }
    }
}

/** The current session, read once through NowPlaying (the same source as the Home card). */
private suspend fun current(context: Context): NowPlayingState? =
    withTimeoutOrNull(2_000) { NowPlaying.flow(context).first() }

private suspend fun load(context: Context): MusicState {
    if (!NowPlaying.hasAccess(context)) return MusicState.NoAccess
    val s = current(context) ?: return MusicState.Nothing
    return MusicState.Playing(viewOf(context, s))
}

/**
 * The widget's state, following the top media session as it changes. While a song plays and
 * the screen is on, it re-emits every 5 s so the progress bar and times move.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun live(context: Context): Flow<MusicState> =
    if (!NowPlaying.hasAccess(context)) {
        flowOf(MusicState.NoAccess)
    } else {
        val power = context.getSystemService(PowerManager::class.java)
        NowPlaying.flow(context).flatMapLatest { s ->
            if (s == null) return@flatMapLatest flowOf(MusicState.Nothing)
            val view = viewOf(context, s)
            if (!s.playing) return@flatMapLatest flowOf(MusicState.Playing(view))
            flow {
                while (true) {
                    if (power.isInteractive) emit(MusicState.Playing(view.now()))
                    delay(TICK_MS)
                }
            }
        }
    }

private fun viewOf(context: Context, s: NowPlayingState): MusicView {
    val icon = runCatching { context.packageManager.getApplicationIcon(s.pkg).toBitmap(144, 144) }.getOrNull()
    val audio = context.getSystemService(AudioManager::class.java)
    val art = s.art?.let { fit(it, 192) }
    return MusicView(
        // Scaled once per song: every redraw sends the bitmaps to the launcher again.
        title = s.title, artist = s.artist, appLabel = s.appLabel, pkg = s.pkg, art = art, appIcon = icon,
        playing = s.playing, durationMs = s.durationMs, positionMs = s.positionMs, positionAt = s.positionAt,
        speed = s.speed,
        muted = audio.isStreamMute(AudioManager.STREAM_MUSIC),
    )
}

// ---- Actions -------------------------------------------------------------------------------

private val Kind = ActionParameters.Key<String>("kind")

/** Every music button: play/pause, next, previous, or mute. */
class MusicControlAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        when (parameters[Kind]) {
            "mute" -> context.getSystemService(AudioManager::class.java)
                .adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, 0)
            else -> {
                val s = current(context) ?: return
                when (parameters[Kind]) {
                    "toggle" -> NowPlaying.playPause(s)
                    "next" -> NowPlaying.next(s)
                    "previous" -> NowPlaying.previous(s)
                }
            }
        }
        // The session's own change redraws it too; this makes a tap feel instant.
        MusicWidget().update(context, glanceId)
    }
}

private fun control(kind: String): Action = actionRunCallback<MusicControlAction>(actionParametersOf(Kind to kind))

/** Opens the player app; Spotify (or Home) when nothing is playing. */
private fun openPlayer(context: Context, pkg: String?): Action {
    val launch = pkg?.let { context.packageManager.getLaunchIntentForPackage(it) }
        ?: context.packageManager.getLaunchIntentForPackage("com.spotify.music")
        ?: Intent(context, MainActivity::class.java)
    return actionStartActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

// ---- Layout --------------------------------------------------------------------------------

@Composable
private fun MusicContent(context: Context, state: MusicState) {
    val size = LocalSize.current
    val shape = shapeOf(size)
    when (state) {
        MusicState.NoAccess -> Message(
            "Allow notification access", "To show what's playing",
            actionStartActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)),
        )
        MusicState.Nothing -> Message("Nothing playing", "Tap to open your music", openPlayer(context, null))
        is MusicState.Playing -> {
            val v = state.view
            when {
                shape == WidgetShape.TINY -> Tiny(context, v, size)
                shape == WidgetShape.STRIP_S || shape == WidgetShape.STRIP_M -> SmallPill(context, v)
                shape == WidgetShape.STRIP_L -> LargePill(context, v)
                shape == WidgetShape.PANEL && size.width < 290.dp -> DiscCard(context, v, size)
                else -> Player(context, v, size)
            }
        }
    }
}

@Composable
private fun Label(text: String, color: Color, size: TextUnit, weight: FontWeight = FontWeight.Normal, maxLines: Int = 1) {
    Text(text, maxLines = maxLines, style = GlanceTextStyle(color = fixed(color), fontSize = size, fontWeight = weight))
}

@Composable
private fun Icon(res: Int, tint: Color, size: Dp, description: String? = null) {
    Image(ImageProvider(res), contentDescription = description, colorFilter = ColorFilter.tint(fixed(tint)), modifier = GlanceModifier.size(size))
}

/** A button: a [size] touch target holding [content]. */
@Composable
private fun Button(action: Action, description: String, size: Dp = 44.dp, content: @Composable () -> Unit) {
    Box(
        GlanceModifier.size(size).clickable(action).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * "1:21" or "1:02:05". The widget redraws every 5 seconds while playing (WidgetSync), so the
 * times move in step with the progress bar; a live per-second clock doesn't render in launchers.
 */
private fun clock(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun Art(v: MusicView, size: Dp, radius: Dp) {
    val art = v.art
    if (art != null) {
        Image(ImageProvider(art), contentDescription = null, contentScale = ContentScale.Crop, modifier = GlanceModifier.size(size).cornerRadius(radius))
    } else {
        Box(GlanceModifier.size(size).cornerRadius(radius).background(fixed(M.Button)), contentAlignment = Alignment.Center) {
            Icon(R.drawable.ic_music_bars, M.Grey, size / 2)
        }
    }
}

@Composable
private fun AppIcon(v: MusicView, size: Dp) {
    val icon = v.appIcon ?: return
    Image(ImageProvider(icon), contentDescription = v.appLabel, modifier = GlanceModifier.size(size))
}

private fun describe(v: MusicView) = buildString {
    append("${v.title}, ${v.artist}")
    append(", ${if (v.playing) "playing" else "paused"} in ${v.appLabel}")
}

/** Empty and no-access states, on the black pill look. */
@Composable
private fun Message(title: String, body: String, onClick: Action) {
    Column(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(28.dp).background(fixed(M.Black))
            .clickable(onClick).semantics { contentDescription = "$title. $body" }.padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Label(title, M.White, 14.sp, FontWeight.Medium)
        Label(body, M.Grey, 12.sp)
    }
}

/** 1×1: the art with play/pause on it. */
@Composable
private fun Tiny(context: Context, v: MusicView, size: DpSize) {
    val d = minOf(size.width, size.height)
    Box(GlanceModifier.fillMaxSize().appWidgetBackground().semantics { contentDescription = describe(v) }, contentAlignment = Alignment.Center) {
        Box(GlanceModifier.clickable(openPlayer(context, v.pkg))) { Art(v, d, 20.dp) }
        Button(control("toggle"), if (v.playing) "Pause" else "Play", size = 48.dp) {
            Box(GlanceModifier.size(40.dp).cornerRadius(20.dp).background(fixed(Color(0xB3000000))), contentAlignment = Alignment.Center) {
                Icon(if (v.playing) R.drawable.ic_music_pause else R.drawable.ic_music_play, M.White, 22.dp)
            }
        }
    }
}

/** Image 2, small: green bars, the live time, the player's icon. */
@Composable
private fun SmallPill(context: Context, v: MusicView) {
    Row(
        GlanceModifier.fillMaxSize().appWidgetBackground().background(ImageProvider(R.drawable.widget_music_pill))
            .clickable(openPlayer(context, v.pkg)).semantics { contentDescription = describe(v) }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(R.drawable.ic_music_bars, if (v.playing) M.Green else M.BarsIdle, 22.dp)
        Spacer(GlanceModifier.width(10.dp))
        Label(clock(v.positionNow()), M.White, 17.sp)
        Spacer(GlanceModifier.defaultWeight())
        AppIcon(v, 30.dp)
    }
}

/** Image 2, large: art, bars and time, title and artist, play/pause, the player's icon. */
@Composable
private fun LargePill(context: Context, v: MusicView) {
    Row(
        GlanceModifier.fillMaxSize().appWidgetBackground().background(ImageProvider(R.drawable.widget_music_pill))
            .semantics { contentDescription = describe(v) }.padding(start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.clickable(openPlayer(context, v.pkg))) { Art(v, 52.dp, 12.dp) }
        Spacer(GlanceModifier.width(12.dp))
        Icon(R.drawable.ic_music_bars, if (v.playing) M.Green else M.BarsIdle, 18.dp)
        Spacer(GlanceModifier.width(6.dp))
        Label(clock(v.positionNow()), M.White, 14.sp)
        Spacer(GlanceModifier.width(14.dp))
        Column(GlanceModifier.defaultWeight().clickable(openPlayer(context, v.pkg))) {
            Label(v.title, M.White, 15.sp, FontWeight.Medium)
            Label(v.artist, M.Grey, 12.sp)
        }
        Button(control("toggle"), if (v.playing) "Pause" else "Play") {
            Box(GlanceModifier.size(40.dp).background(ImageProvider(R.drawable.widget_music_circle_btn)), contentAlignment = Alignment.Center) {
                Icon(if (v.playing) R.drawable.ic_music_pause else R.drawable.ic_music_play, M.White, 20.dp)
            }
        }
        Spacer(GlanceModifier.width(6.dp))
        AppIcon(v, 40.dp)
    }
}

/** Image 3 (3×2): a white card with the round album disc over its left edge. */
@Composable
private fun DiscCard(context: Context, v: MusicView, size: DpSize) {
    val disc = (size.height - 12.dp).coerceIn(72.dp, 140.dp)
    Box(GlanceModifier.fillMaxSize().appWidgetBackground().semantics { contentDescription = describe(v) }, contentAlignment = Alignment.CenterStart) {
        // The card starts halfway under the disc.
        Box(GlanceModifier.fillMaxSize().padding(start = disc / 2)) {
            Column(
                GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_music_card_light))
                    .padding(start = disc / 2 + 14.dp, end = 14.dp, top = 6.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The mute button is a full 48 dp touch target; the 12 dp it adds to this row comes
                // out of the top padding and the gap below, so the text sits where it did.
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight().clickable(openPlayer(context, v.pkg))) {
                        Label(v.title, M.CardInk, 15.sp, FontWeight.Medium)
                        Label(v.artist, M.CardMuted, 12.sp)
                    }
                    Button(control("mute"), if (v.muted) "Unmute" else "Mute", size = 48.dp) {
                        Icon(if (v.muted) R.drawable.ic_music_mute else R.drawable.ic_music_volume, M.CardMuted, 20.dp)
                    }
                }
                if (v.durationMs > 0) {
                    Spacer(GlanceModifier.height(2.dp))
                    LinearProgressIndicator(
                        progress = (v.positionNow().toFloat() / v.durationMs).coerceIn(0f, 1f),
                        modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                        color = fixed(M.Blue),
                        backgroundColor = fixed(M.CardTrack),
                    )
                }
                Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Button(control("previous"), "Previous track") { Icon(R.drawable.ic_music_rewind, M.CardInk, 24.dp) }
                    Spacer(GlanceModifier.defaultWeight())
                    Button(control("toggle"), if (v.playing) "Pause" else "Play") {
                        Icon(if (v.playing) R.drawable.ic_music_pause else R.drawable.ic_music_play, M.CardInk, 28.dp)
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    Button(control("next"), "Next track") {
                        Box(GlanceModifier.size(40.dp).cornerRadius(20.dp).background(fixed(M.CardTrack)), contentAlignment = Alignment.Center) {
                            Icon(R.drawable.ic_music_ffwd, M.CardInk, 22.dp)
                        }
                    }
                }
            }
        }
        // The disc, with a white dot at its centre like a record.
        Box(GlanceModifier.size(disc).clickable(openPlayer(context, v.pkg)), contentAlignment = Alignment.Center) {
            Art(v, disc, disc / 2)
            Box(GlanceModifier.size(12.dp).cornerRadius(6.dp).background(fixed(M.White))) {}
        }
    }
}

/**
 * Image 1 (4×2 and up) on the Calendar's clear glass: the art on the left; on the right, the song at
 * the art's top edge, the controls at its bottom edge and the progress bar between them.
 */
@Composable
private fun Player(context: Context, v: MusicView, size: DpSize) {
    val art = minOf(size.height - 52.dp, size.width * 0.36f)
    Row(
        GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(24.dp)
            .background(ImageProvider(R.drawable.widget_glass_container))
            .semantics { contentDescription = describe(v) }.padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(GlanceModifier.clickable(openPlayer(context, v.pkg))) { Art(v, art, 16.dp) }
        Spacer(GlanceModifier.width(18.dp))
        Column(GlanceModifier.defaultWeight().height(art), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(GlanceModifier.fillMaxWidth().clickable(openPlayer(context, v.pkg)), horizontalAlignment = Alignment.CenterHorizontally) {
                Label(v.title, M.White, 18.sp, FontWeight.Bold)
                Label(v.artist, M.Soft, 13.sp)
            }
            Spacer(GlanceModifier.defaultWeight())
            if (v.durationMs > 0) {
                // Sits a little below the midpoint, nearer the controls.
                Spacer(GlanceModifier.height(10.dp))
                LinearProgressIndicator(
                    progress = (v.positionNow().toFloat() / v.durationMs).coerceIn(0f, 1f),
                    modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                    color = fixed(M.White),
                    backgroundColor = fixed(M.Track),
                )
                Spacer(GlanceModifier.height(4.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    Label(clock(v.positionNow()), M.Soft, 11.sp)
                    Spacer(GlanceModifier.defaultWeight())
                    Label("-" + clock(v.durationMs - v.positionNow()), M.Soft, 11.sp)
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            // Spread across the bar's width, so the outer buttons sit under its ends.
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Button(control("previous"), "Previous track") { Icon(R.drawable.ic_music_rewind, M.White, 30.dp) }
                Spacer(GlanceModifier.defaultWeight())
                Button(control("toggle"), if (v.playing) "Pause" else "Play", size = 48.dp) {
                    Icon(if (v.playing) R.drawable.ic_music_pause else R.drawable.ic_music_play, M.White, 38.dp)
                }
                Spacer(GlanceModifier.defaultWeight())
                Button(control("next"), "Next track") { Icon(R.drawable.ic_music_ffwd, M.White, 30.dp) }
            }
        }
    }
}

/** [b] scaled down so its longer side is at most [max] px. */
private fun fit(b: Bitmap, max: Int): Bitmap {
    val scale = max.toFloat() / maxOf(b.width, b.height)
    if (scale >= 1f) return b
    return Bitmap.createScaledBitmap(b, (b.width * scale).toInt().coerceAtLeast(1), (b.height * scale).toInt().coerceAtLeast(1), true)
}
