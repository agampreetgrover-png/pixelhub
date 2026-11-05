@file:OptIn(ExperimentalLayoutApi::class)

package com.agam.pixelhub.ui.health

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.container
import com.agam.pixelhub.data.HealthNudge
import com.agam.pixelhub.data.NudgeMetric
import com.agam.pixelhub.data.SettingsStore
import com.agam.pixelhub.health.DailyActivity
import com.agam.pixelhub.health.DayActivity
import com.agam.pixelhub.health.HealthStatus
import com.agam.pixelhub.health.NudgeMath
import com.agam.pixelhub.scheduling.Days
import com.agam.pixelhub.ui.CardRadius
import com.agam.pixelhub.ui.DaysPicker
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.Gutter
import com.agam.pixelhub.ui.HubListRow
import com.agam.pixelhub.ui.HubSheet
import com.agam.pixelhub.ui.HubSwitch
import com.agam.pixelhub.ui.HubTextField
import com.agam.pixelhub.ui.PrimaryButton
import com.agam.pixelhub.ui.QuickAction
import com.agam.pixelhub.ui.QuietButton
import com.agam.pixelhub.ui.ScreenScaffold
import com.agam.pixelhub.ui.TimeSelectDialog
import com.agam.pixelhub.ui.expenses.HeroPeek
import com.agam.pixelhub.ui.expenses.NotchedHero
import com.agam.pixelhub.ui.expenses.PillDisc
import com.agam.pixelhub.ui.expenses.ProgressTrack
import com.agam.pixelhub.ui.expenses.RowMeta
import com.agam.pixelhub.ui.expenses.RowTitle
import com.agam.pixelhub.ui.hubCard
import com.agam.pixelhub.ui.home.Dot
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.RollingText
import com.agam.pixelhub.ui.motion.hubAnimateItem
import com.agam.pixelhub.ui.motion.labelSwap
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.motion.rememberDrawIn
import com.agam.pixelhub.ui.motion.rememberStagger
import com.agam.pixelhub.ui.motion.revealEnter
import com.agam.pixelhub.ui.motion.revealExit
import com.agam.pixelhub.ui.motion.sizeSpec
import com.agam.pixelhub.ui.motion.staggerIn
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle as JTextStyle
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val GOAL_STEP = 500L
private const val GOAL_MIN = 1_000L
private const val GOAL_MAX = 50_000L

/**
 * The Health page: today's steps on a sun card with the step goal in its notch, quick
 * actions, today's other figures, the last week as bars, and nudges that notify you when
 * you're behind. Everything comes from Health Connect; every control is a direct one.
 */
@Composable
fun HealthScreen() {
    val context = LocalContext.current
    val c = context.container
    val colors = Hub.colors
    val access = rememberHealthAccess()
    val connect = rememberConnectAction(access)
    val today by rememberToday(access)
    val stagger = rememberStagger()

    // The goal answers every tap at once; the save waits until the taps stop.
    val storedGoal by c.settings.stepGoal.collectAsStateWithLifecycle(SettingsStore.DEFAULT_STEP_GOAL)
    var draftGoal by remember { mutableStateOf<Long?>(null) }
    val goal = draftGoal ?: storedGoal
    LaunchedEffect(draftGoal) {
        val d = draftGoal ?: return@LaunchedEffect
        delay(650)
        c.appScope.launch { c.settings.setStepGoal(d) }
    }
    LaunchedEffect(storedGoal) { if (draftGoal == storedGoal) draftGoal = null }
    val pendingGoal by rememberUpdatedState(draftGoal)
    DisposableEffect(Unit) {
        onDispose { pendingGoal?.let { d -> c.appScope.launch { c.settings.setStepGoal(d) } } }
    }

    val connected = access.connected == true
    val todaySteps = today?.steps
    // Bumped by "Try again" when the week couldn't be read.
    var historyTry by remember { mutableIntStateOf(0) }
    val history by produceState<List<DayActivity>?>(null, connected, todaySteps, historyTry) {
        if (!connected) return@produceState
        value = try {
            c.health.history(7)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }
    val nudges by c.healthNudges.all.collectAsStateWithLifecycle<List<HealthNudge>?>(initialValue = null)
    var creating by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }

    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reduced = LocalReducedMotion.current

    ScreenScaffold(
        title = "Health",
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = list,
            contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp),
        ) {
            when (access.connected) {
                null -> item(key = "checking") {
                    Spacer(hubAnimateItem(Modifier.fillMaxWidth().height(360.dp)))
                }
                false -> item(key = "connect") {
                    ConnectHero(
                        status = access.status,
                        denied = access.denied,
                        onConnect = connect,
                        onManage = { manageAccess(context) },
                        onPrivacy = { openPrivacy(context) },
                        modifier = hubAnimateItem(fadeIn = false).staggerIn(stagger, "connect", 0),
                    )
                }
                true -> {
                    item(key = "hero") {
                        StepsHero(
                            activity = today,
                            goal = goal,
                            onGoal = { draftGoal = it },
                            modifier = hubAnimateItem(fadeIn = false).staggerIn(stagger, "hero", 0).padding(horizontal = Gutter),
                        )
                    }
                    item(key = "actions") {
                        HealthActions(
                            onNudge = { creating = true },
                            onWeek = {
                                scope.launch {
                                    // The week card is the fourth item: hero, actions, today, week.
                                    if (reduced) list.scrollToItem(3) else list.animateScrollToItem(3)
                                }
                            },
                            onAccess = { manageAccess(context) },
                            onPrivacy = { openPrivacy(context) },
                            modifier = hubAnimateItem(fadeIn = false).staggerIn(stagger, "actions", 1),
                        )
                    }
                    item(key = "today") {
                        TodayCard(
                            today,
                            hubAnimateItem(fadeIn = false).staggerIn(stagger, "today", 2),
                            onOpenHealth = { manageAccess(context) },
                        )
                    }
                    item(key = "week") {
                        WeekSection(
                            history,
                            today,
                            goal,
                            hubAnimateItem(fadeIn = false).staggerIn(stagger, "week", 3),
                            onRetry = { historyTry++ },
                            onOpenHealth = { manageAccess(context) },
                        )
                    }
                    item(key = "nudges") {
                        NudgesCard(
                            nudges = nudges,
                            backgroundDenied = access.background == false,
                            onToggle = { n, on -> c.appScope.launch { c.healthNudges.save(n.copy(enabled = on)) } },
                            onEdit = { editingId = it.id },
                            onAdd = { creating = true },
                            onManage = { manageAccess(context) },
                            modifier = hubAnimateItem(fadeIn = false).staggerIn(stagger, "nudges", 4),
                        )
                    }
                    item(key = "source") {
                        Text(
                            "Activity comes from Health Connect. Fitbit and Google Fit only show up here with Health Connect sync turned on in their settings.",
                            style = HubType.small,
                            color = colors.inkMuted,
                            modifier = hubAnimateItem(fadeIn = false)
                                .staggerIn(stagger, "source", 5)
                                .padding(horizontal = Gutter + 4.dp)
                                .padding(top = 20.dp),
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        NudgeEditor(
            initial = HealthNudge(metric = NudgeMetric.STEPS, goal = 6_000, hour = 18, minute = 0),
            isNew = true,
            onDismiss = { creating = false },
            onSave = { n ->
                creating = false
                c.appScope.launch { c.healthNudges.save(n) }
            },
            onDelete = null,
        )
    }
    nudges?.firstOrNull { it.id == editingId }?.let { n ->
        key(n.id) {
            NudgeEditor(
                initial = n,
                isNew = false,
                onDismiss = { editingId = null },
                onSave = { updated ->
                    editingId = null
                    c.appScope.launch { c.healthNudges.save(updated) }
                },
                onDelete = {
                    editingId = null
                    c.appScope.launch { c.healthNudges.delete(n) }
                },
            )
        }
    }
}

/* ---------------------------------------- Not connected ----------------------------------- */

/**
 * What the page will show and one button to connect. The rings sweep in as a preview; when
 * Health Connect needs installing or updating, the copy and the button say so instead.
 */
@Composable
private fun ConnectHero(
    status: HealthStatus,
    denied: Boolean,
    onConnect: () -> Unit,
    onManage: () -> Unit,
    onPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val (title, body, button) = when (status) {
        HealthStatus.AVAILABLE -> Triple(
            "Your day in three rings",
            "PixelHub reads today's steps, exercise and active energy from Health Connect, where Fitbit and Google Fit save them. It never writes or uploads anything.",
            "Connect Health Connect",
        )
        HealthStatus.NEEDS_UPDATE -> Triple(
            "Health Connect needs an update",
            "Update it from the Play Store, then come back here to connect.",
            "Update Health Connect",
        )
        HealthStatus.UNAVAILABLE -> Triple(
            "Health Connect isn't available",
            "This phone can't open Health Connect right now. Install or update it from the Play Store.",
            "Open Play Store",
        )
    }
    Column(
        modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ActivityRings(
            rings = listOf(RingSpec(0.72f, c.ok), RingSpec(0.5f, c.sky), RingSpec(0.84f, c.dawn)),
            loaded = true,
            celebrate = false,
            description = "Preview: rings for steps, exercise minutes and active energy",
            modifier = Modifier.widthIn(max = 250.dp).fillMaxWidth().aspectRatio(1f),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.DirectionsWalk,
                contentDescription = null,
                tint = c.ok,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(28.dp))
        Text(title, style = HubType.title.copy(fontSize = 26.sp, lineHeight = 30.sp), color = c.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(body, style = HubType.body, color = c.inkMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Legend(c.ok, "Steps")
            Spacer(Modifier.width(18.dp))
            Legend(c.sky, "Exercise")
            Spacer(Modifier.width(18.dp))
            Legend(c.dawn, "Energy")
        }
        Spacer(Modifier.height(28.dp))
        PrimaryButton(
            text = button,
            onClick = onConnect,
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedVisibility(
            visible = denied && status == HealthStatus.AVAILABLE,
            enter = revealEnter(reduced),
            exit = revealExit(reduced),
        ) {
            Column(
                Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(CardRadius))
                    .background(c.dangerSoft)
                    .padding(18.dp),
            ) {
                Text("Access wasn't granted", style = HubType.bodyStrong, color = c.ink)
                Text(
                    "PixelHub needs steps, distance, exercise and active energy. You can allow them in Health Connect.",
                    style = HubType.small,
                    color = c.inkMuted,
                )
                Spacer(Modifier.height(12.dp))
                QuietButton("Manage access", onClick = onManage, icon = Icons.AutoMirrored.Outlined.OpenInNew)
            }
        }
        Spacer(Modifier.height(10.dp))
        QuietButton(
            "How PixelHub uses health data",
            onClick = onPrivacy,
            color = c.inkMuted,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color, size = 8.dp)
        Spacer(Modifier.width(6.dp))
        Text(label, style = HubType.small, color = Hub.colors.inkMuted)
    }
}

/* ------------------------------------------ The dial -------------------------------------- */

private data class RingSpec(val fraction: Float, val color: Color)

/** Whether a dial has made its entrance; later changes glide instead of sweeping from zero. */
private class IntroFlag { var played = false }

/**
 * Concentric activity rings in the Home dial's language: thick round-capped arcs on a
 * raised2 track, each with a knob riding its head. On entry the arcs sweep in one after
 * another; later updates glide on a spring. A ring that reaches its goal pops a burst.
 */
@Composable
private fun ActivityRings(
    rings: List<RingSpec>,
    loaded: Boolean,
    description: String,
    modifier: Modifier = Modifier,
    celebrate: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val count = rings.size
    val sweeps = remember(count) { List(count) { Animatable(0f) } }
    val pops = remember(count) { List(count) { Animatable(0f) } }
    val ghosts = remember(count) { List(count) { Animatable(1f) } }
    val intro = remember { IntroFlag() }
    val burst = rememberBurstPath()
    val targets = rings.map { it.fraction }

    LaunchedEffect(targets, loaded) {
        if (!loaded) return@LaunchedEffect
        val first = !intro.played
        intro.played = true
        targets.forEachIndexed { i, raw ->
            launch {
                val target = raw.coerceIn(0f, 1f)
                when {
                    reduced -> sweeps[i].snapTo(target)
                    first -> {
                        delay(80L + i * 70L)
                        sweeps[i].animateTo(target, tween(300, easing = Motion.EaseOut))
                    }
                    else -> sweeps[i].animateTo(target, Motion.gentle())
                }
                if (celebrate && raw >= 1f) {
                    popGoal(pops[i], ghosts[i], reduced, onPop = { haptics.performHapticFeedback(HapticFeedbackType.Confirm) })
                } else {
                    unpopGoal(pops[i], ghosts[i])
                }
            }
        }
    }

    Box(modifier.semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = (size.minDimension * 0.055f).coerceIn(12.dp.toPx(), 20.dp.toPx())
            val gap = stroke * 0.38f
            val outer = size.minDimension / 2f - stroke
            for (i in 0 until count) {
                val r = outer - i * (stroke + gap)
                drawCircle(c.raised2, radius = r, center = center, style = Stroke(stroke))
                val s = sweeps[i].value
                if (s > 0.002f) {
                    drawArc(
                        rings[i].color, startAngle = -90f, sweepAngle = 360f * s, useCenter = false,
                        topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            // Knobs last so a burst sits over the neighbouring ring.
            for (i in 0 until count) {
                val s = sweeps[i].value
                if (s <= 0.002f) continue
                val r = outer - i * (stroke + gap)
                drawGoalKnob(
                    burst, pointOn(center, r, -90f + 360f * s), stroke, rings[i].color, c.ground,
                    pops[i].value, ghosts[i].value, reduced,
                )
            }
        }
        content()
    }
}

/* ------------------------------------------ The hero -------------------------------------- */

private val GoalPillWidth = 176.dp

/**
 * Today's steps on the sun card: the count, what's left to the goal, a bar to it, and when
 * Health Connect last answered. The step goal sits in the black pill in the notch: tap − or +
 * to move it by 500, hold to keep going.
 */
@Composable
private fun StepsHero(activity: DailyActivity?, goal: Long, onGoal: (Long) -> Unit, modifier: Modifier = Modifier) {
    val c = Hub.colors
    val context = LocalContext.current
    val readFailed by context.container.health.readFailed.collectAsStateWithLifecycle()
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val on = c.onSun
    // Small type on the sun card: near-full ink by day, full at night.
    val soft = on.copy(alpha = if (c.isDark) 1f else 0.9f)
    val steps = activity?.steps ?: 0L
    val loaded = activity != null
    val reached = loaded && steps >= goal
    val pct = if (goal > 0L) (steps * 100 / goal) else 0L
    // One confirm when the goal is crossed while the page is open; never for the first reading.
    var lastReached by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(reached, loaded) {
        if (!loaded) return@LaunchedEffect
        val before = lastReached
        lastReached = reached
        if (before == false && reached) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
    }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(20_000)
            value = System.currentTimeMillis()
        }
    }
    NotchedHero(
        color = c.sun,
        notchWidth = GoalPillWidth,
        modifier = modifier,
        pill = { GoalPill(goal = goal, onChange = onGoal) },
    ) {
        Row(Modifier.fillMaxWidth().height(HeroPeek), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.DirectionsWalk, contentDescription = null, tint = on, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("Steps today", style = RowTitle, color = on, maxLines = 1)
            Spacer(Modifier.weight(1f))
            RollingText("$pct%", style = HubType.clockSmall.copy(fontSize = 14.sp), color = on)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Walked", style = HubType.small, color = soft, maxLines = 1)
                RollingText(grouped(steps), style = HubType.clockLarge, color = on)
            }
            AnimatedContent(
                targetState = reached,
                transitionSpec = { labelSwap(reduced) },
                contentAlignment = Alignment.BottomEnd,
                label = "goalState",
                modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
            ) { r ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (r) "Goal" else "To go", style = RowMeta, color = soft, maxLines = 1)
                    if (r) {
                        Text("Reached", style = HubType.clockSmall, color = on, maxLines = 1)
                    } else {
                        RollingText(grouped((goal - steps).coerceAtLeast(0L)), style = HubType.clockSmall, color = on)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        ProgressTrack(
            fraction = steps / goal.coerceAtLeast(1L).toFloat(),
            color = on,
            track = on.copy(alpha = 0.14f),
            drawKey = "health-steps",
        )
        Spacer(Modifier.height(10.dp))
        Column(Modifier.padding(end = GoalPillWidth)) {
            Text("Health Connect", style = RowMeta, color = soft, maxLines = 1)
            Text(
                activity?.let { updatedText(context, it.asOf, now) }
                    ?: if (readFailed) "Couldn't read it. Trying again in a minute." else "Reading…",
                style = HubType.small.copy(fontWeight = FontWeight.Medium),
                color = on,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** − 8,000 + in the black pill. Holding a disc repeats; the goal saves once the taps stop. */
@Composable
private fun RowScope.GoalPill(goal: Long, onChange: (Long) -> Unit) {
    val c = Hub.colors
    PillDisc(
        icon = Icons.Outlined.Remove,
        description = "Lower the goal by 500 steps",
        enabled = goal > GOAL_MIN,
        onStep = { onChange((goal - GOAL_STEP).coerceIn(GOAL_MIN, GOAL_MAX)) },
        repeat = true,
    )
    Column(
        Modifier.weight(1f).semantics(mergeDescendants = true) { contentDescription = "Daily goal ${grouped(goal)} steps" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Holding a disc steps every 80ms, so the roll has to finish fast or the digits smear.
        RollingText(
            grouped(goal),
            style = HubType.button.copy(fontFeatureSettings = "tnum"),
            color = c.onChrome,
            durationMillis = 120,
        )
        Text("goal", style = RowMeta, color = c.onChrome.copy(alpha = 0.75f), maxLines = 1)
    }
    PillDisc(
        icon = Icons.Outlined.Add,
        description = "Raise the goal by 500 steps",
        enabled = goal < GOAL_MAX,
        onStep = { onChange((goal + GOAL_STEP).coerceIn(GOAL_MIN, GOAL_MAX)) },
        repeat = true,
    )
}

/** New nudge, jump to the week, Health Connect access, privacy: icons in round wells. */
@Composable
private fun HealthActions(
    onNudge: () -> Unit,
    onWeek: () -> Unit,
    onAccess: () -> Unit,
    onPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .padding(top = 16.dp)
            .hubCard(Hub.colors.raised)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        QuickAction(Icons.Outlined.NotificationsActive, "Nudge", onClick = onNudge, modifier = Modifier.weight(1f))
        QuickAction(Icons.Outlined.BarChart, "Week", onClick = onWeek, modifier = Modifier.weight(1f))
        QuickAction(Icons.AutoMirrored.Outlined.OpenInNew, "Access", onClick = onAccess, modifier = Modifier.weight(1f))
        QuickAction(Icons.Outlined.Lock, "Privacy", onClick = onPrivacy, modifier = Modifier.weight(1f))
    }
}

/* ---------------------------------------- Today's figures --------------------------------- */

/** Exercise, energy and distance as list rows, each in its own coloured well. */
@Composable
private fun TodayCard(activity: DailyActivity?, modifier: Modifier = Modifier, onOpenHealth: () -> Unit = {}) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val minutes = activity?.exerciseMinutes ?: 0L
    val energy = activity?.let { kcal(it) } ?: 0L
    val allZero = activity != null && activity.steps == 0L && minutes == 0L && energy == 0L
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .padding(top = 16.dp)
            .hubCard(c.raised)
            .padding(top = 14.dp, bottom = 8.dp),
    ) {
        Text("Today", style = HubType.section, color = c.ink, modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 4.dp))
        HubListRow(
            icon = Icons.Outlined.Timer,
            title = "Exercise",
            meta = "Goal $EXERCISE_GOAL_MIN min",
            modifier = Modifier.padding(horizontal = 6.dp),
            trailing = "$minutes min",
            iconTint = c.sky,
            iconTone = c.skySoft,
        )
        HubListRow(
            icon = Icons.Outlined.LocalFireDepartment,
            title = "Active energy",
            meta = "Goal ${grouped(ACTIVE_KCAL_GOAL)} kcal",
            modifier = Modifier.padding(horizontal = 6.dp),
            trailing = "${grouped(energy)} kcal",
            iconTint = c.dawn,
            iconTone = c.dawnSoft,
        )
        HubListRow(
            icon = Icons.Outlined.Route,
            title = "Distance",
            meta = "Walking and running",
            modifier = Modifier.padding(horizontal = 6.dp),
            trailing = "${km(activity?.distanceMeters ?: 0.0)} km",
            iconTint = c.ok,
            iconTone = c.okSoft,
        )
        AnimatedVisibility(
            visible = allZero,
            enter = revealEnter(reduced),
            exit = revealExit(reduced),
        ) {
            Row(
                Modifier
                    .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.well)
                    .padding(14.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Outlined.Sync, contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Nothing recorded yet today", style = RowTitle, color = c.ink)
                    Text(
                        "If you track with Fitbit or Google Fit, turn on Health Connect sync in that app. Until then, everything here stays at 0.",
                        style = HubType.small,
                        color = c.inkMuted,
                    )
                    Spacer(Modifier.height(4.dp))
                    QuietButton("Open Health Connect", onClick = onOpenHealth, icon = Icons.AutoMirrored.Outlined.OpenInNew)
                }
            }
        }
    }
}

private fun updatedText(context: Context, asOf: Instant, now: Long): String {
    val minutes = ((now - asOf.toEpochMilli()) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "Updated just now"
        minutes < 60 -> "Updated $minutes min ago"
        else -> "Updated at ${Fmt.time(context, asOf.atZone(ZoneId.systemDefault()).toLocalTime())}"
    }
}

/* ------------------------------------------ The week -------------------------------------- */

@Composable
private fun WeekSection(
    history: List<DayActivity>?,
    today: DailyActivity?,
    goal: Long,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onOpenHealth: () -> Unit = {},
) {
    val c = Hub.colors
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .padding(top = 16.dp)
            .hubCard(c.raised)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text("Last 7 days", style = HubType.section, color = c.ink)
        Spacer(Modifier.height(8.dp))
        when {
            history == null -> Spacer(Modifier.fillMaxWidth().height(300.dp))
            history.isEmpty() -> {
                Text(
                    "Couldn't read the last week from Health Connect.",
                    style = HubType.body,
                    color = c.inkMuted,
                )
                Spacer(Modifier.height(4.dp))
                QuietButton("Try again", onClick = onRetry, icon = Icons.Outlined.Sync)
            }
            else -> {
                // Today's bar follows the live reading, which can be newer than the history query.
                val todayDate = LocalDate.now()
                val days = history.map { d ->
                    if (d.date == todayDate && today != null) {
                        d.copy(
                            steps = today.steps,
                            distanceMeters = today.distanceMeters,
                            activeKcal = today.activeKcal,
                            exerciseMinutes = today.exerciseMinutes,
                        )
                    } else {
                        d
                    }
                }
                val average = days.sumOf { it.steps } / days.size
                val met = days.count { it.steps >= goal }
                Row(verticalAlignment = Alignment.Bottom) {
                    RollingText(grouped(average), style = HubType.clockMedium, color = c.ink)
                    Text(
                        "steps a day on average",
                        style = HubType.body,
                        color = c.inkMuted,
                        modifier = Modifier.padding(start = 8.dp, bottom = 5.dp),
                    )
                }
                Text(
                    when (met) {
                        0 -> "Goal not reached this week yet"
                        1 -> "Goal reached on 1 day of ${days.size}"
                        else -> "Goal reached on $met days of ${days.size}"
                    },
                    style = HubType.small,
                    color = if (met > 0) c.ok else c.inkMuted,
                )
                Spacer(Modifier.height(8.dp))
                WeekChart(days, goal)
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 18.dp, height = 8.dp).clip(CircleShape).background(c.well))
                    Spacer(Modifier.width(8.dp))
                    Text("Your goal", style = HubType.small, color = c.inkMuted)
                    Spacer(Modifier.width(18.dp))
                    Dot(c.ok, size = 8.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("Today", style = HubType.small, color = c.inkMuted)
                }
                if (days.all { it.steps == 0L }) {
                    Text(
                        "No steps recorded this week. Fitbit and Google Fit only appear here with Health Connect sync turned on.",
                        style = HubType.small,
                        color = c.inkMuted,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    QuietButton("Open Health Connect", onClick = onOpenHealth, icon = Icons.AutoMirrored.Outlined.OpenInNew)
                }
            }
        }
    }
}

/**
 * Seven rounded bars that rise from the baseline one after another, once per process. The goal
 * is a soft band behind them. Tapping a bar (or its day) slides the label over it and fades the
 * figures to that day; the chosen bar widens a touch.
 */
@Composable
private fun WeekChart(days: List<DayActivity>, goal: Long) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val n = days.size
    val todayIndex = days.indexOfLast { it.date == LocalDate.now() }.let { if (it < 0) n - 1 else it }
    var selected by remember(n) { mutableIntStateOf(todayIndex) }
    val sel = selected.coerceIn(0, n - 1)
    val select = { i: Int ->
        if (i != selected) {
            selected = i
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }

    // The bars rise once per process; later visits and scroll-backs show them in place.
    val rises = List(n) { i ->
        rememberDrawIn("health-week-$i", 1f, tween(280, delayMillis = 120 + i * 40, easing = Motion.EaseOut))
    }
    val peak = maxOf(goal * 1.2f, (days.maxOfOrNull { it.steps } ?: 0L) * 1.1f, 1f)
    val top by animateFloatAsState(peak, Motion.gentle(), label = "chartTop")
    val alphas = List(n) { i ->
        animateFloatAsState(if (i == sel || i == todayIndex) 1f else 0.55f, tween(240), label = "barAlpha")
    }
    val widths = List(n) { i ->
        animateFloatAsState(if (i == sel && !reduced) 1.16f else 1f, Motion.snappy(), label = "barWidth")
    }
    val anchor by animateFloatAsState(
        (sel + 0.5f) / n,
        if (reduced) tween(0) else Motion.snappy(),
        label = "labelX",
    )

    Column(Modifier.fillMaxWidth()) {
        AnchoredAt(fraction = anchor, modifier = Modifier.fillMaxWidth().height(84.dp)) {
            BarLabel(days[sel], isToday = sel == todayIndex)
        }
        Spacer(Modifier.height(8.dp))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .pointerInput(n) {
                    detectTapGestures { o ->
                        select((o.x / (size.width / n.toFloat())).toInt().coerceIn(0, n - 1))
                    }
                }
                .clearAndSetSemantics { },
        ) {
            val slot = size.width / n
            val barW = minOf(slot * 0.52f, 30.dp.toPx())
            val h = size.height
            val band = 12.dp.toPx()
            val goalY = h - (goal / top) * h
            drawRoundRect(
                c.well,
                topLeft = Offset(0f, goalY - band / 2f),
                size = Size(size.width, band),
                cornerRadius = CornerRadius(band / 2f),
            )
            days.forEachIndexed { i, d ->
                val cx = slot * (i + 0.5f)
                val w = barW * widths[i].value
                val rise = rises[i].value
                if (d.steps <= 0L) {
                    // A resting stub so empty days still have a place.
                    val r = 3.dp.toPx()
                    drawCircle(c.inkMuted.copy(alpha = 0.3f), radius = r, center = Offset(cx, h - r))
                    return@forEachIndexed
                }
                val full = maxOf((d.steps / top) * h, w)
                val bh = (full * rise).coerceAtLeast(0f)
                if (bh < 1f) return@forEachIndexed
                drawRoundRect(
                    c.ok.copy(alpha = alphas[i].value),
                    topLeft = Offset(cx - w / 2f, h - bh),
                    size = Size(w, bh),
                    cornerRadius = CornerRadius(minOf(w, bh) / 2f),
                )
                // Days that met the goal carry a small mark in the cap, like the dial's knob.
                if (d.steps >= goal) {
                    val markAlpha = ((rise - 0.85f) / 0.15f).coerceIn(0f, 1f)
                    if (markAlpha > 0f) {
                        drawCircle(c.ground.copy(alpha = markAlpha), radius = w * 0.15f, center = Offset(cx, h - bh + w / 2f))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            days.forEachIndexed { i, d ->
                val color by animateColorAsState(if (i == sel) c.ink else c.inkMuted, tween(200), label = "dayInitial")
                val name = d.date.dayOfWeek.getDisplayName(JTextStyle.FULL, Locale.getDefault())
                Box(
                    Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(CircleShape)
                        .pressable(
                            onClick = { select(i) },
                            pressedScale = 0.9f,
                            showRipple = false,
                            onClickLabel = "Show $name",
                        )
                        .semantics(mergeDescendants = true) {
                            contentDescription = "$name, ${grouped(d.steps)} steps"
                            this.selected = i == sel
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        d.date.dayOfWeek.getDisplayName(JTextStyle.NARROW, Locale.getDefault()),
                        style = if (i == todayIndex) HubType.button else HubType.small,
                        color = color,
                    )
                }
            }
        }
    }
}

/** Lays its content out centred on [fraction] of the width, kept inside the edges, at the bottom. */
@Composable
private fun AnchoredAt(fraction: Float, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeables = measurables.map { it.measure(loose) }
        val w = constraints.maxWidth
        val h = if (constraints.hasBoundedHeight) constraints.maxHeight else placeables.maxOfOrNull { it.height } ?: 0
        layout(w, h) {
            placeables.forEach { p ->
                val x = (w * fraction - p.width / 2f).roundToInt().coerceIn(0, (w - p.width).coerceAtLeast(0))
                p.place(x, h - p.height)
            }
        }
    }
}

/** A day's figures swapped in place: the label's slide to the bar is the motion, so this only fades. */
private fun <S> AnimatedContentTransitionScope<S>.barFade(): ContentTransform =
    fadeIn(tween(120)) togetherWith fadeOut(tween(80)) using SizeTransform(clip = false)

@Composable
private fun BarLabel(day: DayActivity, isToday: Boolean) {
    val c = Hub.colors
    val name = if (isToday) "Today" else day.date.dayOfWeek.getDisplayName(JTextStyle.FULL, Locale.getDefault())
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(c.well)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(targetState = name, transitionSpec = { barFade() }, label = "barDay") { t ->
            Text(t, style = HubType.small, color = c.inkMuted)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedContent(targetState = grouped(day.steps), transitionSpec = { barFade() }, label = "barSteps") { t ->
                Text(t, style = HubType.clockSmall, color = c.ink, maxLines = 1)
            }
            Text(" steps", style = HubType.small, color = c.inkMuted, modifier = Modifier.padding(bottom = 2.dp))
        }
        AnimatedContent(
            targetState = "${day.exerciseMinutes} min, ${grouped(day.activeKcal.roundToLong())} kcal",
            transitionSpec = { barFade() },
            label = "barFigures",
        ) { t ->
            Text(t, style = HubType.small.copy(fontFeatureSettings = "tnum"), color = c.inkMuted, maxLines = 1)
        }
    }
}

/* ------------------------------------------- Nudges --------------------------------------- */

private fun metricIcon(metric: NudgeMetric): ImageVector = when (metric) {
    NudgeMetric.STEPS -> Icons.AutoMirrored.Outlined.DirectionsWalk
    NudgeMetric.EXERCISE_MINUTES -> Icons.Outlined.Timer
    NudgeMetric.ACTIVE_CALORIES -> Icons.Outlined.LocalFireDepartment
}

private fun metricLabel(metric: NudgeMetric): String = when (metric) {
    NudgeMetric.STEPS -> "Steps"
    NudgeMetric.EXERCISE_MINUTES -> "Exercise"
    NudgeMetric.ACTIVE_CALORIES -> "Energy"
}

private fun defaultGoal(metric: NudgeMetric): Long = when (metric) {
    NudgeMetric.STEPS -> 6_000
    NudgeMetric.EXERCISE_MINUTES -> 20
    NudgeMetric.ACTIVE_CALORIES -> 250
}

private fun daysCaption(mask: Int): String =
    if ((mask and Days.ALL) == 0) "Every day" else Days.label(mask)

/**
 * The nudges in one card: a line on what they do, a warning when background reading is off,
 * a row per nudge with its switch, and "Add" at the top (or a first row to add one).
 */
@Composable
private fun NudgesCard(
    nudges: List<HealthNudge>?,
    backgroundDenied: Boolean,
    onToggle: (HealthNudge, Boolean) -> Unit,
    onEdit: (HealthNudge) -> Unit,
    onAdd: () -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val list = nudges.orEmpty()
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .padding(top = 16.dp)
            // Saving or deleting a nudge, or the notice arriving, resizes the card calmly.
            .animateContentSize(sizeSpec(reduced))
            .hubCard(c.raised)
            .padding(top = 10.dp, bottom = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Nudges", style = HubType.section, color = c.ink, modifier = Modifier.weight(1f))
            Text(
                "Add",
                style = HubType.small.copy(fontWeight = FontWeight.Medium),
                color = c.inkMuted,
                modifier = Modifier
                    .clip(CircleShape)
                    .pressable(onClick = onAdd, onClickLabel = "Add nudge")
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 10.dp, vertical = 11.dp),
            )
        }
        Text(
            "A notification at a time you choose, sent only if you're behind by then.",
            style = HubType.small,
            color = c.inkMuted,
            modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 6.dp),
        )
        AnimatedVisibility(
            visible = list.isNotEmpty() && backgroundDenied,
            enter = revealEnter(reduced),
            exit = revealExit(reduced),
        ) {
            BackgroundNotice(onManage = onManage, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
        list.forEach { n ->
            key(n.id) {
                NudgeRow(
                    nudge = n,
                    onToggle = { on -> onToggle(n, on) },
                    onClick = { onEdit(n) },
                )
            }
        }
        if (nudges != null && list.isEmpty()) {
            HubListRow(
                icon = Icons.Outlined.Add,
                title = "Add your first nudge",
                meta = "Steps under 6,000 at 18:00 is a good start",
                modifier = Modifier.padding(horizontal = 6.dp),
                onClick = onAdd,
            )
        }
    }
}

/**
 * One nudge: a well in the metric's colour, "Steps under 6,000 at 18:00" with its days
 * beneath, and a switch. Switching off dims the row and greys the well.
 */
@Composable
private fun NudgeRow(nudge: HealthNudge, onToggle: (Boolean) -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = Hub.colors
    var on by remember(nudge.enabled) { mutableStateOf(nudge.enabled) }
    val accent = c.metric(nudge.metric)
    val text = NudgeMath.describe(nudge, Fmt.time(context, LocalTime.of(nudge.hour, nudge.minute)))
    val textColor by animateColorAsState(if (on) c.ink else c.inkMuted, tween(200), label = "nudgeText")
    val wellBg by animateColorAsState(
        if (on) accent.copy(alpha = if (c.isDark) 0.18f else 0.13f) else c.well,
        tween(200),
        label = "nudgeWell",
    )
    val wellTint by animateColorAsState(if (on) accent else c.inkMuted, tween(200), label = "nudgeTint")

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .pressable(onClick = onClick, pressedScale = 0.98f, showRipple = false, onClickLabel = "Edit nudge")
            .clip(RoundedCornerShape(18.dp))
            .padding(start = 12.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(wellBg), contentAlignment = Alignment.Center) {
            Icon(metricIcon(nudge.metric), contentDescription = null, tint = wellTint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = RowTitle, color = textColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(daysCaption(nudge.daysMask), style = RowMeta, color = c.inkMuted, maxLines = 1)
        }
        HubSwitch(
            checked = on,
            onCheckedChange = { on = it; onToggle(it) },
            accent = c.ink,
            contentDescription = text,
        )
    }
}

@Composable
private fun BackgroundNotice(onManage: () -> Unit, modifier: Modifier = Modifier) {
    val c = Hub.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.dangerSoft)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = c.danger, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Nudges can't check on time", style = RowTitle, color = c.ink)
                Text(
                    "Allow PixelHub to read activity in the background in Health Connect. Without it, nudges stay quiet.",
                    style = HubType.small,
                    color = c.inkMuted,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        QuietButton("Manage access", onClick = onManage, icon = Icons.AutoMirrored.Outlined.OpenInNew)
    }
}

/**
 * Create or edit a nudge in a sheet: the metric as three pills with a sliding indicator that
 * takes the metric's colour, the goal, the time and the days, and a live sentence that says
 * exactly what will happen.
 */
@Composable
private fun NudgeEditor(
    initial: HealthNudge,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (HealthNudge) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    var metric by rememberSaveable { mutableStateOf(initial.metric) }
    var goalText by rememberSaveable { mutableStateOf(initial.goal.toString()) }
    var goalTouched by rememberSaveable { mutableStateOf(!isNew) }
    var time by rememberSaveable { mutableStateOf(LocalTime.of(initial.hour, initial.minute)) }
    var mask by rememberSaveable { mutableIntStateOf(initial.daysMask and Days.ALL) }
    var picking by rememberSaveable { mutableStateOf(false) }
    // A goal no day could reach would fire the nudge every time (a day has 1,440 minutes).
    val maxGoal = when (metric) {
        NudgeMetric.STEPS -> 100_000L
        NudgeMetric.EXERCISE_MINUTES -> 600L
        NudgeMetric.ACTIVE_CALORIES -> 5_000L
    }
    val goal = goalText.toLongOrNull()?.takeIf { it in 1..maxGoal }
    val tooBig = (goalText.toLongOrNull() ?: 0L) > maxGoal
    val timeText = Fmt.time(context, time)
    val dirty = metric != initial.metric || goalText != initial.goal.toString() ||
        time != LocalTime.of(initial.hour, initial.minute) || mask != (initial.daysMask and Days.ALL)

    HubSheet(onDismiss = onDismiss, dirty = dirty) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(
                if (isNew) "New nudge" else "Edit nudge",
                style = HubType.section,
                color = c.ink,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "PixelHub checks at this time and notifies you only if you're behind.",
                style = HubType.body,
                color = c.inkMuted,
            )
            Spacer(Modifier.height(20.dp))
            MetricChoices(
                selected = metric,
                onSelect = { m ->
                    metric = m
                    if (!goalTouched) goalText = defaultGoal(m).toString()
                },
            )
            Spacer(Modifier.height(16.dp))
            HubTextField(
                value = goalText,
                onValueChange = { v ->
                    goalText = v.filter { it.isDigit() }.take(6)
                    goalTouched = true
                },
                label = "Notify if under",
                supportingText = if (tooBig) "Up to ${grouped(maxGoal)} ${NudgeMath.unit(metric)}"
                else NudgeMath.unit(metric).replaceFirstChar { it.uppercase() },
                isError = goalText.isNotEmpty() && goal == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressable(onClick = { picking = true }, pressedScale = 0.97f, onClickLabel = "Change time", showRipple = false)
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.raised2)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Check at", style = HubType.bodyStrong, color = c.ink)
                    Text("Tap to change", style = HubType.small, color = c.inkMuted)
                }
                RollingText(timeText, style = HubType.clockMedium, color = c.ok)
            }
            Column(Modifier.padding(top = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Days", style = HubType.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
                    AnimatedContent(
                        targetState = daysCaption(mask),
                        transitionSpec = {
                            if (reduced) {
                                fadeIn(tween(120)) togetherWith fadeOut(tween(80)) using SizeTransform(clip = false)
                            } else {
                                (fadeIn(tween(200, delayMillis = 40)) + slideInVertically(tween(260, easing = Motion.EaseOut)) { it / 2 }) togetherWith
                                    (fadeOut(tween(100)) + slideOutVertically(tween(160)) { -it / 2 }) using SizeTransform(clip = false)
                            }
                        },
                        contentAlignment = Alignment.CenterEnd,
                        label = "daysCaption",
                    ) { t ->
                        Text(t, style = HubType.small, color = c.inkMuted)
                    }
                }
                Spacer(Modifier.height(12.dp))
                // 0 means every day, so it shows as all seven on; the last day can't be turned off.
                DaysPicker(
                    mask = if (mask == 0) Days.ALL else mask,
                    onChange = { m -> if (m != 0) mask = if (m == Days.ALL) 0 else m },
                    accent = c.ok,
                )
            }

            // What will happen, in one sentence.
            Row(
                Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.okSoft)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = c.ok, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                val sentence = if (goal == null) {
                    "Enter a goal to see when you'd be nudged"
                } else {
                    NudgeMath.describe(
                        HealthNudge(metric = metric, goal = goal, hour = time.hour, minute = time.minute),
                        timeText,
                    )
                }
                AnimatedContent(
                    targetState = sentence,
                    transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) using SizeTransform(clip = false) },
                    modifier = Modifier.weight(1f),
                    label = "nudgePreview",
                ) { s ->
                    Text(s, style = HubType.bodyStrong, color = c.ink)
                }
            }

            Column(Modifier.padding(top = 28.dp)) {
                PrimaryButton(
                    text = "Save nudge",
                    enabled = goal != null,
                    onClick = {
                        val g = goal ?: return@PrimaryButton
                        onSave(
                            initial.copy(
                                metric = metric,
                                goal = g,
                                hour = time.hour,
                                minute = time.minute,
                                daysMask = mask,
                                // Editing keeps a switched-off nudge off; only a new one starts on.
                                enabled = if (isNew) true else initial.enabled,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) {
                    Spacer(Modifier.height(10.dp))
                    QuietButton(
                        text = "Delete nudge",
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                        color = c.danger,
                        icon = Icons.Outlined.Delete,
                    )
                }
            }
        }
    }

    if (picking) {
        TimeSelectDialog(
            initial = time,
            onDismiss = { picking = false },
            onConfirm = { t ->
                time = t
                picking = false
            },
            accent = c.ok,
        )
    }
}

/**
 * Steps, exercise or energy as three pills on one raised track. The indicator travels on
 * springs with its leading edge ahead of its trailing edge, so it stretches as it moves, and
 * it takes on the colour of the chosen metric.
 */
@Composable
private fun MetricChoices(selected: NudgeMetric, onSelect: (NudgeMetric) -> Unit, modifier: Modifier = Modifier) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val options = NudgeMetric.entries
    val index = options.indexOf(selected).coerceAtLeast(0)
    val left = remember { Animatable(index.toFloat()) }
    val right = remember { Animatable(index + 1f) }
    LaunchedEffect(index) {
        if (reduced) {
            left.snapTo(index.toFloat())
            right.snapTo(index + 1f)
        } else {
            val forward = index > left.value
            val lead = spring<Float>(dampingRatio = 0.8f, stiffness = 900f)
            val trail = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)
            launch { left.animateTo(index.toFloat(), if (forward) trail else lead) }
            launch { right.animateTo(index + 1f, if (forward) lead else trail) }
        }
    }
    val indicator by animateColorAsState(c.metric(selected), tween(260), label = "metricIndicator")
    val inset = with(LocalDensity.current) { 4.dp.toPx() }
    Box(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(c.raised2)
            .drawBehind {
                val seg = (size.width - 2 * inset) / options.size
                val h = size.height - 2 * inset
                drawRoundRect(
                    indicator,
                    topLeft = Offset(inset + seg * left.value, inset),
                    size = Size(seg * (right.value - left.value), h),
                    cornerRadius = CornerRadius(h / 2f),
                )
            },
    ) {
        Row(Modifier.fillMaxSize().padding(4.dp)) {
            options.forEach { m ->
                val isSelected = m == selected
                val fg by animateColorAsState(if (isSelected) c.ground else c.ink, tween(180), label = "metricFg")
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .pressable(
                            onClick = { onSelect(m) },
                            pressedScale = 0.94f,
                            role = Role.RadioButton,
                            showRipple = false,
                        )
                        .semantics { this.selected = isSelected },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(metricIcon(m), contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(metricLabel(m), style = HubType.button, color = fg, maxLines = 1)
                }
            }
        }
    }
}
