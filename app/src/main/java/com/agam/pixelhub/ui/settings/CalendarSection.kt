package com.agam.pixelhub.ui.settings

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.calendar.CalendarInfo
import com.agam.pixelhub.container
import com.agam.pixelhub.ui.Gutter
import com.agam.pixelhub.ui.PrimaryButton
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.launch

/**
 * One-way copy of reminders into a phone calendar (Google's own sync takes them to Google
 * Calendar). "Off" plus a radio list of the calendars on the phone.
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
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasPermission = result.values.all { it }
    }

    Column(modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "Copies your reminders into a calendar on this phone. Pick a Google calendar and they show up in Google Calendar too.",
            style = HubType.body, color = colors.inkMuted,
        )
        if (!hasPermission) {
            PrimaryButton(
                "Allow calendar access",
                onClick = { enableAfterGrant = true; ask.launch(c.calendar.permissions) },
                container = colors.sky, content = colors.onSky,
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(colors.raised).selectableGroup().padding(vertical = 6.dp),
        ) {
            CalendarOption(
                title = "Off",
                subtitle = "Reminders stay in PixelHub only",
                dot = colors.raised2,
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
            enter = expandVertically(tween(260, easing = Motion.EaseOut)) + fadeIn(),
            exit = shrinkVertically(tween(200)) + fadeOut(),
        ) {
            Text(
                "Turning sync off keeps the events already copied.",
                style = HubType.small, color = colors.inkMuted,
            )
        }
    }
}

@Composable
private fun CalendarOption(title: String, subtitle: String, dot: Color, selected: Boolean, onSelect: () -> Unit) {
    val colors = Hub.colors
    val fill by animateFloatAsState(if (selected) 1f else 0f, Motion.bouncy(), label = "radio")
    val ring by animateColorAsState(if (selected) colors.sky else colors.inkMuted, tween(180), label = "ring")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(12.dp)) { drawCircle(dot) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(title, style = HubType.bodyStrong, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = HubType.small, color = colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Canvas(Modifier.size(22.dp)) {
            val s = 2.dp.toPx()
            drawCircle(ring, radius = size.minDimension / 2 - s / 2, style = Stroke(s))
            if (fill > 0f) drawCircle(colors.sky, radius = (size.minDimension / 2 - 5.dp.toPx()) * fill)
        }
    }
}
