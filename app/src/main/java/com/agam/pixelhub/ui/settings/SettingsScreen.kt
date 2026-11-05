package com.agam.pixelhub.ui.settings

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.agam.pixelhub.ui.CircleIconButton
import com.agam.pixelhub.ui.Gutter
import com.agam.pixelhub.ui.ScreenScaffold
import com.agam.pixelhub.ui.SectionHeader
import com.agam.pixelhub.ui.health.HealthSettingsSection
import com.agam.pixelhub.ui.hubCard
import com.agam.pixelhub.ui.motion.HubShapes
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.PolygonShape
import com.agam.pixelhub.ui.motion.RollingText
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.motion.rememberStagger
import com.agam.pixelhub.ui.motion.staggerIn
import com.agam.pixelhub.ui.openSafely
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val checks = rememberSetupChecks()
    val notificationsFix = checks.first { it.kind == CheckKind.NOTIFICATIONS }.fix
    val activity = LocalActivity.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // After two denials Android stops showing the prompt and denies at once; only then send the
        // user to system settings. A single "Don't allow" is respected.
        val blocked = activity?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) == false
        if (!granted && blocked) openSafely(context, notificationsFix)
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
            CircleIconButton(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", onClick = onBack)
        },
    ) { padding ->
        // Edge-to-edge windows don't resize for the keyboard, so pad for it: the key field and Save
        // stay reachable above it.
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            item(key = "setup_header") {
                SectionHeader("Alarms & reminders", Modifier.staggerIn(stagger, "setup_header", 0))
            }
            item(key = "setup") {
                SettingsCard(Modifier.staggerIn(stagger, "setup", 1)) {
                    SetupSummary(checks)
                    checks.forEach { check -> CheckRow(check, onFix = { fix(check) }) }
                }
            }

            item(key = "calendar_header") { SectionHeader("Calendar", Modifier.staggerIn(stagger, "calendar_header", 2)) }
            item(key = "calendar") { CalendarSyncSection(Modifier.staggerIn(stagger, "calendar", 3)) }

            item(key = "health_header") { SectionHeader("Health", Modifier.staggerIn(stagger, "health_header", 4)) }
            item(key = "health") { Box(Modifier.staggerIn(stagger, "health", 5)) { HealthSettingsSection() } }

            item(key = "ai_header") { SectionHeader("Assistant", Modifier.staggerIn(stagger, "ai_header", 6)) }
            item(key = "ai") { AssistantAiSection(Modifier.staggerIn(stagger, "ai", 7)) }

            item(key = "about_header") { SectionHeader("About", Modifier.staggerIn(stagger, "about_header", 8)) }
            item(key = "about") {
                SettingsCard(Modifier.staggerIn(stagger, "about", 9)) {
                    SettingRow(icon = Icons.Outlined.Info, title = "PixelHub", meta = "Version $version")
                    SettingRow(
                        icon = Icons.Outlined.Tune,
                        title = "App info",
                        meta = "Permissions, storage and notifications in Android settings",
                        onClick = {
                            openSafely(
                                context,
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()),
                            )
                        },
                        onClickLabel = "Open app info",
                        trailing = { Chevron() },
                    )
                }
            }
        }
    }
}

/** A settings group: one raised card holding its rows. */
@Composable
internal fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .hubCard(Hub.colors.raised)
            .padding(6.dp),
        content = content,
    )
}

/**
 * A row in a settings card, matching the list rows elsewhere: an icon in a round well, a
 * title with a muted line under it, and an optional control at the far end. When [onClick]
 * is set the whole row is the target and springs when pressed.
 */
@Composable
internal fun SettingRow(
    icon: ImageVector,
    title: String,
    meta: String?,
    modifier: Modifier = Modifier,
    iconTint: Color = Hub.colors.ink,
    iconTone: Color = Hub.colors.well,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    role: Role? = Role.Button,
    /** Drawn in the well instead of [icon], e.g. a calendar's colour. */
    wellContent: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = Hub.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (onClick != null) {
                    Modifier.pressable(onClick = onClick, pressedScale = 0.98f, role = role, onClickLabel = onClickLabel)
                } else Modifier,
            )
            .padding(start = 12.dp, end = if (trailing != null) 6.dp else 12.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(iconTone), contentAlignment = Alignment.Center) {
            if (wellContent != null) {
                wellContent()
            } else {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = HubType.bodyStrong.copy(fontSize = 14.sp, lineHeight = 20.sp),
                color = c.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta != null) {
                Text(
                    meta,
                    style = HubType.small.copy(fontSize = 11.sp, lineHeight = 16.sp),
                    color = c.inkMuted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** The quiet arrow at the end of a row that opens somewhere else. */
@Composable
internal fun Chevron() {
    Icon(
        Icons.Outlined.ChevronRight,
        contentDescription = null,
        tint = Hub.colors.inkMuted,
        modifier = Modifier.padding(end = 6.dp).size(22.dp),
    )
}

private val CheckKind.icon: ImageVector
    get() = when (this) {
        CheckKind.NOTIFICATIONS -> Icons.Outlined.Notifications
        CheckKind.EXACT_ALARMS -> Icons.Outlined.Alarm
        CheckKind.FULL_SCREEN -> Icons.Outlined.Fullscreen
        CheckKind.BATTERY -> Icons.Outlined.BatteryFull
        CheckKind.ALARM_CHANNEL -> Icons.Outlined.NotificationsActive
    }

/**
 * The top of the setup card: a small dial whose arc sweeps to how much is granted, the
 * count rolling inside it, and one line saying what's left. Turns green when all is granted.
 */
@Composable
private fun SetupSummary(checks: List<SetupCheck>) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val total = checks.size
    val done = checks.count { it.ok }
    val allDone = total > 0 && done == total
    val fraction = if (total == 0) 1f else done / total.toFloat()

    // Starts empty so the arc sweeps in; moves again whenever the count changes on resume.
    val sweep = remember { Animatable(if (reduced) fraction else 0f) }
    LaunchedEffect(fraction) {
        if (reduced) sweep.snapTo(fraction) else sweep.animateTo(fraction, spring(dampingRatio = 0.9f, stiffness = 260f))
    }
    val arcColor by animateColorAsState(if (allDone) c.ok else c.dawn, tween(240), label = "arc")

    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$done of $total setup steps done" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(64.dp)) {
                val stroke = 6.dp.toPx()
                val r = size.minDimension / 2 - stroke / 2
                drawCircle(c.raised2, radius = r, style = Stroke(stroke))
                val s = sweep.value
                if (s > 0f) {
                    drawArc(
                        arcColor, startAngle = -90f, sweepAngle = 360f * s, useCenter = false,
                        topLeft = Offset(center.x - r, center.y - r), size = Size(r * 2, r * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
            RollingText("$done/$total", style = HubType.clockSmall, color = c.ink)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = allDone to (total - done),
                transitionSpec = { fadeIn(tween(200, delayMillis = 60)) togetherWith fadeOut(tween(120)) },
                label = "setupHeading",
            ) { (finished, left) ->
                Text(
                    if (finished) "All set" else if (left == 1) "1 thing left to grant" else "$left things left to grant",
                    style = HubType.section,
                    color = c.ink,
                )
            }
            Text(
                "So alarms and reminders fire on time, even in Doze and on the lock screen.",
                style = HubType.small.copy(fontSize = 11.sp, lineHeight = 16.sp),
                color = c.inkMuted,
            )
        }
    }
}

/** A check as a settings row. Rows that need attention can be tapped anywhere, or on Fix. */
@Composable
private fun CheckRow(check: SetupCheck, onFix: () -> Unit) {
    val c = Hub.colors
    val tone by animateColorAsState(if (check.ok) c.well else c.dangerSoft, tween(200), label = "checkTone")
    val tint by animateColorAsState(if (check.ok) c.ink else c.danger, tween(200), label = "checkTint")
    SettingRow(
        icon = check.kind.icon,
        title = check.title,
        meta = check.detail,
        iconTone = tone,
        iconTint = tint,
        onClick = if (check.ok) null else onFix,
        onClickLabel = "Fix ${check.title}",
        trailing = { StatusEnd(check.ok, check.title, onFix) },
    )
}

/** A green seal once granted (it pops in as it changes); until then, an ink "Fix" pill. */
@Composable
private fun StatusEnd(ok: Boolean, title: String, onFix: () -> Unit) {
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
                fadeIn(tween(160)) togetherWith fadeOut(tween(120))
            }
        },
        contentAlignment = Alignment.Center,
        label = "status",
    ) { isOk ->
        if (isOk) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(30.dp).clip(seal).background(c.ok).semantics { contentDescription = "Granted" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = c.ground, modifier = Modifier.size(16.dp))
                }
            }
        } else {
            Box(
                Modifier
                    .heightIn(min = 40.dp)
                    .clip(CircleShape)
                    .background(c.ink)
                    .pressable(onClick = onFix, pressedScale = 0.92f, onClickLabel = "Fix $title")
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Fix", style = HubType.button, color = c.ground)
            }
        }
    }
}
