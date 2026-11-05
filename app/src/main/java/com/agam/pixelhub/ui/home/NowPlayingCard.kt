package com.agam.pixelhub.ui.home

import android.content.Context
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.media.NowPlaying
import com.agam.pixelhub.media.NowPlayingState
import com.agam.pixelhub.ui.CircleIconButton
import com.agam.pixelhub.ui.QuietButton
import com.agam.pixelhub.ui.hubCard
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.motion.revealEnter
import com.agam.pixelhub.ui.motion.revealExit
import com.agam.pixelhub.ui.motion.sizeSpec
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

private val TitleStyle = HubType.bodyStrong.copy(fontSize = 14.sp, lineHeight = 20.sp)
private val MetaStyle = HubType.small.copy(fontSize = 11.sp, lineHeight = 16.sp, fontFeatureSettings = "tnum")

/**
 * What Spotify (or any other player) is playing, with play/pause and skip. Asks for
 * notification access first, since that is what lets an app see other apps' media sessions;
 * hides itself while nothing is playing.
 */
@Composable
fun NowPlayingCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val reduced = LocalReducedMotion.current
    var access by remember { mutableStateOf(NowPlaying.hasAccess(context)) }
    LifecycleResumeEffect(Unit) {
        access = NowPlaying.hasAccess(context)
        onPauseOrDispose { }
    }
    val flow = remember(access) { if (access) NowPlaying.flow(context) else flowOf(null) }
    // Starts from the last state seen this process, so returning to Home doesn't re-expand the card.
    val state by flow.collectAsStateWithLifecycle(initialValue = if (access) NowPlaying.last else null)
    // "Not now" on the connect prompt; remembered so declining access isn't asked on every visit.
    var dismissed by remember { mutableStateOf(connectDismissed(context)) }

    // Keep the last track while the card folds away, so it doesn't blank mid-animation.
    var shown by remember { mutableStateOf(state) }
    if (state != null) shown = state

    AnimatedVisibility(
        visible = (!access && !dismissed) || state != null,
        enter = revealEnter(reduced),
        exit = revealExit(reduced),
    ) {
        Box(modifier.animateContentSize(sizeSpec(reduced))) {
            val s = shown
            if (!access) {
                ConnectRow(onNotNow = { dismissed = true; setConnectDismissed(context) })
            } else if (s != null) {
                Playing(s)
            }
        }
    }
}

private const val PREFS = "now_playing"
private const val KEY_DISMISSED = "connect_dismissed"

private fun connectDismissed(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DISMISSED, false)

private fun setConnectDismissed(context: Context) =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DISMISSED, true).apply()

@Composable
private fun ConnectRow(onNotNow: () -> Unit) {
    val c = Hub.colors
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().hubCard(c.raised).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(c.sky), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = c.onSky, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Now playing", style = TitleStyle, color = c.ink, maxLines = 1)
            Text(
                "Show Spotify and other music here",
                style = MetaStyle, color = c.inkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onNotNow) {
            Icon(Icons.Outlined.Close, contentDescription = "Not now", tint = c.inkMuted, modifier = Modifier.size(20.dp))
        }
        QuietButton("Connect", onClick = { NowPlaying.openAccessSettings(context) })
    }
}

@Composable
private fun Playing(s: NowPlayingState) {
    val c = Hub.colors
    val context = LocalContext.current

    // Re-reads the clock once a second while playing, so the bar and times move.
    var elapsed by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(s.playing, s.positionAt) {
        elapsed = SystemClock.elapsedRealtime()
        while (s.playing) {
            delay(1000L - (s.positionNow(SystemClock.elapsedRealtime()) % 1000L).coerceIn(0, 999) + 20)
            elapsed = SystemClock.elapsedRealtime()
        }
    }
    val position = s.positionNow(elapsed)

    Column(
        Modifier
            .fillMaxWidth()
            .pressable(
                onClick = { NowPlaying.openPlayer(context, s) },
                pressedScale = 0.97f,
                onClickLabel = "Open ${s.appLabel}",
                showRipple = false,
            )
            .hubCard(c.raised)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val art = s.art
            val image = remember(art) { art?.asImageBitmap() }
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(c.well),
                contentAlignment = Alignment.Center,
            ) {
                if (image != null) {
                    Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp))
                } else {
                    Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(24.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    s.appLabel.uppercase(),
                    style = MetaStyle.copy(letterSpacing = 0.6.sp), color = c.inkMuted, maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(s.title, style = TitleStyle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (s.artist.isNotBlank()) {
                    Text(s.artist, style = MetaStyle, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        if (s.durationMs > 0) {
            Spacer(Modifier.height(12.dp))
            val fraction = (position.toFloat() / s.durationMs).coerceIn(0f, 1f)
            val fill = c.sky
            val track = c.well
            Canvas(Modifier.fillMaxWidth().height(4.dp).clearAndSetSemantics { }) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = r)
                if (fraction > 0f) drawRoundRect(fill, size = Size(size.width * fraction, size.height), cornerRadius = r)
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(clock(position), style = MetaStyle, color = c.inkMuted)
                Text(clock(s.durationMs), style = MetaStyle, color = c.inkMuted)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = if (s.durationMs > 0) 4.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleIconButton(Icons.Outlined.SkipPrevious, "Previous track", onClick = { NowPlaying.previous(s) }, tone = c.well)
            CircleIconButton(
                if (s.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                if (s.playing) "Pause" else "Play",
                onClick = { NowPlaying.playPause(s) },
                tone = c.sky,
                tint = c.onSky,
                size = 56.dp,
                iconSize = 26.dp,
            )
            CircleIconButton(Icons.Outlined.SkipNext, "Next track", onClick = { NowPlaying.next(s) }, tone = c.well)
        }
    }
}

private fun clock(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val sec = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
