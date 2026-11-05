package com.agam.pixelhub.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.RollingText
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** What one card in the deck shows. */
data class CardFace(
    val name: String,
    val color: Color,
    val onColor: Color,
    val icon: ImageVector,
    /** Top right, visible even when the card is at the back ("Weekdays", "Tomorrow"). */
    val corner: String,
    val kicker: String,
    val figure: String,
    val asideLabel: String?,
    val aside: String?,
    val footLabel: String,
    val foot: String,
    val actionLabel: String,
    val onAction: () -> Unit,
    val onOpen: () -> Unit,
)

private val Peek = 54.dp
private val CardHeight = 204.dp
private val NotchWidth = 150.dp
private val NotchHeight = 64.dp
private val NotchGap = 6.dp

/**
 * Two cards stacked like a wallet: the front one is whole, the back one shows its top strip.
 * Tap the strip, or swipe the front card sideways, and they trade places. The front card's
 * bottom-right corner is cut away to seat its action button.
 */
@Composable
fun CardDeck(faces: List<CardFace>, modifier: Modifier = Modifier) {
    require(faces.size == 2)
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var front by rememberSaveable { mutableIntStateOf(0) }
    // 0 = faces[0] in front, 1 = faces[1] in front.
    val swap = remember { Animatable(front.toFloat()) }
    val drag = remember { Animatable(0f) }
    // The card being dragged keeps its offset while it travels to the back, then settles; the card
    // coming forward never inherits it.
    var dragOwner by remember { mutableIntStateOf(front) }
    val swipeAway = with(density) { 90.dp.toPx() }

    fun flip() {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        front = 1 - front
        scope.launch {
            if (reduced) swap.snapTo(front.toFloat())
            else swap.animateTo(front.toFloat(), spring(dampingRatio = 0.78f, stiffness = 380f))
        }
        scope.launch { drag.animateTo(0f, Motion.snappy()) }
    }

    BoxWithConstraints(modifier.fillMaxWidth().height(Peek + CardHeight)) {
        val notch = remember { NotchShape(28.dp, 18.dp, NotchWidth, NotchHeight) }
        faces.forEachIndexed { i, face ->
            // 1 when this card is in front, 0 at the back.
            val frontness = if (i == 0) 1f - swap.value else swap.value
            val isFront = i == front
            // The card passing to the back dips a little mid-flight.
            val dip = sin(PI * swap.value).toFloat()
            Box(
                Modifier
                    .zIndex(if (frontness > 0.5f) 1f else 0f)
                    .offset { androidx.compose.ui.unit.IntOffset(0, (Peek.toPx() * frontness).toInt()) }
                    .graphicsLayer {
                        val s = 1f - 0.04f * dip
                        scaleX = s; scaleY = s
                        if (i == dragOwner && !reduced) {
                            translationX = drag.value
                            rotationZ = drag.value / size.width * 6f
                        }
                    }
                    .fillMaxWidth()
                    .height(CardHeight)
                    .clip(notch)
                    .background(face.color)
                    .then(
                        if (isFront) {
                            Modifier
                                .draggable(
                                    orientation = Orientation.Horizontal,
                                    onDragStarted = { dragOwner = i },
                                    state = rememberDraggableState { d -> scope.launch { drag.snapTo(drag.value + d) } },
                                    onDragStopped = { v ->
                                        if (abs(drag.value) > swipeAway || abs(v) > 1400f) flip()
                                        else drag.animateTo(0f, Motion.snappy())
                                    },
                                )
                                .pressable(onClick = face.onOpen, pressedScale = 0.985f, showRipple = false, onClickLabel = "Open ${face.name}")
                        } else {
                            Modifier.semantics {
                                role = Role.Button
                                contentDescription = "Show ${face.name}"
                                onClick { flip(); true }
                            }
                        },
                    ),
            ) {
                CardBody(face)
            }
            // The back card's visible strip trades places when tapped.
            if (!isFront) {
                Box(
                    Modifier
                        .zIndex(2f)
                        .fillMaxWidth()
                        .height(Peek)
                        .pressable(onClick = { flip() }, pressedScale = 1f, showRipple = false, onClickLabel = "Show ${face.name}"),
                )
            }
        }
        // The front card's action, seated in its notch.
        val f = faces[front]
        Row(
            Modifier
                .zIndex(3f)
                .align(Alignment.BottomEnd)
                .alpha(1f - abs(swap.value - front))
                .width(NotchWidth - NotchGap)
                .height(NotchHeight - NotchGap)
                .clip(CircleShape)
                .background(Hub.colors.chrome)
                // Only once the deck has settled, so a tap mid-flip can't hit the card that is leaving.
                .pressable(onClick = f.onAction, enabled = abs(swap.value - front) < 0.5f, pressedScale = 0.95f, onClickLabel = f.actionLabel)
                .padding(start = 8.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Hub.colors.onChrome), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Hub.colors.chrome, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(f.actionLabel, style = HubType.button, color = Hub.colors.onChrome, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CardBody(face: CardFace) {
    val on = face.onColor
    val soft = on.copy(alpha = 0.78f)
    Column(Modifier.padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().height(Peek), verticalAlignment = Alignment.CenterVertically) {
            Icon(face.icon, contentDescription = null, tint = on, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(face.name, style = HubType.bodyStrong.copy(fontSize = 14.sp), color = on, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text(face.corner, style = HubType.clockSmall.copy(fontSize = 14.sp), color = on, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(face.kicker, style = HubType.small, color = soft, maxLines = 1)
                RollingText(face.figure, HubType.clockLarge, color = on)
            }
            if (face.aside != null) {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 6.dp)) {
                    face.asideLabel?.let { Text(it, style = HubType.small.copy(fontSize = 11.sp), color = soft, maxLines = 1) }
                    Text(face.aside, style = HubType.clockSmall, color = on, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.padding(end = NotchWidth)) {
            Text(face.footLabel, style = HubType.small.copy(fontSize = 11.sp), color = soft, maxLines = 1)
            Text(face.foot, style = HubType.bodyStrong, color = on, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * A rounded card with its bottom-right corner cut away: convex corners where the card meets
 * the cut, a pill-round inner corner that hugs the button seated there.
 */
private class NotchShape(
    private val radius: Dp,
    private val fillet: Dp,
    private val notchWidth: Dp,
    private val notchHeight: Dp,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        with(density) {
            val w = size.width
            val h = size.height
            val r = radius.toPx()
            val f = fillet.toPx()
            val nw = notchWidth.toPx()
            val nh = notchHeight.toPx()
            val inner = nh / 2f
            val p = Path().apply {
                moveTo(r, 0f)
                lineTo(w - r, 0f)
                arcTo(Rect(w - 2 * r, 0f, w, 2 * r), -90f, 90f, false)
                lineTo(w, h - nh - f)
                arcTo(Rect(w - 2 * f, h - nh - 2 * f, w, h - nh), 0f, 90f, false)
                lineTo(w - nw + inner, h - nh)
                arcTo(Rect(w - nw, h - nh, w - nw + 2 * inner, h - nh + 2 * inner), -90f, -90f, false)
                lineTo(w - nw, h - f)
                arcTo(Rect(w - nw - 2 * f, h - 2 * f, w - nw, h), 0f, 90f, false)
                lineTo(r, h)
                arcTo(Rect(0f, h - 2 * r, 2 * r, h), 90f, 90f, false)
                lineTo(0f, r)
                arcTo(Rect(0f, 0f, 2 * r, 2 * r), 180f, 90f, false)
                close()
            }
            return Outline.Generic(p)
        }
    }
}
