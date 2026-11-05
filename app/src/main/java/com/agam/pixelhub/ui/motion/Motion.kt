package com.agam.pixelhub.ui.motion

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** True when the user turned on "Remove animations". Travel is dropped; content still appears. */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * One motion vocabulary for the whole app.
 * Entrances use [EaseOut]; things moving on screen use springs so they can be interrupted.
 */
object Motion {
    /** Strong ease-out: starts fast, settles softly. Entrances and reveals. */
    val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    /** For things travelling across the screen. */
    val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
    /** iOS-like sheet curve. */
    val Drawer = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

    /** Press feedback, toggles, indicators. */
    fun <T> snappy() = spring<T>(dampingRatio = 0.75f, stiffness = 700f)
    /** Playful pops: markers, badges, checkmarks. */
    fun <T> bouncy() = spring<T>(dampingRatio = 0.5f, stiffness = 380f)
    /** Large, calm movements. */
    fun <T> gentle() = spring<T>(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow)

    const val STAGGER_MS = 55L
    const val ENTER_MS = 520
}

/**
 * Click with a spring press: scales to [pressedScale] while held and gives a light haptic.
 * Use on anything tappable that isn't a stock Material button.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    showRipple: Boolean = true,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduced = LocalReducedMotion.current
    val scale by animateFloatAsState(
        if (pressed && !reduced) pressedScale else 1f,
        animationSpec = if (pressed) spring(dampingRatio = 0.9f, stiffness = 1400f) else Motion.snappy(),
        label = "press",
    )
    val haptics = LocalHapticFeedback.current
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .combinedClickable(
            interactionSource = source,
            indication = if (showRipple) ripple() else null,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onLongClick = onLongClick?.let { lc -> { haptics.performHapticFeedback(HapticFeedbackType.LongPress); lc() } },
            onClick = onClick,
        )
}

/**
 * Remembers which items of a screen have already made their entrance, so scrolling back
 * or recomposing never replays it. Create one per screen with [rememberStagger].
 */
class Stagger internal constructor() {
    internal val shown = mutableSetOf<Any>()
    internal val bornAt = SystemClock.uptimeMillis()
}

@Composable
fun rememberStagger(): Stagger = remember { Stagger() }

/**
 * Rises 24dp and fades in, once per [key]. Items in the first screenful are staggered by
 * [index]; anything arriving later (scrolled into view, newly added) enters without delay.
 */
fun Modifier.staggerIn(stagger: Stagger, key: Any, index: Int = 0, distance: Float = 24f): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val already = key in stagger.shown
    val progress = remember(key) { Animatable(if (already) 1f else 0f) }
    val px = with(LocalDensity.current) { distance.dp.toPx() }
    LaunchedEffect(key) {
        if (progress.value < 1f) {
            stagger.shown += key
            val sinceBirth = SystemClock.uptimeMillis() - stagger.bornAt
            if (sinceBirth < 700) delay(index.coerceIn(0, 10) * Motion.STAGGER_MS)
            progress.animateTo(1f, tween(if (reduced) 180 else Motion.ENTER_MS, easing = Motion.EaseOut))
        }
    }
    graphicsLayer {
        val p = progress.value
        alpha = p
        if (!reduced) translationY = (1f - p) * px
    }
}

/** Overshooting ease for pops drawn by hand on a Canvas (0..1 in, ~1.1 peak, 1 out). */
fun easeOutBack(t: Float, overshoot: Float = 1.7f): Float {
    val x = t.coerceIn(0f, 1f) - 1f
    return 1f + (overshoot + 1f) * x * x * x + overshoot * x * x
}
