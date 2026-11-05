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
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material.icons.outlined.Vibration
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.agam.pixelhub.ui.hubCard
import com.agam.pixelhub.ui.hubTimePickerColors
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.RollingText
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.motion.rememberStagger
import com.agam.pixelhub.ui.motion.staggerIn
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime

private val SNOOZE_CHOICES = listOf(5, 10, 15, 20)

/** Repeat presets, one tap each; any other mix of days is set with the day circles. */
private val REPEAT_LABELS = listOf("Once", "Weekdays", "Weekends", "Daily")
private val REPEAT_MASKS = listOf(0, Days.WEEKDAYS, Days.WEEKENDS, Days.ALL)

private val MetaStyle = HubType.small.copy(fontSize = 11.sp, lineHeight = 16.sp)

/**
 * Create or edit an alarm in a sheet. The chosen time sits large on a violet card and rolls
 * as the dial moves; every setting under it is a direct control. Also used by the Assistant
 * and Home to confirm or edit an alarm.
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
    var label by rememberSaveable { mutableStateOf(initial.label) }
    var mask by rememberSaveable { mutableStateOf(initial.daysMask) }
    var vibrate by rememberSaveable { mutableStateOf(initial.vibrate) }
    var snooze by rememberSaveable { mutableStateOf(initial.snoozeMinutes) }
    var ringtone by rememberSaveable { mutableStateOf(initial.ringtoneUri) }
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
                style = HubType.title.copy(fontSize = 22.sp, lineHeight = 30.sp),
                color = c.ink,
                modifier = Modifier.staggerIn(stagger, "title", 0),
            )
            Spacer(Modifier.height(14.dp))
            TimeHero(
                hour = timeState.hour,
                minute = timeState.minute,
                mask = mask,
                label = label,
                modifier = Modifier.staggerIn(stagger, "hero", 1),
            )
            Spacer(Modifier.height(16.dp))
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
            Spacer(Modifier.height(12.dp))
            HubTextField(
                value = label,
                onValueChange = { label = it },
                label = "Label",
                placeholder = "Wake up, gym, meds",
                modifier = Modifier.staggerIn(stagger, "label", 3),
            )

            Column(Modifier.padding(top = 18.dp).staggerIn(stagger, "repeat", 4)) {
                SettingRow(
                    icon = Icons.Outlined.Repeat,
                    title = "Repeat",
                    meta = { LiveCaption(repeatCaption(mask)) },
                )
                Spacer(Modifier.height(8.dp))
                SegmentedChoices(
                    labels = REPEAT_LABELS,
                    selected = REPEAT_MASKS.indexOf(mask and Days.ALL),
                    onSelect = { mask = REPEAT_MASKS[it] },
                    description = "Repeat",
                )
                Spacer(Modifier.height(12.dp))
                DaysPicker(mask, onChange = { mask = it }, accent = c.dawn)
            }

            SettingRow(
                icon = Icons.Outlined.MusicNote,
                title = "Sound",
                onClick = {
                    picker.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtone?.toUri()),
                    )
                },
                onClickLabel = "Choose sound",
                meta = {
                    Text(ringtoneTitle, style = MetaStyle, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                trailing = {
                    Icon(
                        Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = c.inkMuted,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                },
                modifier = Modifier.padding(top = 18.dp).staggerIn(stagger, "sound", 5),
            )

            SettingRow(
                icon = Icons.Outlined.Vibration,
                title = "Vibrate",
                onClick = { vibrate = !vibrate },
                onClickLabel = if (vibrate) "Turn vibration off" else "Turn vibration on",
                meta = { LiveCaption(if (vibrate) "Buzzes along with the sound" else "Sound only") },
                trailing = {
                    HubSwitch(
                        checked = vibrate,
                        onCheckedChange = { vibrate = it },
                        accent = c.dawn,
                        contentDescription = "Vibrate",
                    )
                },
                modifier = Modifier.padding(top = 6.dp).staggerIn(stagger, "vibrate", 6),
            )

            Column(Modifier.padding(top = 6.dp).staggerIn(stagger, "snooze", 7)) {
                SettingRow(
                    icon = Icons.Outlined.Snooze,
                    title = "Snooze length",
                    meta = { LiveCaption("$snooze minutes each time") },
                )
                Spacer(Modifier.height(8.dp))
                SegmentedChoices(
                    labels = snoozeOptions.map { "$it min" },
                    selected = snoozeOptions.indexOf(snooze),
                    onSelect = { snooze = snoozeOptions[it] },
                    description = "Snooze length",
                )
            }

            Column(Modifier.padding(top = 28.dp).staggerIn(stagger, "actions", 8)) {
                PrimaryButton(
                    text = "Save alarm",
                    icon = Icons.Outlined.Check,
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
                    // Two taps, like the reminder editor: the first arms it for three seconds.
                    var armed by remember { mutableStateOf(false) }
                    LaunchedEffect(armed) { if (armed) { delay(3_000); armed = false } }
                    Spacer(Modifier.height(10.dp))
                    QuietButton(
                        text = if (armed) "Tap again to delete" else "Delete alarm",
                        onClick = { if (armed) onDelete() else armed = true },
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
 * The chosen time on the violet card, as on the reference's front card. Each digit rolls up
 * or down as the dial moves; the line under it counts down to when it would ring.
 */
@Composable
private fun TimeHero(hour: Int, minute: Int, mask: Int, label: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = Hub.colors
    val on = c.onDawn
    val soft = c.onDawnSoft
    val (digits, period) = splitTime(Fmt.time(context, LocalTime.of(hour, minute)))
    val ringsAt = AlarmRepository.nextTrigger(Alarm(hour = hour, minute = minute, daysMask = mask))
        .toInstant().toEpochMilli()
    Column(
        modifier
            .fillMaxWidth()
            .hubCard(c.dawnCard)
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Alarm, contentDescription = null, tint = on, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                label.trim().ifBlank { "Alarm" },
                style = HubType.bodyStrong.copy(fontSize = 14.sp),
                color = on,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            LiveCaption(
                repeatCaption(mask),
                style = HubType.bodyStrong.copy(fontSize = 14.sp),
                color = on,
                alignStart = false,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            RollingText(digits, style = HubType.clockHuge.copy(fontSize = 64.sp, lineHeight = 70.sp), color = on)
            if (period != null) {
                RollingText(
                    period,
                    style = HubType.section,
                    color = on,
                    modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Rings in ", style = HubType.small, color = soft)
            RollingText(Fmt.untilText(ringsAt), style = HubType.button.copy(fontSize = 12.sp, lineHeight = 18.sp), color = on)
        }
    }
}

/**
 * A settings line as on the reference lists: an icon in a round well, a title with a muted
 * line under it, and an optional control at the end. Tappable when [onClick] is given.
 */
@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    meta: @Composable () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    val c = Hub.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (onClick != null) {
                    Modifier.pressable(onClick = onClick, pressedScale = 0.98f, onClickLabel = onClickLabel)
                } else {
                    Modifier
                },
            )
            .padding(start = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(c.well), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = HubType.bodyStrong.copy(fontSize = 14.sp, lineHeight = 20.sp), color = c.ink, maxLines = 1)
            meta()
        }
        trailing()
    }
}

/** A short caption that slides softly when its text changes ("Rings once" → "Weekdays"). */
@Composable
private fun LiveCaption(
    text: String,
    style: TextStyle = MetaStyle,
    color: Color = Hub.colors.inkMuted,
    alignStart: Boolean = true,
) {
    val reduced = LocalReducedMotion.current
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            val swap = if (reduced) {
                fadeIn(tween(150)) togetherWith fadeOut(tween(100))
            } else {
                (fadeIn(tween(180, delayMillis = 30)) + slideInVertically(tween(240, easing = Motion.EaseOut)) { it / 2 }) togetherWith
                    (fadeOut(tween(110)) + slideOutVertically(tween(160)) { -it / 2 })
            }
            swap using SizeTransform(clip = false)
        },
        contentAlignment = if (alignStart) Alignment.CenterStart else Alignment.CenterEnd,
        label = "caption",
    ) { t ->
        Text(t, style = style, color = color, maxLines = 1)
    }
}

/**
 * A row of choices on one track (repeat presets, snooze lengths). The violet indicator travels
 * between them on springs, its leading edge a little ahead of its trailing edge, so it
 * stretches as it moves and settles back into a pill. [selected] = -1 shows no indicator
 * (say, a custom mix of days).
 */
@Composable
private fun SegmentedChoices(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    description: String,
) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val start = selected.coerceAtLeast(0)
    // Edges measured in segments: [left, right).
    val left = remember { Animatable(start.toFloat()) }
    val right = remember { Animatable(start + 1f) }
    var shown by remember { mutableStateOf(selected >= 0) }
    LaunchedEffect(selected) {
        if (selected < 0) {
            shown = false
            return@LaunchedEffect
        }
        if (reduced || !shown) {
            // Appearing from "none": no travel, it is simply there.
            left.snapTo(selected.toFloat())
            right.snapTo(selected + 1f)
        } else {
            val forward = selected > left.value
            val lead = spring<Float>(dampingRatio = 0.8f, stiffness = 900f)
            val trail = spring<Float>(dampingRatio = 0.8f, stiffness = 420f)
            launch { left.animateTo(selected.toFloat(), if (forward) trail else lead) }
            launch { right.animateTo(selected + 1f, if (forward) lead else trail) }
        }
        shown = true
    }
    val inset = with(LocalDensity.current) { 4.dp.toPx() }
    val indicator = c.dawnCard
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(c.raised2)
            .drawBehind {
                if (selected >= 0) {
                    val seg = (size.width - 2 * inset) / labels.size
                    val h = size.height - 2 * inset
                    drawRoundRect(
                        indicator,
                        topLeft = Offset(inset + seg * left.value, inset),
                        size = Size(seg * (right.value - left.value), h),
                        cornerRadius = CornerRadius(h / 2f),
                    )
                }
            },
    ) {
        Row(Modifier.fillMaxSize().padding(4.dp)) {
            labels.forEachIndexed { i, text ->
                val isSelected = i == selected
                val fg by animateColorAsState(if (isSelected) c.onDawn else c.ink, tween(160), label = "segFg")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .pressable(
                            onClick = { onSelect(i) },
                            pressedScale = 0.94f,
                            role = Role.RadioButton,
                            showRipple = false,
                        )
                        .semantics {
                            this.selected = isSelected
                            contentDescription = "$description: $text"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text, style = HubType.button.copy(fontSize = 13.sp), color = fg, maxLines = 1)
                }
            }
        }
    }
}

private fun repeatCaption(mask: Int): String =
    if ((mask and Days.ALL) == 0) "Rings once" else Days.label(mask)

/** "7:30 AM" → ("7:30", "AM"); "07:30" → ("07:30", null). */
private fun splitTime(s: String): Pair<String, String?> {
    val i = s.indexOfFirst { it.isWhitespace() || it == ' ' }
    if (i <= 0) return s to null
    val rest = s.substring(i + 1).trim()
    return if (rest.any { it.isLetter() }) s.substring(0, i) to rest else s to null
}
