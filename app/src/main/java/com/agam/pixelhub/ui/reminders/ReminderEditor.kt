@file:OptIn(ExperimentalMaterial3Api::class)

package com.agam.pixelhub.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.agam.pixelhub.scheduling.Days
import com.agam.pixelhub.scheduling.Recurrence
import com.agam.pixelhub.scheduling.RepeatType
import com.agam.pixelhub.ui.DaysPicker
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.TimeSelectDialog
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class ReminderDraft(
    val title: String = "",
    val notes: String = "",
    val at: LocalDateTime = defaultReminderTime(),
    val recurrence: Recurrence = Recurrence.NONE,
)

fun defaultReminderTime(): LocalDateTime = LocalDateTime.now().plusHours(1).truncatedTo(ChronoUnit.HOURS)

private fun repeatLabel(type: RepeatType) = when (type) {
    RepeatType.NONE -> "Doesn't repeat"
    RepeatType.DAILY -> "Every day"
    RepeatType.WEEKLY -> "On certain days"
    RepeatType.EVERY_N_DAYS -> "Every few days"
    RepeatType.EVERY_N_WEEKS -> "Every few weeks"
}

@Composable
fun ReminderEditorDialog(
    initial: ReminderDraft,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (ReminderDraft) -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial.title) }
    var notes by remember { mutableStateOf(initial.notes) }
    var date by remember { mutableStateOf(initial.at.toLocalDate()) }
    var time by remember { mutableStateOf(initial.at.toLocalTime()) }
    var type by remember { mutableStateOf(initial.recurrence.type) }
    var mask by remember { mutableStateOf(initial.recurrence.daysMask) }
    var intervalText by remember { mutableStateOf(initial.recurrence.interval.coerceAtLeast(2).toString()) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var repeatMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun build(): ReminderDraft? {
        val at = LocalDateTime.of(date, time)
        val interval = intervalText.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val rec = Recurrence(
            type = type,
            daysMask = if (type == RepeatType.WEEKLY) mask else 0,
            interval = if (type == RepeatType.EVERY_N_DAYS || type == RepeatType.EVERY_N_WEEKS) interval else 1,
        )
        error = when {
            title.isBlank() -> "Give the reminder a title"
            type == RepeatType.NONE && !at.isAfter(LocalDateTime.now()) -> "Pick a time in the future"
            type == RepeatType.WEEKLY && (mask and Days.ALL) == 0 -> "Pick at least one day"
            else -> null
        }
        return if (error == null) ReminderDraft(title.trim(), notes.trim(), at, rec) else null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New reminder" else "Edit reminder") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Title") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    label = { Text("Notes (optional)") }, maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())))
                    }
                    OutlinedButton(onClick = { showTime = true }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(Fmt.time(context, time))
                    }
                }
                Box {
                    OutlinedButton(onClick = { repeatMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Repeat, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(repeatLabel(type))
                    }
                    DropdownMenu(expanded = repeatMenu, onDismissRequest = { repeatMenu = false }) {
                        RepeatType.entries.forEach { t ->
                            DropdownMenuItem(
                                text = { Text(repeatLabel(t)) },
                                onClick = {
                                    type = t
                                    repeatMenu = false
                                    if (t == RepeatType.WEEKLY && (mask and Days.ALL) == 0) mask = Days.bit(date.dayOfWeek)
                                },
                            )
                        }
                    }
                }
                when (type) {
                    RepeatType.WEEKLY -> DaysPicker(mask, onChange = { mask = it })
                    RepeatType.EVERY_N_DAYS, RepeatType.EVERY_N_WEEKS -> OutlinedTextField(
                        value = intervalText,
                        onValueChange = { intervalText = it.filter(Char::isDigit).take(3) },
                        label = { Text(if (type == RepeatType.EVERY_N_DAYS) "Every how many days" else "Every how many weeks") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    else -> Unit
                }
                if (type != RepeatType.NONE) {
                    Text(
                        "Starts ${date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { build()?.let(onSave) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (showDate) {
        // DatePicker works in UTC midnight millis.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
    if (showTime) {
        TimeSelectDialog(initial = time, onDismiss = { showTime = false }, onConfirm = { time = it; showTime = false })
    }
}
