package com.agam.pixelhub.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.agam.pixelhub.ui.BackButton
import com.agam.pixelhub.ui.Gutter
import com.agam.pixelhub.ui.ScreenScaffold
import com.agam.pixelhub.ui.Tab
import com.agam.pixelhub.ui.hubCard
import com.agam.pixelhub.ui.motion.HubShapes
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.PolygonShape
import com.agam.pixelhub.ui.motion.RollingText
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.motion.quickFade
import com.agam.pixelhub.ui.motion.rememberHeldTrue
import com.agam.pixelhub.ui.motion.rememberStagger
import com.agam.pixelhub.ui.motion.revealEnter
import com.agam.pixelhub.ui.motion.revealExit
import com.agam.pixelhub.ui.motion.staggerIn
import com.agam.pixelhub.ui.openSafely
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType

/**
 * Settings as a short list of groups. Every group starts as one or two rows; details
 * (the permission list, the calendars, the API key forms) open in place when tapped.
 */
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpen: (Tab) -> Unit = {}) {
    val context = LocalContext.current
    val checks = rememberSetupChecks()
    val notificationsFix = checks.first { it.kind == CheckKind.NOTIFICATIONS }.fix
    val activity = LocalActivity.current
    // The result needs no handling: the checks are read again when the screen resumes. A
    // "Don't allow" is respected here; the next tap decides whether to ask or open settings.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    val stagger = rememberStagger()

    fun fix(check: SetupCheck) {
        if (check.kind == CheckKind.NOTIFICATIONS) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            // Android stops showing the prompt after repeated denials (asked before, no rationale),
            // and it can't turn notifications back on once the permission is held: settings then.
            val granted = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            val blocked = PermissionAsks.askedBefore(context, permission) &&
                activity?.shouldShowRequestPermissionRationale(permission) == false
            if (granted || blocked) {
                openSafely(context, notificationsFix)
            } else {
                PermissionAsks.markAsked(context, permission)
                askNotifications.launch(permission)
            }
        } else {
            openSafely(context, check.fix)
        }
    }

    ScreenScaffold(
        title = "Settings",
        navigationIcon = { BackButton(onBack) },
    ) { padding ->
        // Edge-to-edge windows don't resize for the keyboard, so pad for it: the key field and Save
        // stay reachable above it.
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item(key = "theme_header") { GroupLabel("Appearance", Modifier.staggerIn(stagger, "theme_header", 0)) }
            item(key = "theme") { ThemeSection(Modifier.staggerIn(stagger, "theme", 1)) }

            item(key = "setup_header") { GroupLabel("Alarms & reminders", Modifier.staggerIn(stagger, "setup_header", 2)) }
            item(key = "setup") { SetupCard(checks, onFix = ::fix, modifier = Modifier.staggerIn(stagger, "setup", 3)) }

            item(key = "calendar_header") { GroupLabel("Calendar", Modifier.staggerIn(stagger, "calendar_header", 4)) }
            // The header and its card arrive together; below this the cascade stops.
            item(key = "calendar") { CalendarSyncSection(Modifier.staggerIn(stagger, "calendar", 4)) }

            item(key = "health_header") { GroupLabel("Health") }
            item(key = "health") {
                HealthSettingsCard(onOpenHealth = { onOpen(Tab.HEALTH) })
            }

            item(key = "ai_header") { GroupLabel("Assistant") }
            item(key = "ai") { AssistantLogSection() }

            item(key = "about_header") { GroupLabel("About") }
            item(key = "about") {
                SettingsCard {
                    SettingRow(
                        icon = Icons.Outlined.Info,
                        title = "App info",
                        meta = "PixelHub $version",
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

/** A quiet group name above a card: small, muted, set in from the card's edge. */
@Composable
internal fun GroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = HubType.small.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
        color = Hub.colors.inkMuted,
        modifier = modifier.fillMaxWidth().padding(start = Gutter + 14.dp, end = Gutter, top = 22.dp, bottom = 8.dp),
    )
}

/** A settings group: one raised card holding its rows. */
@Composable
internal fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .hubCard(Hub.colors.raised)
            .padding(4.dp),
        content = content,
    )
}

/**
 * A row in a settings card: an icon in a small round well, a title with a muted line under
 * it, and an optional control at the far end. When [onClick] is set the whole row is the
 * target and springs when pressed.
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
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (onClick != null) {
                    Modifier.pressable(onClick = onClick, pressedScale = 0.985f, role = role, onClickLabel = onClickLabel)
                } else Modifier,
            )
            .padding(start = 10.dp, end = if (trailing != null) 4.dp else 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(iconTone), contentAlignment = Alignment.Center) {
            if (wellContent != null) {
                wellContent()
            } else {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = HubType.bodyStrong.copy(fontSize = 15.sp, lineHeight = 20.sp),
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta != null) {
                // "Not set" -> "Saved", "Checking" -> "Connected": the new line fades in over the old.
                AnimatedContent(targetState = meta, transitionSpec = { quickFade() }, label = "meta") { m ->
                    Text(
                        m,
                        style = HubType.small.copy(fontSize = 12.sp, lineHeight = 16.sp),
                        color = c.inkMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
        modifier = Modifier.padding(end = 8.dp).size(20.dp),
    )
}

/** The arrow at the end of a row that opens more rows under it; turns over when open. */
@Composable
internal fun ExpandChevron(expanded: Boolean) {
    val turn by animateFloatAsState(if (expanded) 180f else 0f, Motion.snappy(), label = "expand")
    Icon(
        Icons.Outlined.ExpandMore,
        contentDescription = null,
        tint = Hub.colors.inkMuted,
        modifier = Modifier.padding(end = 8.dp).size(22.dp).graphicsLayer { rotationZ = turn },
    )
}

/** A small ink pill for a row's one action ("Fix", "Allow", "Connect"). 48dp to the touch. */
@Composable
internal fun RowAction(text: String, onClick: () -> Unit, onClickLabel: String? = null) {
    val c = Hub.colors
    Box(
        Modifier
            .padding(end = 4.dp)
            .minimumInteractiveComponentSize()
            .pressable(onClick = onClick, pressedScale = 0.92f, onClickLabel = onClickLabel ?: text, showRipple = false)
            .height(32.dp)
            .clip(CircleShape)
            .background(c.ink)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = HubType.button.copy(fontSize = 13.sp, lineHeight = 18.sp), color = c.ground, maxLines = 1)
    }
}

/** Opens and closes the extra rows of a card. */
@Composable
internal fun Expandable(visible: Boolean, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible,
        enter = revealEnter(reduced),
        exit = revealExit(reduced),
    ) { content() }
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
 * The permissions alarms need, as one summary row with a small progress ring. Anything not
 * yet granted is always listed under it; granted items show when the row is opened.
 */
@Composable
private fun SetupCard(checks: List<SetupCheck>, onFix: (SetupCheck) -> Unit, modifier: Modifier = Modifier) {
    val total = checks.size
    val done = checks.count { it.ok }
    val left = total - done
    var expanded by rememberSaveable { mutableStateOf(false) }
    SettingsCard(modifier) {
        SettingRow(
            icon = Icons.Outlined.Alarm,
            title = when (left) {
                0 -> "All permissions granted"
                1 -> "1 permission needed"
                else -> "$left permissions needed"
            },
            meta = "Keeps alarms and reminders on time",
            iconTone = Color.Transparent,
            wellContent = { SetupRing(done, total) },
            onClick = { expanded = !expanded },
            onClickLabel = if (expanded) "Hide permissions" else "Show permissions",
            modifier = Modifier.semantics { contentDescription = "$done of $total permissions granted" },
            trailing = { ExpandChevron(expanded) },
        )
        checks.forEach { check ->
            // A row granted just now stays a moment, so its seal is seen before it folds away.
            val needed = rememberHeldTrue(!check.ok)
            Expandable(expanded || needed) { CheckRow(check, onFix = { onFix(check) }) }
        }
    }
}

/** A ring that sweeps to how much is granted; a check inside once everything is. */
@Composable
private fun SetupRing(done: Int, total: Int) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val allDone = total > 0 && done == total
    val fraction = if (total == 0) 1f else done / total.toFloat()
    // Starts empty so the arc sweeps in; moves again whenever the count changes on resume.
    val sweep = remember { Animatable(if (reduced) fraction else 0f) }
    LaunchedEffect(fraction) {
        if (reduced) sweep.snapTo(fraction) else sweep.animateTo(fraction, spring(dampingRatio = 0.9f, stiffness = 260f))
    }
    val arcColor by animateColorAsState(if (allDone) c.ok else c.dawn, tween(240), label = "arc")
    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(36.dp)) {
            val stroke = 3.5.dp.toPx()
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
        AnimatedContent(targetState = allDone, label = "ringCenter") { finished ->
            if (finished) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = c.ok, modifier = Modifier.size(16.dp))
            } else {
                RollingText(
                    "$done/$total",
                    style = HubType.small.copy(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = c.ink,
                )
            }
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

/** A green seal once granted (it pops in as it changes); until then, a "Fix" pill. */
@Composable
private fun StatusEnd(ok: Boolean, title: String, onFix: () -> Unit) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val seal = remember { PolygonShape(HubShapes.burst) }
    AnimatedContent(
        targetState = ok,
        transitionSpec = {
            if (targetState && !reduced) {
                (scaleIn(initialScale = 0.85f, animationSpec = Motion.snappy()) + fadeIn(tween(120))) togetherWith
                    fadeOut(tween(90))
            } else {
                fadeIn(tween(160)) togetherWith fadeOut(tween(120))
            }
        },
        contentAlignment = Alignment.Center,
        label = "status",
    ) { isOk ->
        if (isOk) {
            Box(Modifier.padding(end = 4.dp).size(40.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(24.dp).clip(seal).background(c.ok).semantics { contentDescription = "Granted" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = c.ground, modifier = Modifier.size(14.dp))
                }
            }
        } else {
            RowAction("Fix", onClick = onFix, onClickLabel = "Fix $title")
        }
    }
}
