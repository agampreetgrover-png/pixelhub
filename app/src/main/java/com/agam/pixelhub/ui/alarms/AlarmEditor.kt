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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.agam.pixelhub.data.Alarm
import com.agam.pixelhub.scheduling.Days
import com.agam.pixelhub.ui.DaysPicker

private val SNOOZE_CHOICES = listOf(5, 10, 15, 20)

@Composable
fun AlarmEditorDialog(
    initial: Alarm,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    val timeState = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(context))
    var label by remember { mutableStateOf(initial.label) }
    var mask by remember { mutableStateOf(initial.daysMask) }
    var vibrate by remember { mutableStateOf(initial.vibrate) }
    var snooze by remember { mutableStateOf(initial.snoozeMinutes) }
    var ringtone by remember { mutableStateOf(initial.ringtoneUri) }

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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New alarm" else "Edit alarm") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TimePicker(state = timeState)
                OutlinedTextField(
                    value = label, onValueChange = { label = it },
                    label = { Text("Label (optional)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Repeat: ${Days.label(mask).let { if (it == "Once") "rings once" else it }}",
                    style = MaterialTheme.typography.labelLarge)
                DaysPicker(mask, onChange = { mask = it })
                OutlinedButton(
                    onClick = {
                        picker.launch(
                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtone?.toUri()),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.MusicNote, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(ringtoneTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Vibrate", modifier = Modifier.weight(1f))
                    Switch(checked = vibrate, onCheckedChange = { vibrate = it })
                }
                Text("Snooze length", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SNOOZE_CHOICES.forEach { m ->
                        FilterChip(selected = snooze == m, onClick = { snooze = m }, label = { Text("$m min") })
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete alarm", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
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
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
