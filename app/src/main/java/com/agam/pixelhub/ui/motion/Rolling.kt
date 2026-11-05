package com.agam.pixelhub.ui.motion

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle

/**
 * Text whose characters roll like an odometer when they change: a rising value rolls up,
 * a falling one rolls down. Characters that stay the same don't move. Aligned from the
 * right so "9:59" → "10:00" lines up digit by digit. When the whole string is wider than
 * the space it gets (large font sizes), it is scaled down to fit instead of losing its tail.
 */
@Composable
fun RollingText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    /** Roll time per character. Keep it short for values that change in quick succession. */
    durationMillis: Int = 300,
) {
    val reduced = LocalReducedMotion.current
    Row(
        modifier
            .semantics(mergeDescendants = true) { contentDescription = text }
            .shrinkToFit(),
        verticalAlignment = Alignment.Bottom,
    ) {
        // Index from the right so lengths can change without everything shifting.
        val chars = text.toList()
        chars.forEachIndexed { i, ch ->
            val fromRight = chars.size - i
            androidx.compose.runtime.key(fromRight) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        if (reduced) {
                            fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                        } else {
                            val up = targetState > initialState
                            val dir = if (up) 1 else -1
                            (slideInVertically(tween(durationMillis, easing = Motion.EaseOut)) { h -> dir * h } +
                                fadeIn(tween(durationMillis * 3 / 4))) togetherWith
                                (slideOutVertically(tween(durationMillis, easing = Motion.EaseOut)) { h -> -dir * h } +
                                    fadeOut(tween(durationMillis / 2)))
                        }
                    },
                    modifier = Modifier.clipToBounds().clearAndSetSemantics { },
                    label = "roll",
                ) { c ->
                    Text(c.toString(), style = style, color = color)
                }
            }
        }
    }
}

/**
 * Measures the content at its natural width; if that is wider than allowed, scales it down
 * uniformly (anchored bottom-left) so every character stays visible. A plain layout, so it
 * works inside intrinsic measurement and lazy lists.
 */
private fun Modifier.shrinkToFit(): Modifier = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
    if (!constraints.hasBoundedWidth || p.width <= constraints.maxWidth) {
        layout(p.width.coerceAtLeast(constraints.minWidth), p.height) { p.place(0, 0) }
    } else {
        val f = constraints.maxWidth.toFloat() / p.width
        val h = (p.height * f).roundToInt().coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(constraints.maxWidth, h) {
            p.placeWithLayer(0, h - p.height) {
                scaleX = f
                scaleY = f
                transformOrigin = TransformOrigin(0f, 1f)
            }
        }
    }
}
