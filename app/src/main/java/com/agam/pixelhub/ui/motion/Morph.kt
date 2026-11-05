package com.agam.pixelhub.ui.motion

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.toPath

/** Unit polygons (radius 1, centred on 0) used for shape morphing across the app. */
object HubShapes {
    val circle: RoundedPolygon = RoundedPolygon.circle(numVertices = 12)
    /** Nine soft scallops: the "pressed" and "thinking" shape. */
    val cookie: RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 9, radius = 1f, innerRadius = 0.82f,
        rounding = CornerRounding(radius = 0.28f, smoothing = 0.6f),
    )
    /** Four soft lobes. */
    val clover: RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 4, radius = 1f, innerRadius = 0.62f,
        rounding = CornerRounding(radius = 0.42f, smoothing = 0.4f),
    )
    /** A rounded square tilted like a gem. */
    val gem: RoundedPolygon = RoundedPolygon(
        numVertices = 4, radius = 1f, rounding = CornerRounding(radius = 0.38f, smoothing = 0.5f),
    )
    /** A softened six-point burst for "done". */
    val burst: RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 6, radius = 1f, innerRadius = 0.74f,
        rounding = CornerRounding(radius = 0.2f, smoothing = 0.8f),
    )
}

/** A [Shape] that is [morph] at [progress], rotated by [rotation] degrees, filling its bounds. */
class MorphShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotation: Float = 0f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress, android.graphics.Path())
        val m = android.graphics.Matrix()
        m.postRotate(rotation)
        m.postScale(size.width / 2f, size.height / 2f)
        m.postTranslate(size.width / 2f, size.height / 2f)
        path.transform(m)
        return Outline.Generic(path.asComposePath())
    }
}

/** A plain polygon as a Shape. */
class PolygonShape(private val polygon: RoundedPolygon, private val rotation: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = polygon.toPath(android.graphics.Path())
        val m = android.graphics.Matrix()
        m.postRotate(rotation)
        m.postScale(size.width / 2f, size.height / 2f)
        m.postTranslate(size.width / 2f, size.height / 2f)
        path.transform(m)
        return Outline.Generic(path.asComposePath())
    }
}

/**
 * The assistant's "thinking" mark: one shape that keeps morphing circle → cookie → clover →
 * gem while slowly turning. Under reduced motion it is a still cookie.
 */
@Composable
fun ThinkingIndicator(color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val reduced = LocalReducedMotion.current
    val sequence = remember { listOf(HubShapes.circle, HubShapes.cookie, HubShapes.clover, HubShapes.gem) }
    val morphs = remember { sequence.indices.map { Morph(sequence[it], sequence[(it + 1) % sequence.size]) } }
    val t = rememberInfiniteTransition(label = "thinking")
    val phase by t.animateFloat(
        0f, sequence.size.toFloat(),
        infiniteRepeatable(tween(sequence.size * 700, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "spin")
    Canvas(modifier.size(size).semantics { contentDescription = "Thinking" }) {
        val path = if (reduced) {
            HubShapes.cookie.toPath(android.graphics.Path())
        } else {
            val i = phase.toInt().coerceIn(0, morphs.lastIndex)
            val local = phase - i
            // Ease within each step so it rests briefly on every shape.
            val eased = Motion.EaseInOut.transform(local.coerceIn(0f, 1f))
            morphs[i].toPath(eased, android.graphics.Path())
        }
        val m = android.graphics.Matrix()
        if (!reduced) m.postRotate(spin)
        m.postScale(this.size.width / 2f, this.size.height / 2f)
        m.postTranslate(this.size.width / 2f, this.size.height / 2f)
        path.transform(m)
        drawPath(path.asComposePath(), color)
    }
}
