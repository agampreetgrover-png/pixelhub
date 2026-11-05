package com.agam.pixelhub.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
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
}

private fun fixed(c: Color): ColorProvider = DayNightColor(c, c)

/** The player's own extra buttons (shuffle, repeat, add to library), drawn with its own icons. */
private data class PlayerButton(val action: String, val name: String, val icon: Icon)

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
    val shuffle: PlayerButton?,
    val repeat: PlayerButton?,
    val like: PlayerButton?,
    val muted: Boolean,
) {
    fun positionNow(): Long {
        val now = SystemClock.elapsedRealtime()
        if (!playing) return positionMs.coerceAtLeast(0)
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
        positionMs = positionNow(), positionAt = SystemClock.elapsedRealtime(), speed = speed,
        shuffle = shuffle, repeat = repeat, like = like, muted = muted,
    )
}

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
            positionAt = SystemClock.elapsedRealtime(), speed = 1f, shuffle = null, repeat = null, like = null, muted = false,
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
                    delay(5_000L)
                }
            }
        }
    }

private fun viewOf(context: Context, s: NowPlayingState): MusicView {
    val customs = s.controller.playbackState?.customActions.orEmpty().map {
        PlayerButton(it.action, it.name.toString(), Icon.createWithResource(s.pkg, it.icon))
    }
    fun find(vararg words: String) = customs.firstOrNull { b -> words.any { b.name.contains(it, ignoreCase = true) } }
    val icon = runCatching { context.packageManager.getApplicationIcon(s.pkg).toBitmap(144, 144) }.getOrNull()
    val audio = context.getSystemService(AudioManager::class.java)
    return MusicView(
        title = s.title, artist = s.artist, appLabel = s.appLabel, pkg = s.pkg, art = s.art, appIcon = icon,
        playing = s.playing, durationMs = s.durationMs, positionMs = s.positionMs, positionAt = s.positionAt,
        speed = s.speed,
        shuffle = find("shuffle"),
        repeat = find("repeat"),
        like = find("like", "heart", "save", "add", "library"),
        muted = audio.isStreamMute(AudioManager.STREAM_MUSIC),
    )
}

// ---- Actions -------------------------------------------------------------------------------

private val Kind = ActionParameters.Key<String>("kind")
private val Custom = ActionParameters.Key<String>("custom")

/** Every music button: play/pause, next, previous, a player button, or mute. */
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
                    "custom" -> parameters[Custom]?.let { s.controller.transportControls.sendCustomAction(it, null) }
                }
            }
        }
        // The session's own change redraws it too; this makes a tap feel instant.
        MusicWidget().update(context, glanceId)
    }
}

private fun control(kind: String): Action = actionRunCallback<MusicControlAction>(actionParametersOf(Kind to kind))
private fun custom(b: PlayerButton): Action =
    actionRunCallback<MusicControlAction>(actionParametersOf(Kind to "custom", Custom to b.action))

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
                    .padding(start = disc / 2 + 14.dp, end = 14.dp, top = 12.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(GlanceModifier.defaultWeight().clickable(openPlayer(context, v.pkg))) {
                        Label(v.title, M.CardInk, 15.sp, FontWeight.Medium)
                        Label(v.artist, M.CardMuted, 12.sp)
                    }
                    Button(control("mute"), if (v.muted) "Unmute" else "Mute", size = 36.dp) {
                        Icon(if (v.muted) R.drawable.ic_music_mute else R.drawable.ic_music_volume, M.CardMuted, 20.dp)
                    }
                }
                if (v.durationMs > 0) {
                    Spacer(GlanceModifier.height(8.dp))
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

/** Image 1 (4×2 and up): dark with the album's colour glowing up from the bottom. */
@Composable
private fun Player(context: Context, v: MusicView, size: DpSize) {
    Box(GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(28.dp).semantics { contentDescription = describe(v) }) {
        Image(
            ImageProvider(glow(context, size, v.art)),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = GlanceModifier.fillMaxSize(),
        )
        // One block in a fixed rhythm, centred so the space above and below is equal.
        Column(GlanceModifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            // The song, large and bold beside the art; it wraps to a second line rather than being cut.
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.clickable(openPlayer(context, v.pkg))) { Art(v, 56.dp, 12.dp) }
                Spacer(GlanceModifier.width(14.dp))
                Column(GlanceModifier.defaultWeight().clickable(openPlayer(context, v.pkg))) {
                    Label(v.title, M.White, 18.sp, FontWeight.Bold, maxLines = 2)
                    Label(v.artist, M.Grey, 13.sp)
                }
                v.like?.let { PlayerIcon(it, 26.dp) }
            }
            Spacer(GlanceModifier.height(26.dp))
            if (v.durationMs > 0) {
                LinearProgressIndicator(
                    progress = (v.positionNow().toFloat() / v.durationMs).coerceIn(0f, 1f),
                    modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                    color = fixed(M.White),
                    backgroundColor = fixed(M.Track),
                )
                Spacer(GlanceModifier.height(4.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    Label(clock(v.positionNow()), M.Grey, 11.sp)
                    Spacer(GlanceModifier.defaultWeight())
                    Label("-" + clock(v.durationMs - v.positionNow()), M.Grey, 11.sp)
                }
            }
            Spacer(GlanceModifier.height(6.dp))
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Slot { v.shuffle?.let { PlayerIcon(it, 22.dp) } }
                Spacer(GlanceModifier.defaultWeight())
                Button(control("previous"), "Previous track") { Icon(R.drawable.ic_music_prev, M.White, 28.dp) }
                Spacer(GlanceModifier.defaultWeight())
                Button(control("toggle"), if (v.playing) "Pause" else "Play", size = 52.dp) {
                    Box(GlanceModifier.size(48.dp).cornerRadius(24.dp).background(fixed(M.White)), contentAlignment = Alignment.Center) {
                        Icon(if (v.playing) R.drawable.ic_music_pause else R.drawable.ic_music_play, M.Black, 26.dp)
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                Button(control("next"), "Next track") { Icon(R.drawable.ic_music_next, M.White, 28.dp) }
                Spacer(GlanceModifier.defaultWeight())
                Slot { v.repeat?.let { PlayerIcon(it, 22.dp) } }
            }
        }
    }
}

/** A fixed-width place for an optional player button, so the main controls stay centred. */
@Composable
private fun Slot(content: @Composable () -> Unit) {
    Box(GlanceModifier.size(44.dp), contentAlignment = Alignment.Center) { content() }
}

/** One of the player's own buttons, with the player's own icon (it shows its state, e.g. shuffle on). */
@Composable
private fun PlayerIcon(b: PlayerButton, size: Dp) {
    Button(custom(b), b.name) {
        // Tinted white to read on the dark player; the icon's shape still shows its state.
        Image(
            ImageProvider(b.icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(fixed(M.White.copy(alpha = 0.85f))),
            modifier = GlanceModifier.size(size),
        )
    }
}

/**
 * The background of the big player: black at the top, the album's main colour glowing up from
 * the bottom. The colour is darkened until white text on it keeps 4.5:1.
 */
private fun glow(context: Context, size: DpSize, art: Bitmap?): Bitmap {
    val d = context.resources.displayMetrics.density
    val w = (size.width.value * d).toInt().coerceAtLeast(1)
    val h = (size.height.value * d).toInt().coerceAtLeast(1)
    var tint = art?.let { Color(Bitmap.createScaledBitmap(it, 1, 1, true).getPixel(0, 0)) } ?: Color(0xFF3A3A40)
    tint = lerp(tint, M.Black, 0.35f)
    while (tint.luminance() > 0.18f) tint = lerp(tint, M.Black, 0.15f)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val paint = Paint().apply {
        shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(M.Black.toArgb(), M.Black.toArgb(), tint.toArgb()), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
    }
    Canvas(bitmap).drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    return bitmap
}
