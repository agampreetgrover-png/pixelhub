@file:OptIn(ExperimentalLayoutApi::class)

package com.agam.pixelhub.ui.health

import androidx.compose.ui.graphics.toArgb
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.graphics.shapes.toPath
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.container
import com.agam.pixelhub.data.NudgeMetric
import com.agam.pixelhub.data.SettingsStore
import com.agam.pixelhub.health.DailyActivity
import com.agam.pixelhub.health.HealthPrivacyActivity
import com.agam.pixelhub.health.HealthRepository
import com.agam.pixelhub.health.HealthStatus
import com.agam.pixelhub.ui.PrimaryButton
import com.agam.pixelhub.ui.QuietButton
import com.agam.pixelhub.ui.CardRadius
import com.agam.pixelhub.ui.expenses.ProgressTrack
import com.agam.pixelhub.ui.expenses.RowMeta
import com.agam.pixelhub.ui.expenses.RowTitle
import com.agam.pixelhub.ui.hubCard
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.HubShapes
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.RollingText
import com.agam.pixelhub.ui.motion.easeOutBack
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubColors
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

/* ------------------------------------------------------------------------------------------
 * Shared by the Health page, the Home card and the Settings block.
 * ---------------------------------------------------------------------------------------- */

/** Ring goals that aren't user-set (the step goal is, in Settings). */
internal const val EXERCISE_GOAL_MIN = 30L
internal const val ACTIVE_KCAL_GOAL = 400L

internal fun grouped(n: Long): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(n)

internal fun km(meters: Double): String = String.format(Locale.getDefault(), "%.1f", meters / 1000.0)

internal fun kcal(a: DailyActivity): Long = a.activeKcal.roundToLong()

/**
 * Steps are green, exercise minutes sky, active energy the theme accent. In a theme whose
 * accent is itself green (Forest), energy takes the warm danger hue so it doesn't read as steps.
 */
internal fun HubColors.metric(metric: NudgeMetric): Color = when (metric) {
    NudgeMetric.STEPS -> ok
    NudgeMetric.EXERCISE_MINUTES -> sky
    NudgeMetric.ACTIVE_CALORIES -> if (hueDistance(dawn, ok) < 40f) danger else dawn
}

private fun hueDistance(a: Color, b: Color): Float {
    val ha = FloatArray(3).also { android.graphics.Color.colorToHSV(a.toArgb(), it) }[0]
    val hb = FloatArray(3).also { android.graphics.Color.colorToHSV(b.toArgb(), it) }[0]
    val d = kotlin.math.abs(ha - hb)
    return minOf(d, 360f - d)
}

internal val HubColors.okSoft: Color get() = ok.copy(alpha = if (isDark) 0.16f else 0.12f)

/**
 * Whether Health Connect is there and what PixelHub may read. Re-checked every time the
 * screen resumes, because people come back from Health Connect's own settings.
 */
@Stable
internal class HealthAccess(private val repo: HealthRepository, private val scope: CoroutineScope) {
    var status by mutableStateOf(runCatching { repo.status() }.getOrDefault(HealthStatus.UNAVAILABLE))
        private set

    /** Null until the first check answers, so nothing flashes "not connected" on open. */
    var connected by mutableStateOf<Boolean?>(null)
        private set

    var background by mutableStateOf<Boolean?>(null)
        private set

    /** The last permission request came back without the read permissions. */
    var denied by mutableStateOf(false)

    fun refresh() {
        scope.launch {
            val s = runCatching { repo.status() }.getOrDefault(HealthStatus.UNAVAILABLE)
            status = s
            // A failed check (Health Connect briefly unreachable) keeps the last answer instead of
            // flipping a connected user to "Connect".
            val on = if (s == HealthStatus.AVAILABLE) safely { repo.isConnected() } ?: connected else false
            connected = on
            background = if (on == true) safely { repo.canReadInBackground() } ?: background else false
            if (on == true) denied = false
        }
    }

    /** The answer, or null when the check itself failed. */
    private suspend fun safely(block: suspend () -> Boolean): Boolean? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}

@Composable
internal fun rememberHealthAccess(): HealthAccess {
    val repo = LocalContext.current.container.health
    val scope = rememberCoroutineScope()
    val access = remember(repo) { HealthAccess(repo, scope) }
    LifecycleResumeEffect(access) {
        access.refresh()
        onPauseOrDispose { }
    }
    return access
}

/** Today's totals, re-read every minute while on screen. Null while not connected or loading. */
@Composable
internal fun rememberToday(access: HealthAccess): State<DailyActivity?> {
    val repo = LocalContext.current.container.health
    val connected = access.connected == true
    val flow = remember(connected) { if (connected) repo.todayFlow() else flowOf(null) }
    return flow.collectAsStateWithLifecycle(initialValue = null)
}

/**
 * The "Connect" action: Health Connect's permission sheet (reads and background reads in one
 * go), or the Play Store when Health Connect needs installing or updating.
 */
@Composable
internal fun rememberConnectAction(access: HealthAccess): () -> Unit {
    val context = LocalContext.current
    val repo = context.container.health
    val contract = remember(repo) { repo.permissionContract() }
    val launcher = rememberLauncherForActivityResult(contract) { granted ->
        access.denied = !granted.containsAll(repo.readPermissions)
        access.refresh()
    }
    return {
        if (access.status == HealthStatus.AVAILABLE) {
            try {
                launcher.launch(repo.allPermissions)
            } catch (e: ActivityNotFoundException) {
                com.agam.pixelhub.ui.openSafely(context, repo.managePermissionsIntent())
            }
        } else {
            com.agam.pixelhub.ui.openSafely(context, healthConnectStoreIntent())
        }
    }
}

internal fun healthConnectStoreIntent(): Intent =
    Intent(
        Intent.ACTION_VIEW,
        "market://details?id=com.google.android.apps.healthdata&url=healthconnect%3A%2F%2Fonboarding".toUri(),
    ).setPackage("com.android.vending")

internal fun openPrivacy(context: Context) {
    com.agam.pixelhub.ui.openSafely(context, Intent(context, HealthPrivacyActivity::class.java))
}

internal fun manageAccess(context: Context) {
    com.agam.pixelhub.ui.openSafely(context, context.container.health.managePermissionsIntent())
}

/* -------------------------------- Goal pop (shared drawing) ------------------------------- */

/** A unit-radius burst, built once. */
@Composable
internal fun rememberBurstPath(): Path = remember { HubShapes.burst.toPath(android.graphics.Path()).asComposePath() }

/**
 * The goal-reached pop: a ghost burst flies outward and fades while the knob turns into a
 * burst with an easeOutBack overshoot. Starts from the knob's own size, never from zero.
 */
internal suspend fun popGoal(
    pop: Animatable<Float, AnimationVector1D>,
    ghost: Animatable<Float, AnimationVector1D>,
    reduced: Boolean,
    onPop: () -> Unit = {},
) = coroutineScope {
    when {
        pop.value >= 1f -> {
            if (ghost.value < 1f) ghost.animateTo(1f, tween(300))
        }
        reduced -> {
            ghost.snapTo(1f)
            pop.snapTo(1f)
        }
        pop.value > 0f -> {
            // Interrupted half-way (data refreshed): just settle it.
            ghost.snapTo(1f)
            pop.animateTo(1f, Motion.bouncy())
        }
        else -> {
            onPop()
            launch { pop.animateTo(1f, tween(620, easing = LinearEasing)) }
            ghost.snapTo(0f)
            ghost.animateTo(1f, tween(900, easing = Motion.EaseOut))
        }
    }
    Unit
}

internal suspend fun unpopGoal(pop: Animatable<Float, AnimationVector1D>, ghost: Animatable<Float, AnimationVector1D>) {
    ghost.snapTo(1f)
    if (pop.value > 0f) pop.animateTo(0f, tween(180))
}

/** The knob riding the head of an arc; a burst once the goal is reached. */
internal fun DrawScope.drawGoalKnob(
    burst: Path,
    at: Offset,
    stroke: Float,
    color: Color,
    ground: Color,
    pop: Float,
    ghost: Float,
    reduced: Boolean,
) {
    if (ghost < 1f) {
        translate(at.x, at.y) {
            rotate(36f * ghost, pivot = Offset.Zero) {
                scale(stroke * (0.8f + 1.5f * ghost), pivot = Offset.Zero) {
                    drawPath(burst, color.copy(alpha = 0.38f * (1f - ghost)))
                }
            }
        }
    }
    if (pop > 0f) {
        // Opens at the knob's size, overshoots ~6% and settles.
        val s = if (reduced) 1f else 0.72f + 0.28f * easeOutBack(pop, overshoot = 3f)
        translate(at.x, at.y) {
            rotate(-30f * (1f - pop.coerceIn(0f, 1f)), pivot = Offset.Zero) {
                scale(stroke * 0.86f * s, pivot = Offset.Zero) { drawPath(burst, color) }
            }
        }
        drawCircle(ground, radius = stroke * 0.2f, center = at)
    } else {
        drawCircle(color, radius = stroke * 0.62f, center = at)
        drawCircle(ground, radius = stroke * 0.2f, center = at)
    }
}

internal fun pointOn(center: Offset, radius: Float, angleDeg: Float): Offset {
    val a = Math.toRadians(angleDeg.toDouble())
    return Offset(center.x + radius * cos(a).toFloat(), center.y + radius * sin(a).toFloat())
}

/* ----------------------------------------- Home card -------------------------------------- */

/**
 * Today's steps on Home, in the list-card language: a sun well with the walker, "Steps today"
 * and a quiet line under it, then the count rolling like an odometer against the goal with a
 * bar to it. The whole card opens the Health page.
 */
@Composable
fun HealthHomeCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val c = Hub.colors
    val context = LocalContext.current
    val readFailed by context.container.health.readFailed.collectAsStateWithLifecycle()
    val reduced = LocalReducedMotion.current
    val access = rememberHealthAccess()
    val today by rememberToday(access)
    val goal by context.container.settings.stepGoal.collectAsStateWithLifecycle(SettingsStore.DEFAULT_STEP_GOAL)
    val data = today

    val kind = when {
        access.connected == false -> CardKind.CONNECT
        data == null -> CardKind.LOADING
        else -> CardKind.DATA
    }
    val steps = data?.steps ?: 0L
    val reached = data != null && steps >= goal
    val pct = if (goal > 0L) steps * 100 / goal else 0L
    val meta = when (kind) {
        CardKind.LOADING -> if (readFailed) "Couldn't read Health Connect. Retrying." else "Reading Health Connect"
        CardKind.CONNECT -> when (access.status) {
            HealthStatus.AVAILABLE -> "Tap to connect Health Connect"
            HealthStatus.NEEDS_UPDATE -> "Health Connect needs an update"
            HealthStatus.UNAVAILABLE -> "Health Connect isn't available on this phone"
        }
        CardKind.DATA -> "${data?.exerciseMinutes ?: 0L} min exercise • ${grouped(data?.let { kcal(it) } ?: 0L)} kcal"
    }

    Column(
        modifier
            .fillMaxWidth()
            .pressable(onClick = onOpen, pressedScale = 0.97f, onClickLabel = "Open Health", showRipple = false)
            .hubCard(c.raised)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(c.sun), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Outlined.DirectionsWalk,
                    contentDescription = null,
                    tint = c.onSun,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Steps today", style = RowTitle, color = c.ink, maxLines = 1)
                AnimatedContent(
                    targetState = meta,
                    transitionSpec = {
                        fadeIn(tween(if (reduced) 120 else 200)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false)
                    },
                    label = "healthMeta",
                ) { t ->
                    Text(t, style = RowMeta, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.size(36.dp).clip(CircleShape).background(c.well), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
            }
        }
        AnimatedVisibility(
            visible = kind == CardKind.DATA,
            enter = expandVertically(tween(if (reduced) 0 else 240, easing = Motion.EaseOut)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
        ) {
            Column {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingText(grouped(steps), style = HubType.clockMedium, color = c.ink)
                    Text(
                        " / ${grouped(goal)}",
                        style = HubType.small.copy(fontFeatureSettings = "tnum"),
                        color = c.inkMuted,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 7.dp),
                    )
                    Spacer(Modifier.weight(1f))
                    RollingText(
                        "$pct%",
                        style = HubType.clockSmall,
                        color = if (reached) c.ok else c.ink,
                        modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                ProgressTrack(
                    fraction = steps / goal.coerceAtLeast(1L).toFloat(),
                    color = if (reached) c.ok else c.ink,
                    track = c.well,
                    height = 8.dp,
                    drawKey = "home-health",
                )
            }
        }
    }
}


private enum class CardKind { LOADING, CONNECT, DATA }
