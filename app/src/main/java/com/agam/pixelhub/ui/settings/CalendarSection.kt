package com.agam.pixelhub.ui.settings

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.core.net.toUri
import com.agam.pixelhub.ui.openSafely
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.Lock
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.calendar.CalendarInfo
import com.agam.pixelhub.container
import com.agam.pixelhub.ui.PrimaryButton
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.launch

/**
 * One-way copy of reminders into a phone calendar (Google's own sync takes them to Google
 * Calendar). A card with "Off" and a row per calendar on the phone; tap one to pick it.
 */
@Composable
fun CalendarSyncSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = context.container
    val colors = Hub.colors
    val selected by c.calendar.selectedCalendarId.collectAsStateWithLifecycle(initialValue = null)
    var hasPermission by remember { mutableStateOf(c.calendar.hasPermission()) }
    var calendars by remember { mutableStateOf<List<CalendarInfo>>(emptyList()) }
    // Set when the user asked to turn sync on before granting access.
    var enableAfterGrant by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        hasPermission = c.calendar.hasPermission()
        onPauseOrDispose { }
    }
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            calendars = c.calendar.listCalendars()
            if (enableAfterGrant && selected == null) {
                enableAfterGrant = false
                (calendars.firstOrNull { it.isGoogle && it.isPrimary } ?: calendars.firstOrNull())
                    ?.let { cal -> c.appScope.launch { c.reminders.setCalendar(cal.id) } }
            }
        }
    }
    val activity = LocalActivity.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasPermission = result.values.all { it }
        if (!hasPermission) {
            enableAfterGrant = false
            // Denied twice: Android no longer asks, so the button would do nothing. Open the app's
            // settings page, where calendar access can be turned on.
            val blocked = c.calendar.permissions.none { activity?.shouldShowRequestPermissionRationale(it) == true }
            if (blocked) {
                Toast.makeText(context, "Turn on Calendar under Permissions", Toast.LENGTH_LONG).show()
                openSafely(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
            }
        }
    }

    SettingsCard(modifier) {
        SettingRow(
            icon = Icons.Outlined.CalendarMonth,
            title = "Copy reminders to a calendar",
            meta = "Pick a Google calendar and they show up in Google Calendar too.",
        )
        if (!hasPermission) {
            SettingRow(
                icon = Icons.Outlined.Lock,
                title = "Calendar access is off",
                meta = "PixelHub only adds and updates its own events.",
            )
            PrimaryButton(
                "Allow calendar access",
                onClick = { enableAfterGrant = true; ask.launch(c.calendar.permissions) },
                modifier = Modifier.fillMaxWidth().padding(6.dp),
            )
        } else {
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                CalendarOption(
                    title = "Off",
                    subtitle = "Reminders stay in PixelHub only",
                    dot = null,
                    selected = selected == null,
                    onSelect = { c.appScope.launch { c.reminders.setCalendar(null) } },
                )
                calendars.forEach { cal ->
                    CalendarOption(
                        title = cal.name,
                        subtitle = cal.accountName,
                        dot = Color(cal.color),
                        selected = selected == cal.id,
                        onSelect = { c.appScope.launch { c.reminders.setCalendar(cal.id) } },
                    )
                }
            }
            AnimatedVisibility(
                visible = selected != null,
                enter = expandVertically(tween(240, easing = Motion.EaseOut)) + fadeIn(tween(160)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(120)),
            ) {
                Text(
                    "Turning sync off keeps the events already copied.",
                    style = HubType.small.copy(fontSize = 11.sp, lineHeight = 16.sp),
                    color = colors.inkMuted,
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 10.dp),
                )
            }
        }
    }
}

/** A calendar as a row: its colour in the well, the account under the name, a radio at the end. */
@Composable
private fun CalendarOption(title: String, subtitle: String, dot: Color?, selected: Boolean, onSelect: () -> Unit) {
    val colors = Hub.colors
    val fill by animateFloatAsState(if (selected) 1f else 0f, Motion.bouncy(), label = "radio")
    val ring by animateColorAsState(if (selected) colors.sky else colors.inkMuted, tween(180), label = "ring")
    SettingRow(
        icon = Icons.Outlined.EventBusy,
        title = title,
        meta = subtitle.takeIf { it.isNotBlank() },
        iconTint = colors.inkMuted,
        onClick = onSelect,
        role = Role.RadioButton,
        modifier = Modifier.semantics { this.selected = selected },
        // A calendar shows its own colour in the well instead of an icon.
        wellContent = if (dot != null) {
            { Box(Modifier.size(14.dp).clip(CircleShape).background(dot)) }
        } else null,
        trailing = {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(22.dp)) {
                    val s = 2.dp.toPx()
                    drawCircle(ring, radius = size.minDimension / 2 - s / 2, style = Stroke(s))
                    if (fill > 0f) drawCircle(colors.sky, radius = (size.minDimension / 2 - 5.dp.toPx()) * fill)
                }
            }
        },
    )
}
