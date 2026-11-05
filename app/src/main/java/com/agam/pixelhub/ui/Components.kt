@file:OptIn(ExperimentalMaterial3Api::class)

package com.agam.pixelhub.ui

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.SheetValue
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.format.DateFormat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.core.net.toUri
import androidx.graphics.shapes.Morph
import com.agam.pixelhub.container
import com.agam.pixelhub.scheduling.Days
import com.agam.pixelhub.ui.motion.HubShapes
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.MorphShape
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.motion.quickFade
import com.agam.pixelhub.ui.motion.toggleSpec
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

/** Side margin used by every screen. */
val Gutter = 20.dp

/** Corner radius of every card. */
val CardRadius = 26.dp

/**
 * Height the floating tab bar takes at the bottom of the window (bar, its margin and the
 * system navigation bar). Screens add it to their bottom padding so nothing hides under it.
 * Zero on pages that open above the tabs.
 */
val LocalNavInset = compositionLocalOf { 0.dp }

/**
 * Paints a round tone behind its content only when there is content to sit on, and only when
 * the content doesn't paint its own well ([ownWell]), so a button that shrinks when pressed
 * isn't hidden by a still tone behind it.
 */
private fun Modifier.roundToneIfAny(color: Color, ownWell: State<Boolean>? = null) = drawBehind {
    if (size.width > 0f && size.height > 0f && ownWell?.value != true) {
        drawRoundRect(color, cornerRadius = CornerRadius(size.height / 2))
    }
}

/** Set to true by a header button that paints its own well (see [BackButton]). */
private val LocalOwnWell = compositionLocalOf<MutableState<Boolean>?> { null }

/**
 * A screen with a large title that collapses as you scroll. The back button and actions sit
 * in round wells like the header on Home. The content must be scrollable (LazyColumn or
 * verticalScroll) for the collapse to work; its padding already clears the floating tab bar.
 */
@Composable
fun ScreenScaffold(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = NoFab,
    snackbarHost: SnackbarHostState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val c = Hub.colors
    val navInset = LocalNavInset.current
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navOwnWell = remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = c.ground,
        contentColor = c.ink,
        topBar = {
            LargeTopAppBar(
                title = { Text(title, style = HubType.title, maxLines = 1) },
                navigationIcon = {
                    CompositionLocalProvider(LocalOwnWell provides navOwnWell) {
                        Box(Modifier.padding(start = 8.dp).roundToneIfAny(c.raised, navOwnWell)) { navigationIcon() }
                    }
                },
                actions = {
                    Row(
                        Modifier.padding(end = 12.dp).roundToneIfAny(c.raised),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions,
                    )
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = c.ground,
                    scrolledContainerColor = c.ground,
                    titleContentColor = c.ink,
                    navigationIconContentColor = c.ink,
                    actionIconContentColor = c.ink,
                ),
            )
        },
        bottomBar = bottomBar,
        floatingActionButton = { Box(Modifier.padding(bottom = navInset)) { floatingActionButton() } },
        snackbarHost = {
            if (snackbarHost != null) {
                // With a FAB, Scaffold already stacks the snackbar above it (and the FAB carries the
                // tab-bar inset), so only pad when there is none; otherwise it floated twice as high.
                SnackbarHost(snackbarHost, Modifier.padding(bottom = if (floatingActionButton === NoFab) navInset else 0.dp)) { data ->
                    Snackbar(
                        data,
                        shape = RoundedCornerShape(18.dp),
                        containerColor = c.chrome,
                        contentColor = c.onChrome,
                        actionColor = c.onChrome,
                    )
                }
            }
        },
    ) { inner ->
        val dir = LocalLayoutDirection.current
        content(
            PaddingValues(
                start = inner.calculateStartPadding(dir),
                top = inner.calculateTopPadding(),
                end = inner.calculateEndPadding(dir),
                bottom = inner.calculateBottomPadding() + navInset,
            ),
        )
    }
}

/** A heading inside a screen, with an optional quiet action at the far end ("View all"). */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Hub.colors.ink,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = Gutter, end = Gutter - 8.dp, top = 28.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = HubType.section, color = color, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            // "Hide" ↔ "Show 3": the label changes softly instead of snapping.
            AnimatedContent(targetState = action, transitionSpec = { quickFade() }, label = "sectionAction") { a ->
                Text(
                    a,
                    style = HubType.small,
                    color = Hub.colors.inkMuted,
                    modifier = Modifier.minimumInteractiveComponentSize().clip(CircleShape).pressable(onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** A card: the raised tone with the shared radius. */
fun Modifier.hubCard(color: Color, radius: Dp = CardRadius): Modifier =
    clip(RoundedCornerShape(radius)).background(color)

/**
 * A round icon button on a tone: the header's menu, bell and back buttons, and the wells
 * inside cards. 48dp to the touch.
 */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Color = Hub.colors.raised,
    tint: Color = Hub.colors.ink,
    size: Dp = 48.dp,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier
            .size(size)
            // Press first, so the whole well shrinks, not just the icon inside it.
            .pressable(onClick = onClick, pressedScale = 0.92f, onClickLabel = contentDescription, showRipple = false)
            .clip(CircleShape)
            .background(tone)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** One quick action: an icon in a round well with its name under it. */
@Composable
fun QuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: Color = Hub.colors.well,
    tint: Color = Hub.colors.ink,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .pressable(onClick = onClick, pressedScale = 0.94f, showRipple = false, onClickLabel = label)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(54.dp).clip(CircleShape).background(tone), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = HubType.small.copy(fontWeight = FontWeight.Medium), color = Hub.colors.ink, maxLines = 1)
    }
}

/**
 * A list row as on the reference cards: an icon in a round well, a title with a muted line
 * under it, and a value at the far end.
 */
@Composable
fun HubListRow(
    icon: ImageVector,
    title: String,
    meta: String?,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    trailingColor: Color = Hub.colors.ink,
    iconTint: Color = Hub.colors.ink,
    iconTone: Color = Hub.colors.well,
    onClick: (() -> Unit)? = null,
) {
    val c = Hub.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.pressable(onClick = onClick, pressedScale = 0.98f, showRipple = false) else Modifier)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(iconTone), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = HubType.bodyStrong.copy(fontSize = 14.sp, lineHeight = 20.sp), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (meta != null) {
                Text(meta, style = HubType.small.copy(fontSize = 11.sp, lineHeight = 16.sp), color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, style = HubType.clockSmall.copy(fontSize = 14.sp), color = trailingColor, maxLines = 1)
        }
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(text, style = HubType.body, color = Hub.colors.inkMuted, modifier = modifier.padding(Gutter))
}

/**
 * An empty screen as an invitation: a small dial with one marker that swings into place,
 * a heading and one sentence saying what to do.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    accent: Color,
    modifier: Modifier = Modifier,
    /** One way forward, shown under the text (a QuietButton, say). */
    action: (@Composable () -> Unit)? = null,
) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val swing = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { swing.animateTo(1f, tween(400, easing = Motion.EaseOut)) }
    Column(
        modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(120.dp)) {
            val stroke = 6.dp.toPx()
            val r = size.minDimension / 2 - stroke
            drawCircle(c.raised2, radius = r, style = Stroke(stroke))
            drawArc(
                accent.copy(alpha = 0.5f), startAngle = 90f, sweepAngle = 360f * swing.value * 0.3f,
                useCenter = false, topLeft = Offset(center.x - r, center.y - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round),
            )
            val a = Math.toRadians(90.0 + 360.0 * swing.value * 0.3)
            drawCircle(
                accent, radius = 9.dp.toPx(),
                center = Offset(center.x + r * kotlin.math.cos(a).toFloat(), center.y + r * kotlin.math.sin(a).toFloat()),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = HubType.section, color = c.ink)
        Spacer(Modifier.height(6.dp))
        Text(body, style = HubType.body, color = c.inkMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/**
 * The primary action of a screen: a black pill holding a white disc with the icon, and the
 * action's name. The whole pill presses in a little.
 */
@Composable
fun HubFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = Hub.colors.chrome,
    content: Color = Hub.colors.onChrome,
    label: String = contentDescription,
) {
    Row(
        modifier
            .height(56.dp)
            .pressable(onClick = onClick, pressedScale = 0.95f, onClickLabel = contentDescription, showRipple = false)
            .clip(CircleShape)
            .background(container)
            .semantics { this.contentDescription = contentDescription }
            .padding(start = 8.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(content), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = container, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(label, style = HubType.button, color = content, maxLines = 1)
    }
}

/** Full-width pill button for the main action in a sheet or card. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = Hub.colors.ink,
    content: Color = Hub.colors.ground,
    icon: ImageVector? = null,
) {
    val bg by animateColorAsState(if (enabled) container else Hub.colors.raised2, Motion.toggleSpec(), label = "btnBg")
    val fg by animateColorAsState(if (enabled) content else Hub.colors.inkMuted, Motion.toggleSpec(), label = "btnFg")
    Row(
        modifier
            .heightIn(min = 52.dp)
            .pressable(onClick = onClick, enabled = enabled, showRipple = false)
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = HubType.button, color = fg)
    }
}

/** A secondary pill: tone-step background, ink text. */
@Composable
fun QuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Hub.colors.ink,
    icon: ImageVector? = null,
) {
    Row(
        modifier
            .heightIn(min = 48.dp)
            .pressable(onClick = onClick, enabled = enabled, showRipple = false)
            .clip(CircleShape)
            .background(Hub.colors.raised2)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (enabled) color else Hub.colors.inkMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = HubType.button, color = if (enabled) color else Hub.colors.inkMuted)
    }
}

/** A filled text field on the raised tone; no outline. */
@Composable
fun HubTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 4,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val c = Hub.colors
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        singleLine = singleLine,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        visualTransformation = visualTransformation,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        textStyle = HubType.body,
        shape = RoundedCornerShape(18.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = c.raised2,
            unfocusedContainerColor = c.raised2,
            errorContainerColor = c.dangerSoft,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            errorIndicatorColor = Color.Transparent,
            focusedLabelColor = c.ink,
            unfocusedLabelColor = c.inkMuted,
            cursorColor = c.dawn,
            focusedTextColor = c.ink,
            unfocusedTextColor = c.ink,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * A round check: the ring fills with [accent], then the tick draws itself and the dot pops.
 * Unchecking reverses quickly.
 */
@Composable
fun CheckDot(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    contentDescription: String? = null,
) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val fill by animateFloatAsState(
        if (checked) 1f else 0f,
        if (checked && !reduced) Motion.bouncy() else tween(160),
        label = "fill",
    )
    val tick by animateFloatAsState(
        if (checked) 1f else 0f,
        if (checked) tween(if (reduced) 0 else 320, delayMillis = if (reduced) 0 else 90, easing = Motion.EaseOut) else tween(120),
        label = "tick",
    )
    val toggle = if (onCheckedChange != null) {
        Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
    } else Modifier
    Box(modifier.size(48.dp).then(toggle).semantics { contentDescription?.let { this.contentDescription = it } }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 2.dp.toPx()
            val r = this.size.minDimension / 2
            drawCircle(c.inkMuted.copy(alpha = 0.7f * (1f - fill)), radius = r - stroke / 2, style = Stroke(stroke))
            if (fill > 0f) drawCircle(accent, radius = r * fill.coerceAtMost(1.08f))
            if (tick > 0f) {
                val w = this.size.width
                val p = Path().apply {
                    moveTo(w * 0.28f, w * 0.52f)
                    lineTo(w * 0.44f, w * 0.67f)
                    lineTo(w * 0.73f, w * 0.36f)
                }
                val measure = PathMeasure().apply { setPath(p, false) }
                val seg = Path()
                measure.getSegment(0f, measure.length * tick, seg, true)
                drawPath(seg, c.ground, style = Stroke(2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/**
 * On/off switch: the thumb travels on a spring and grows when on; the track takes [accent].
 */
@Composable
fun HubSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accent: Color = Hub.colors.dawn,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val travel by animateDpAsState(if (checked) 22.dp else 0.dp, if (reduced) snap() else Motion.snappy(), label = "thumb")
    val thumb by animateDpAsState(if (checked) 24.dp else 16.dp, if (reduced) snap() else Motion.bouncy(), label = "thumbSize")
    val track by animateColorAsState(if (checked) accent else c.raised2, Motion.toggleSpec(), label = "track")
    val knob by animateColorAsState(if (checked) c.ground else c.inkMuted, Motion.toggleSpec(), label = "knob")
    Box(
        modifier
            .size(width = 56.dp, height = 48.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { contentDescription?.let { this.contentDescription = it } },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 54.dp, height = 32.dp).clip(CircleShape).background(track)) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = 4.dp + travel + (24.dp - thumb) / 2)
                    .size(thumb)
                    .clip(CircleShape)
                    .background(knob),
            )
        }
    }
}

/**
 * Bottom sheet used for every editor. Rises on the drawer curve; the content sits inside the
 * gutter and above the navigation bar.
 */
@Composable
fun HubSheet(
    onDismiss: () -> Unit,
    /**
     * True while the sheet holds edits that aren't saved. A swipe down, a tap outside or Back
     * then asks "Discard changes?" instead of throwing them away.
     */
    dirty: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Hub.colors
    val isDirty by rememberUpdatedState(dirty)
    var askDiscard by remember { mutableStateOf(false) }
    // Back, the scrim and a swipe all go through confirmValueChange before the sheet hides.
    val state = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            if (value == SheetValue.Hidden && isDirty) {
                askDiscard = true
                false
            } else true
        },
    )
    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            title = { Text("Discard changes?", style = HubType.section, color = c.ink) },
            text = { Text("What you changed here won't be saved.", style = HubType.body, color = c.inkMuted) },
            confirmButton = {
                QuietButton("Discard", color = c.danger, onClick = { askDiscard = false; onDismiss() })
            },
            dismissButton = { QuietButton("Keep editing", color = c.ink, onClick = { askDiscard = false }) },
            containerColor = c.raised,
            shape = RoundedCornerShape(28.dp),
        )
    }
    ModalBottomSheet(
        onDismissRequest = { if (isDirty) askDiscard = true else onDismiss() },
        sheetState = state,
        containerColor = c.raised,
        contentColor = c.ink,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        scrimColor = Color.Black.copy(alpha = 0.45f),
        // Fully open, the sheet stops below the status bar instead of sliding under it.
        modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = Gutter).padding(bottom = 16.dp).navigationBarsPadding(),
            content = content,
        )
    }
}

/** Seven round toggles, Monday first. Each pops when turned on. */
@Composable
fun DaysPicker(mask: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier, accent: Color = Hub.colors.dawn) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        DayOfWeek.entries.forEach { day ->
            val on = Days.contains(mask, day)
            val bg by animateColorAsState(if (on) accent else c.raised2, Motion.toggleSpec(), label = "dayBg")
            val fg by animateColorAsState(if (on) c.ground else c.inkMuted, Motion.toggleSpec(), label = "dayFg")
            val scale by animateFloatAsState(if (on) 1f else 0.9f, if (reduced) snap() else Motion.bouncy(), label = "dayScale")
            Box(
                Modifier
                    // 48dp to the touch around a 40dp circle; seven still fit across a phone.
                    .minimumInteractiveComponentSize()
                    .size(40.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape)
                    .background(bg)
                    .toggleable(value = on, role = Role.Checkbox, onValueChange = { onChange(mask xor Days.bit(day)) })
                    .semantics { contentDescription = day.getDisplayName(JTextStyle.FULL, Locale.getDefault()) },
                contentAlignment = Alignment.Center,
            ) {
                Text(day.getDisplayName(JTextStyle.NARROW, Locale.getDefault()), style = HubType.button, color = fg)
            }
        }
    }
}

/** Material time picker dressed in the PixelHub palette. */
@Composable
fun hubTimePickerColors(accent: Color = Hub.colors.dawn) = Hub.colors.let { c ->
    TimePickerDefaults.colors(
        clockDialColor = c.raised2,
        clockDialSelectedContentColor = c.ground,
        clockDialUnselectedContentColor = c.ink,
        selectorColor = accent,
        containerColor = c.raised,
        periodSelectorBorderColor = c.raised2,
        periodSelectorSelectedContainerColor = accent,
        periodSelectorUnselectedContainerColor = c.raised2,
        periodSelectorSelectedContentColor = c.ground,
        periodSelectorUnselectedContentColor = c.inkMuted,
        timeSelectorSelectedContainerColor = accent,
        timeSelectorUnselectedContainerColor = c.raised2,
        timeSelectorSelectedContentColor = c.ground,
        timeSelectorUnselectedContentColor = c.ink,
    )
}

@Composable
fun TimeSelectDialog(initial: LocalTime, onDismiss: () -> Unit, onConfirm: (LocalTime) -> Unit, accent: Color = Hub.colors.sky) {
    val context = LocalContext.current
    val state = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(context))
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Hub.colors.raised,
        shape = RoundedCornerShape(32.dp),
        confirmButton = { QuietButton("Set time", onClick = { onConfirm(LocalTime.of(state.hour, state.minute)) }, color = accent) },
        dismissButton = { QuietButton("Cancel", onClick = onDismiss, color = Hub.colors.inkMuted) },
        text = { TimePicker(state = state, colors = hubTimePickerColors(accent)) },
    )
}

/** An installed app's icon, or [fallback] in a circle when there is no app. Fades in when loaded. */
@Composable
fun AppIcon(
    packageName: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    fallback: ImageVector = Icons.Outlined.Link,
) {
    val apps = LocalContext.current.container.installedApps
    val cached = remember(packageName) { packageName?.let { apps.cachedIcon(it) } }
    val bitmap = produceState(cached, packageName) {
        value = packageName?.let { withContext(Dispatchers.IO) { apps.icon(it) } }
    }.value
    val reduced = LocalReducedMotion.current
    Box(modifier.size(size)) {
        AnimatedVisibility(
            bitmap != null,
            enter = if (reduced) fadeIn(tween(120)) else fadeIn(tween(220)) + scaleIn(initialScale = 0.9f),
            exit = fadeOut(),
        ) {
            bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.size(size)) }
        }
        if (bitmap == null) {
            Box(Modifier.size(size).clip(CircleShape).background(Hub.colors.raised2), contentAlignment = Alignment.Center) {
                Icon(fallback, contentDescription = null, tint = Hub.colors.ink, modifier = Modifier.size(size * 0.5f))
            }
        }
    }
}

/**
 * Opens a system screen; falls back to this app's info page if that screen doesn't exist or
 * is closed to normal apps (SecurityException), instead of crashing.
 */
fun openSafely(context: Context, intent: Intent) {
    try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        if (e !is ActivityNotFoundException && e !is SecurityException) throw e
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** A small count badge in [color] that pops when it appears. */
@Composable
fun CountBadge(count: Int, color: Color = Hub.colors.dawn, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        count > 0,
        modifier,
        enter = if (reduced) fadeIn(tween(120)) else scaleIn(Motion.bouncy(), initialScale = 0.6f) + fadeIn(tween(120)),
        exit = if (reduced) fadeOut(tween(100)) else scaleOut(tween(120), targetScale = 0.8f) + fadeOut(tween(100)),
    ) {
        // Grows with the count and the font size instead of clipping "12" in a fixed circle.
        Box(
            Modifier
                .heightIn(min = 18.dp)
                .widthIn(min = 18.dp)
                .clip(CircleShape)
                .background(color)
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("$count", style = HubType.small.copy(fontSize = 11.sp, lineHeight = 12.sp), color = Hub.colors.ground, maxLines = 1)
        }
    }
}


/**
 * The back arrow used in screen headers: a round well that presses in like every other
 * button. Inside a [ScreenScaffold] it paints the header's well itself, so the whole well
 * shrinks when pressed.
 */
@Composable
fun BackButton(onBack: () -> Unit) {
    val ownWell = LocalOwnWell.current
    DisposableEffect(ownWell) {
        ownWell?.value = true
        onDispose { ownWell?.value = false }
    }
    CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack, tone = Hub.colors.raised)
}

/**
 * The height of a fixed-size colour card (the hero cards and the Home deck) at the phone's
 * font size. Their text grows with the font scale and their padding doesn't, so only the
 * text's share ([textShare]) is scaled; at the default size this is just [base].
 */
@Composable
fun heroHeight(base: Dp = 204.dp, textShare: Dp = 130.dp): Dp {
    val scale = LocalDensity.current.fontScale
    return if (scale <= 1f) base else base + textShare * (scale - 1f)
}

/**
 * The width of a pill seated in a card's notch that fits the longest of [labels] at the
 * phone's font size: [chrome] is everything in the pill besides the text (padding, the round
 * plus well, the gap). Never narrower than [min], never more than 55% of the screen.
 */
@Composable
fun notchWidthFor(labels: List<String>, chrome: Dp, min: Dp, style: TextStyle = HubType.button): Dp {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val screen = LocalConfiguration.current.screenWidthDp.dp
    val text = labels.maxOfOrNull { label ->
        with(density) { measurer.measure(label, style, maxLines = 1).size.width.toDp() }
    } ?: 0.dp
    return (text + chrome + 2.dp).coerceIn(min, maxOf(min, screen * 0.55f))
}

/** ScreenScaffold's default: no floating action button. Compared by identity. */
private val NoFab: @Composable () -> Unit = {}
