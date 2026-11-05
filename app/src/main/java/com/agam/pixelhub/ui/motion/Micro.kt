package com.agam.pixelhub.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize

/*
 * Small, shared motion recipes. Each one answers a user action or a state change; none of
 * them decorate. Every recipe collapses to a plain fade (or nothing) under reduced motion.
 */

/** Keys whose draw-in has already played in this process. */
private val drawnIn = mutableSetOf<Any>()

/**
 * A bar, ring or meter that grows in the first time it is shown in this process, then simply
 * follows [target] (animating changes, never replaying the entrance when scrolled back into
 * view or revisited).
 */
@Composable
fun rememberDrawIn(
    key: Any,
    target: Float,
    spec: AnimationSpec<Float> = tween(420, easing = Motion.EaseOut),
): State<Float> {
    val reduced = LocalReducedMotion.current
    val first = key !in drawnIn
    val anim = remember(key) { Animatable(if (first && !reduced) 0f else target) }
    LaunchedEffect(key, target) {
        drawnIn += key
        if (reduced) anim.snapTo(target) else anim.animateTo(target, if (first) spec else Motion.snappy())
    }
    return anim.asState()
}

/**
 * A small pop (1 → [peak] → 1) when [selected] turns true because of the user. It never
 * plays on first composition, so a chip that opens already selected stays still.
 */
fun Modifier.popOnSelect(selected: Boolean, peak: Float = 1.06f): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val scale = remember { Animatable(1f) }
    val seen = remember { BooleanArray(1) }
    LaunchedEffect(selected) {
        if (!seen[0]) { seen[0] = true; return@LaunchedEffect }
        if (selected && !reduced) {
            scale.animateTo(peak, tween(90, easing = Motion.EaseOut))
            scale.animateTo(1f, Motion.bouncy())
        }
    }
    graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/**
 * A short label that changes meaning ("Left" → "Over by", "To go" → "Reached"): the new
 * text rises in as the old one leaves upward. A plain crossfade under reduced motion.
 */
fun <S> AnimatedContentTransitionScope<S>.labelSwap(reduced: Boolean): ContentTransform =
    if (reduced) {
        fadeIn(tween(120)) togetherWith fadeOut(tween(80))
    } else {
        (slideInVertically(tween(220, easing = Motion.EaseOut)) { it / 2 } + fadeIn(tween(180))) togetherWith
            (slideOutVertically(tween(140)) { -it / 2 } + fadeOut(tween(100)))
    }.using(SizeTransform(clip = false))

/** Content replaced in place (a list of chips for another mode, a different figure). */
fun <S> AnimatedContentTransitionScope<S>.quickFade(): ContentTransform =
    fadeIn(tween(160, easing = Motion.EaseOut)) togetherWith fadeOut(tween(100))

/** Small things that appear on demand (a send button, a clear button, a badge). */
fun popEnter(reduced: Boolean): EnterTransition =
    if (reduced) fadeIn(tween(120)) else fadeIn(tween(160)) + scaleIn(Motion.snappy(), initialScale = 0.85f)

fun popExit(): ExitTransition = fadeOut(tween(110)) + scaleOut(tween(140), targetScale = 0.9f)

/** A row, notice or block that opens inside a card, pushing what is below it down. */
fun revealEnter(reduced: Boolean): EnterTransition =
    if (reduced) fadeIn(tween(120))
    else fadeIn(tween(200, delayMillis = 40)) + expandVertically(tween(240, easing = Motion.EaseOut))

fun revealExit(reduced: Boolean): ExitTransition =
    if (reduced) fadeOut(tween(80))
    else fadeOut(tween(100)) + shrinkVertically(tween(180, easing = Motion.EaseOut))

/** Size changes for animateContentSize: calm spring, or instant under reduced motion. */
fun sizeSpec(reduced: Boolean): FiniteAnimationSpec<IntSize> = if (reduced) snap() else Motion.gentle()

/** Text or a trailing control whose state changes in place ("Allow" → a check, "Save" → "Saved"). */
@Composable
fun <S> StatusSwap(
    target: S,
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.CenterEnd,
    content: @Composable (S) -> Unit,
) {
    val reduced = LocalReducedMotion.current
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        contentAlignment = alignment,
        transitionSpec = {
            if (reduced) fadeIn(tween(120)) togetherWith fadeOut(tween(80))
            else (fadeIn(tween(160, easing = Motion.EaseOut)) + scaleIn(tween(160, easing = Motion.EaseOut), initialScale = 0.92f)) togetherWith
                fadeOut(tween(100))
        },
        label = "status",
    ) { content(it) }
}

/** Stays true for [holdMs] after [value] turns false, so a "done" state is seen before it goes. */
@Composable
fun rememberHeldTrue(value: Boolean, holdMs: Long = 900): Boolean {
    var held by remember { mutableStateOf(value) }
    LaunchedEffect(value) {
        if (value) held = true else { delay(holdMs); held = false }
    }
    return value || held
}

/**
 * The one list-item spec: a short fade in (staggerIn may add the rise), a quicker fade out,
 * and a firm spring for neighbours closing the gap. Placement snaps under reduced motion.
 */
@Composable
fun LazyItemScope.hubAnimateItem(modifier: Modifier = Modifier, fadeIn: Boolean = true): Modifier {
    val reduced = LocalReducedMotion.current
    return modifier.animateItem(
        fadeInSpec = if (fadeIn) tween(200, easing = Motion.EaseOut) else null,
        placementSpec = if (reduced) null else spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold),
        fadeOutSpec = tween(120),
    )
}

/** A corner radius that eases when a row becomes the first or last of its run. */
@Composable
fun animatedRadius(target: Dp): Dp {
    val reduced = LocalReducedMotion.current
    // snappy() overshoots a little; a negative corner size is invalid.
    return animateDpAsState(target, if (reduced) snap() else Motion.snappy(), label = "radius").value.coerceAtLeast(0.dp)
}

/** Colour change of a toggle, tab or chip: one duration everywhere. */
fun <T> Motion.toggleSpec(): AnimationSpec<T> = tween(180, easing = EaseOut)
