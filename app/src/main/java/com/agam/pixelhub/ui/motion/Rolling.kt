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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle

/**
 * Text whose characters roll like an odometer when they change: a rising value rolls up,
 * a falling one rolls down. Characters that stay the same don't move. Aligned from the
 * right so "9:59" → "10:00" lines up digit by digit.
 */
@Composable
fun RollingText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    val reduced = LocalReducedMotion.current
    Row(
        modifier.semantics(mergeDescendants = true) { contentDescription = text },
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
                            (slideInVertically(tween(420, easing = Motion.EaseOut)) { h -> dir * h } +
                                fadeIn(tween(300))) togetherWith
                                (slideOutVertically(tween(420, easing = Motion.EaseOut)) { h -> -dir * h } +
                                    fadeOut(tween(200)))
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
