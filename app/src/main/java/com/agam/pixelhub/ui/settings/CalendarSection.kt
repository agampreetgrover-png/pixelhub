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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.agam.pixelhub.ui.motion.StatusSwap
import com.agam.pixelhub.ui.motion.toggleSpec
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
    // Null until the phone's calendars have been read, so a missing calendar isn't reported early.
    var calendars by remember { mutableStateOf<List<CalendarInfo>?>(null) }
    // Set when the user asked to turn sync on before granting access.
    var enableAfterGrant by remember { mutableStateOf(false) }
    // Bumped on every resume: an account added or removed in system settings changes the list.
    var resumes by remember { mutableIntStateOf(0) }

    LifecycleResumeEffect(Unit) {
        hasPermission = c.calendar.hasPermission()
        resumes++
        onPauseOrDispose { }
    }
    LaunchedEffect(hasPermission, resumes) {
        if (hasPermission) {
            val found = c.calendar.listCalendars()
            calendars = found
            // Used up by the first grant either way: left set, it would switch sync back on
            // the next time the user picks "Off".
            if (enableAfterGrant) {
                enableAfterGrant = false
                if (selected == null) (found.firstOrNull { it.isGoogle && it.isPrimary } ?: found.firstOrNull())
                    ?.let { cal -> c.appScope.launch { c.reminders.setCalendar(cal.id) } }
            }
        }
    }
    val activity = LocalActivity.current
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasPermission = result.values.all { it }
        if (!hasPermission) enableAfterGrant = false
    }
    // Decided before launching: once asked and Android no longer shows the prompt (denied twice),
    // the request would do nothing, so open the app's settings page, where access can be turned on.
    val allow: () -> Unit = {
        enableAfterGrant = true
        val blocked = PermissionAsks.askedBefore(context, CALENDAR_ASKED) &&
            c.calendar.permissions.none { activity?.shouldShowRequestPermissionRationale(it) == true }
        if (blocked) {
            Toast.makeText(context, "Turn on Calendar under Permissions", Toast.LENGTH_LONG).show()
            openSafely(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
        } else {
            PermissionAsks.markAsked(context, CALENDAR_ASKED)
            ask.launch(c.calendar.permissions)
        }
    }

    // The calendar list opens under the row; it starts closed so the card is one line.
    var open by rememberSaveable { mutableStateOf(false) }
    val current = calendars?.firstOrNull { it.id == selected }

    // One row either way: when access is granted only its line and its end control change.
    val tint by animateColorAsState(if (hasPermission && selected != null) colors.ok else colors.ink, Motion.toggleSpec(), label = "syncTint")
    SettingsCard(modifier) {
        SettingRow(
            icon = Icons.Outlined.CalendarMonth,
            title = "Calendar sync",
            meta = when {
                // Access revoked in system settings while a calendar is still chosen: copying has
                // stopped, so say so instead of looking like sync was never set up.
                !hasPermission && selected != null -> "Paused · calendar access was turned off"
                !hasPermission -> "Copy reminders into Google Calendar"
                selected == null -> "Off"
                current != null -> listOf(current.name, current.accountName).filter { it.isNotBlank() }.distinct().joinToString(" · ")
                // Its account was removed or the calendar hidden: copies go nowhere until another is picked.
                calendars != null -> "Calendar not found · pick another"
                else -> "On"
            },
            iconTint = tint,
            onClick = if (hasPermission) ({ open = !open }) else allow,
            onClickLabel = when {
                !hasPermission -> "Allow calendar access"
                open -> "Hide calendars"
                else -> "Choose a calendar"
            },
            trailing = {
                StatusSwap(hasPermission) { granted ->
                    if (granted) ExpandChevron(open) else RowAction("Allow", onClick = allow, onClickLabel = "Allow calendar access")
                }
            },
        )
        if (hasPermission) {
            Expandable(open) {
                Column(Modifier.fillMaxWidth().selectableGroup()) {
                    CalendarOption(
                        title = "Off",
                        // Said here, where it matters: switching off doesn't delete what was copied.
                        subtitle = if (selected != null) "Events already copied stay" else "Reminders stay in PixelHub",
                        dot = null,
                        selected = selected == null,
                        onSelect = { c.appScope.launch { c.reminders.setCalendar(null) } },
                    )
                    calendars.orEmpty().forEach { cal ->
                        CalendarOption(
                            title = cal.name,
                            subtitle = cal.accountName,
                            dot = Color(cal.color),
                            selected = selected == cal.id,
                            onSelect = { c.appScope.launch { c.reminders.setCalendar(cal.id) } },
                        )
                    }
                }
            }
        }
    }
}

private const val CALENDAR_ASKED = "calendar"

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
            Box(Modifier.padding(end = 4.dp).size(40.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(20.dp)) {
                    val s = 2.dp.toPx()
                    drawCircle(ring, radius = size.minDimension / 2 - s / 2, style = Stroke(s))
                    if (fill > 0f) drawCircle(colors.sky, radius = (size.minDimension / 2 - 5.dp.toPx()) * fill)
                }
            }
        },
    )
}
