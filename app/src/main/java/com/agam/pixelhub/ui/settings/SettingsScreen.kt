package com.agam.pixelhub.ui.settings

import com.agam.pixelhub.ui.health.HealthSettingsSection

import android.Manifest
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.graphics.shapes.toPath
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.container
import com.agam.pixelhub.data.SettingsStore
import com.agam.pixelhub.ui.Gutter
import com.agam.pixelhub.ui.HubTextField
import com.agam.pixelhub.ui.PrimaryButton
import com.agam.pixelhub.ui.QuietButton
import com.agam.pixelhub.ui.ScreenScaffold
import com.agam.pixelhub.ui.SectionHeader
import com.agam.pixelhub.ui.motion.HubShapes
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.PolygonShape
import com.agam.pixelhub.ui.motion.RollingText
import com.agam.pixelhub.ui.motion.Stagger
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.motion.rememberStagger
import com.agam.pixelhub.ui.motion.staggerIn
import com.agam.pixelhub.ui.openSafely
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val checks = rememberSetupChecks()
    val notificationsFix = checks.first { it.kind == CheckKind.NOTIFICATIONS }.fix
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // After two denials Android stops showing the prompt; send the user to settings instead.
        if (!granted) openSafely(context, notificationsFix)
    }

    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    val stagger = rememberStagger()

    fun fix(check: SetupCheck) {
        if (check.kind == CheckKind.NOTIFICATIONS) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openSafely(context, check.fix)
        }
    }

    ScreenScaffold(
        title = "Settings",
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 48.dp)) {
            item(key = "hero") {
                SetupHero(checks, Modifier.staggerIn(stagger, "hero", 0))
            }
            item(key = "checks") {
                Checklist(checks, onFix = { fix(it) }, stagger = stagger)
            }

            item(key = "ai_header") {
                SectionHeader("Assistant AI", Modifier.staggerIn(stagger, "ai_header", 7))
            }
            item(key = "ai") {
                AssistantAiSection(Modifier.staggerIn(stagger, "ai", 8))
            }

            item(key = "health_header") { SectionHeader("Health", Modifier.staggerIn(stagger, "health_header", 9)) }
            item(key = "health") { HealthSettingsSection() }

            item(key = "calendar_header") { SectionHeader("Google Calendar", Modifier.staggerIn(stagger, "calendar_header", 9)) }
            item(key = "calendar") { CalendarSyncSection(Modifier.staggerIn(stagger, "calendar", 10)) }

            item(key = "about_header") {
                SectionHeader("About", Modifier.staggerIn(stagger, "about_header", 9))
            }
            item(key = "about") {
                About(version, Modifier.staggerIn(stagger, "about", 10))
            }
        }
    }
}

/**
 * The checklist as a bedside dial: the arc sweeps to how much is granted, the count rolls,
 * and when everything is granted the ring turns green and a burst pops behind the numbers.
 */
@Composable
private fun SetupHero(checks: List<SetupCheck>, modifier: Modifier = Modifier) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val haptics = LocalHapticFeedback.current
    val total = checks.size
    val done = checks.count { it.ok }
    val allDone = total > 0 && done == total
    val fraction = if (total == 0) 1f else done / total.toFloat()

    // Starts empty so the arc sweeps in; animates again whenever the count changes on resume.
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(fraction) {
        if (reduced) sweep.snapTo(fraction) else sweep.animateTo(fraction, tween(1_100, easing = Motion.EaseOut))
    }
    val arcColor by animateColorAsState(if (allDone) c.ok else c.dawn, tween(500), label = "arc")

    // The settled burst behind the numbers, and a ghost of it that flies outward and fades.
    val badge = remember { Animatable(if (allDone && reduced) 1f else 0f) }
    val ghost = remember { Animatable(1f) }
    LaunchedEffect(allDone) {
        if (allDone) {
            if (reduced) {
                badge.snapTo(1f)
            } else {
                delay(650) // let the arc close first
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                launch {
                    badge.snapTo(0.6f)
                    badge.animateTo(1f, Motion.bouncy())
                }
                ghost.snapTo(0f)
                ghost.animateTo(1f, tween(900, easing = Motion.EaseOut))
            }
        } else {
            badge.animateTo(0f, tween(200))
        }
    }
    val burst = remember { HubShapes.burst.toPath(android.graphics.Path()).asComposePath() }

    Column(
        modifier.fillMaxWidth().padding(horizontal = Gutter).padding(top = 8.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(220.dp).semantics { contentDescription = "$done of $total setup steps done" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(196.dp)) {
                val stroke = 14.dp.toPx()
                val r = size.minDimension / 2 - stroke / 2
                val inner = r - stroke * 1.4f

                // Ghost burst: grows past the ring and fades.
                if (ghost.value < 1f) {
                    val t = ghost.value
                    translate(center.x, center.y) {
                        rotate(28f * t, pivot = Offset.Zero) {
                            scale(r * (0.95f + 0.4f * t), pivot = Offset.Zero) {
                                drawPath(burst, c.ok.copy(alpha = 0.32f * (1f - t)))
                            }
                        }
                    }
                }
                // Settled burst behind the numerals.
                if (badge.value > 0f) {
                    translate(center.x, center.y) {
                        scale(inner * badge.value, pivot = Offset.Zero) {
                            drawPath(burst, c.ok.copy(alpha = if (c.isDark) 0.18f else 0.14f))
                        }
                    }
                }

                drawCircle(c.raised2, radius = r, style = Stroke(stroke))
                val s = sweep.value
                if (s > 0f) {
                    drawArc(
                        arcColor, startAngle = -90f, sweepAngle = 360f * s, useCenter = false,
                        topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
                // A knob riding the head of the arc, like the hand of a dial.
                val a = Math.toRadians(-90.0 + 360.0 * s)
                val knob = Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
                drawCircle(if (s > 0f) arcColor else c.inkMuted, radius = stroke * 0.85f, center = knob)
                drawCircle(c.ground, radius = stroke * 0.28f, center = knob)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                RollingText(done.toString(), style = HubType.clockLarge, color = c.ink)
                Text(
                    " of $total",
                    style = HubType.section,
                    color = c.inkMuted,
                    modifier = Modifier.padding(bottom = 9.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        AnimatedContent(
            targetState = allDone to (total - done),
            transitionSpec = { fadeIn(tween(220, delayMillis = 80)) togetherWith fadeOut(tween(120)) },
            label = "heroHeading",
        ) { (finished, left) ->
            Text(
                if (finished) "All set" else if (left == 1) "1 thing left to grant" else "$left things left to grant",
                style = HubType.section,
                color = if (finished) c.ok else c.ink,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Grant these so alarms and reminders fire on time, even in Doze and on the lock screen.",
            style = HubType.body,
            color = c.inkMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/** All checks on one raised surface. Rows that need attention can be tapped anywhere. */
@Composable
private fun Checklist(checks: List<SetupCheck>, onFix: (SetupCheck) -> Unit, stagger: Stagger) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .clip(RoundedCornerShape(28.dp))
            .background(Hub.colors.raised)
            .padding(vertical = 6.dp),
    ) {
        checks.forEachIndexed { i, check ->
            CheckRow(check, onFix = { onFix(check) }, modifier = Modifier.staggerIn(stagger, check.kind, i + 1))
        }
    }
}

@Composable
private fun CheckRow(check: SetupCheck, onFix: () -> Unit, modifier: Modifier = Modifier) {
    val c = Hub.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .pressable(onClick = onFix, enabled = !check.ok, pressedScale = 0.98f, onClickLabel = "Fix ${check.title}")
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusMark(check.ok)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(check.title, style = HubType.bodyStrong, color = c.ink)
            Text(check.detail, style = HubType.small, color = c.inkMuted)
        }
        AnimatedVisibility(
            visible = !check.ok,
            enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.8f, animationSpec = Motion.snappy()),
            exit = fadeOut(tween(140)) + shrinkHorizontally(tween(260, easing = Motion.EaseOut)),
        ) {
            Box(
                Modifier
                    .padding(start = 12.dp)
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(c.dawn)
                    .pressable(onClick = onFix, pressedScale = 0.92f, onClickLabel = "Fix ${check.title}")
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Fix", style = HubType.button, color = c.onDawn)
            }
        }
    }
}

/** A green burst seal when granted (it pops in as it changes), a red dot when not. */
@Composable
private fun StatusMark(ok: Boolean) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val seal = remember { PolygonShape(HubShapes.burst) }
    AnimatedContent(
        targetState = ok,
        transitionSpec = {
            if (targetState && !reduced) {
                (scaleIn(initialScale = 0.5f, animationSpec = Motion.bouncy()) + fadeIn(tween(120))) togetherWith
                    fadeOut(tween(90))
            } else {
                fadeIn(tween(160)) togetherWith fadeOut(tween(160))
            }
        },
        contentAlignment = Alignment.Center,
        label = "status",
    ) { isOk ->
        if (isOk) {
            Box(
                Modifier.size(34.dp).clip(seal).background(c.ok).semantics { contentDescription = "Done" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = c.ground, modifier = Modifier.size(18.dp))
            }
        } else {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(c.dangerSoft).semantics { contentDescription = "Needs attention" },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c.danger))
            }
        }
    }
}

/** One quiet line saying whether a key is in place. Hidden until the store has answered. */
@Composable
private fun KeyStatus(savedKey: String?) {
    val c = Hub.colors
    AnimatedVisibility(visible = savedKey != null, enter = fadeIn(tween(200)), exit = fadeOut(tween(120))) {
        val hasKey = !savedKey.isNullOrBlank()
        val dot by animateColorAsState(if (hasKey) c.ok else c.inkMuted, tween(240), label = "keyDot")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(10.dp))
            Text(
                if (hasKey) "A key is saved on this phone." else "Add a key to use the assistant.",
                style = HubType.body,
                color = c.inkMuted,
            )
        }
    }
}

@Composable
private fun About(version: String, modifier: Modifier = Modifier) {
    val c = Hub.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .clip(RoundedCornerShape(28.dp))
            .background(c.raised)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // A tiny dial: dawn and sky markers on the night ring.
        Canvas(Modifier.size(48.dp)) {
            val stroke = 4.dp.toPx()
            val r = size.minDimension / 2 - stroke
            drawCircle(c.raised2, radius = r, style = Stroke(stroke))
            fun marker(deg: Double, color: Color) {
                val a = Math.toRadians(deg)
                drawCircle(color, radius = 5.dp.toPx(), center = Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat()))
            }
            marker(-60.0, c.dawn)
            marker(150.0, c.sky)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text("PixelHub", style = HubType.section, color = c.ink)
            Text("Version $version", style = HubType.small, color = c.inkMuted)
        }
    }
}
