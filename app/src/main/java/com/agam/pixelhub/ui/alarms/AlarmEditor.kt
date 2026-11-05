@file:OptIn(ExperimentalMaterial3Api::class)

package com.agam.pixelhub.ui.alarms

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.agam.pixelhub.data.Alarm
import com.agam.pixelhub.data.AlarmRepository
import com.agam.pixelhub.scheduling.Days
import com.agam.pixelhub.ui.DaysPicker
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.HubSheet
import com.agam.pixelhub.ui.HubSwitch
import com.agam.pixelhub.ui.HubTextField
import com.agam.pixelhub.ui.PrimaryButton
import com.agam.pixelhub.ui.QuietButton
import com.agam.pixelhub.ui.hubTimePickerColors
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.RollingText
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.motion.rememberStagger
import com.agam.pixelhub.ui.motion.staggerIn
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.launch
import java.time.LocalTime

private val SNOOZE_CHOICES = listOf(5, 10, 15, 20)

/**
 * Create or edit an alarm in a sheet. The chosen time sits large at the top and rolls as the
 * dial moves. Also used by the Assistant to confirm an alarm it drafted.
 */
@Composable
fun AlarmEditorDialog(
    initial: Alarm,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    val c = Hub.colors
    val stagger = rememberStagger()
    val timeState = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(context))
    var label by remember { mutableStateOf(initial.label) }
    var mask by remember { mutableStateOf(initial.daysMask) }
    var vibrate by remember { mutableStateOf(initial.vibrate) }
    var snooze by remember { mutableStateOf(initial.snoozeMinutes) }
    var ringtone by remember { mutableStateOf(initial.ringtoneUri) }
    // Four choices; an unusual length set elsewhere (say by the Assistant) joins them.
    val snoozeOptions = remember { (SNOOZE_CHOICES + initial.snoozeMinutes).distinct().sorted() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ringtone = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)?.toString()
        }
    }
    val ringtoneTitle = remember(ringtone) {
        runCatching {
            RingtoneManager.getRingtone(context, ringtone?.toUri() ?: Settings.System.DEFAULT_ALARM_ALERT_URI)
                ?.getTitle(context)
        }.getOrNull() ?: "Default alarm sound"
    }

    HubSheet(onDismiss = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Text(
                if (isNew) "New alarm" else "Edit alarm",
                style = HubType.section,
                color = c.ink,
                modifier = Modifier.staggerIn(stagger, "title", 0),
            )
            Spacer(Modifier.height(16.dp))
            TimeHero(
                hour = timeState.hour,
                minute = timeState.minute,
                mask = mask,
                modifier = Modifier.staggerIn(stagger, "hero", 1),
            )
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.fillMaxWidth().staggerIn(stagger, "picker", 2),
                contentAlignment = Alignment.Center,
            ) {
                TimePicker(
                    state = timeState,
                    colors = hubTimePickerColors(c.dawn),
                    layoutType = TimePickerLayoutType.Vertical,
                )
            }
            Spacer(Modifier.height(20.dp))
            HubTextField(
                value = label,
                onValueChange = { label = it },
                label = "Label",
                placeholder = "Wake up, gym, meds",
                modifier = Modifier.staggerIn(stagger, "label", 3),
            )

            Column(Modifier.padding(top = 28.dp).staggerIn(stagger, "repeat", 4)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Repeat", style = HubType.bodyStrong, color = c.ink, modifier = Modifier.weight(1f))
                    LiveCaption(repeatCaption(mask))
                }
                Spacer(Modifier.height(12.dp))
                DaysPicker(mask, onChange = { mask = it }, accent = c.dawn)
            }

            Row(
                Modifier
                    .padding(top = 28.dp)
                    .staggerIn(stagger, "sound", 5)
                    .fillMaxWidth()
                    .pressable(
                        onClick = {
                            picker.launch(
                                Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtone?.toUri()),
                            )
                        },
                        pressedScale = 0.97f,
                        onClickLabel = "Choose sound",
                        showRipple = false,
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.raised2)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(c.dawnSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.MusicNote, contentDescription = null, tint = c.dawn, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sound", style = HubType.bodyStrong, color = c.ink)
                    Text(
                        ringtoneTitle,
                        style = HubType.small,
                        color = c.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = c.inkMuted)
            }

            Row(
                Modifier.padding(top = 16.dp).fillMaxWidth().staggerIn(stagger, "vibrate", 6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Vibrate", style = HubType.bodyStrong, color = c.ink)
                    LiveCaption(
                        if (vibrate) "Buzzes along with the sound" else "Sound only",
                        color = c.inkMuted,
                        alignStart = true,
                    )
                }
                HubSwitch(
                    checked = vibrate,
                    onCheckedChange = { vibrate = it },
                    accent = c.dawn,
                    contentDescription = "Vibrate",
                )
            }

            Column(Modifier.padding(top = 24.dp).staggerIn(stagger, "snooze", 7)) {
                Text("Snooze length", style = HubType.bodyStrong, color = c.ink)
                Spacer(Modifier.height(12.dp))
                SnoozeChoices(options = snoozeOptions, selected = snooze, onSelect = { snooze = it })
            }

            Column(Modifier.padding(top = 32.dp).staggerIn(stagger, "actions", 8)) {
                PrimaryButton(
                    text = "Save alarm",
                    onClick = {
                        onSave(
                            initial.copy(
                                hour = timeState.hour,
                                minute = timeState.minute,
                                daysMask = mask,
                                label = label.trim(),
                                vibrate = vibrate,
                                snoozeMinutes = snooze,
                                ringtoneUri = ringtone,
                                enabled = true,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onDelete != null) {
                    Spacer(Modifier.height(10.dp))
                    QuietButton(
                        text = "Delete alarm",
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth(),
                        color = c.danger,
                        icon = Icons.Outlined.Delete,
                    )
                }
            }
        }
    }
}

/**
 * The chosen time, set huge. Each digit rolls up or down as the dial moves, and the line
 * beneath counts down to when it would ring.
 */
@Composable
private fun TimeHero(hour: Int, minute: Int, mask: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = Hub.colors
    val (digits, period) = splitTime(Fmt.time(context, LocalTime.of(hour, minute)))
    val ringsAt = AlarmRepository.nextTrigger(Alarm(hour = hour, minute = minute, daysMask = mask))
        .toInstant().toEpochMilli()
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            RollingText(digits, style = HubType.clockHuge, color = c.ink)
            if (period != null) {
                RollingText(
                    period,
                    style = HubType.section,
                    color = c.dawn,
                    modifier = Modifier.padding(start = 8.dp, bottom = 14.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Rings in ", style = HubType.body, color = c.inkMuted)
            RollingText(Fmt.untilText(ringsAt), style = HubType.bodyStrong, color = c.dawn)
        }
    }
}

/** A short caption that slides softly when its text changes ("Rings once" → "Weekdays"). */
@Composable
private fun LiveCaption(text: String, color: Color = Hub.colors.dawn, alignStart: Boolean = false) {
    val reduced = LocalReducedMotion.current
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            val swap = if (reduced) {
                fadeIn(tween(160)) togetherWith fadeOut(tween(100))
            } else {
                (fadeIn(tween(200, delayMillis = 40)) + slideInVertically(tween(260, easing = Motion.EaseOut)) { it / 2 }) togetherWith
                    (fadeOut(tween(120)) + slideOutVertically(tween(180)) { -it / 2 })
            }
            swap using SizeTransform(clip = false)
        },
        contentAlignment = if (alignStart) Alignment.CenterStart else Alignment.CenterEnd,
        label = "caption",
    ) { t ->
        Text(t, style = if (alignStart) HubType.small else HubType.bodyStrong, color = color)
    }
}

/**
 * Snooze lengths as a row of pills on one raised track. The dawn indicator travels between
 * them on springs, its leading edge a little ahead of its trailing edge, so it stretches
 * as it moves and settles back into a pill.
 */
@Composable
private fun SnoozeChoices(options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val index = options.indexOf(selected).coerceAtLeast(0)
    // Edges measured in segments: [left, right).
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
    val inset = with(LocalDensity.current) { 4.dp.toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .background(c.raised2)
            .drawBehind {
                val seg = (size.width - 2 * inset) / options.size
                val h = size.height - 2 * inset
                drawRoundRect(
                    c.dawn,
                    topLeft = Offset(inset + seg * left.value, inset),
                    size = Size(seg * (right.value - left.value), h),
                    cornerRadius = CornerRadius(h / 2f),
                )
            },
    ) {
        Row(Modifier.fillMaxSize().padding(4.dp)) {
            options.forEach { m ->
                val isSelected = m == selected
                val fg by animateColorAsState(if (isSelected) c.onDawn else c.ink, tween(180), label = "snoozeFg")
                Box(
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
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$m min", style = HubType.button, color = fg)
                }
            }
        }
    }
}

private fun repeatCaption(mask: Int): String =
    if ((mask and Days.ALL) == 0) "Rings once" else Days.label(mask)

/** "7:30 AM" → ("7:30", "AM"); "07:30" → ("07:30", null). */
private fun splitTime(s: String): Pair<String, String?> {
    val i = s.indexOfFirst { it.isWhitespace() || it == ' ' }
    if (i <= 0) return s to null
    val rest = s.substring(i + 1).trim()
    return if (rest.any { it.isLetter() }) s.substring(0, i) to rest else s to null
}
