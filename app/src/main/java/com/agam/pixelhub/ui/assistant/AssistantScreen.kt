package com.agam.pixelhub.ui.assistant

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.ai.ParsedCommand
import com.agam.pixelhub.data.Alarm
import com.agam.pixelhub.scheduling.Days
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.ScreenScaffold
import com.agam.pixelhub.ui.alarms.AlarmEditorDialog
import com.agam.pixelhub.ui.reminders.ReminderDraft
import com.agam.pixelhub.ui.reminders.ReminderEditorDialog
import java.time.LocalTime

private val SUGGESTIONS = listOf(
    "Remind me to call mom at 6pm",
    "Wake me up at 6:30 on weekdays",
    "Remind me to water the plants every 3 days at 8am",
    "Open Spotify",
    "Navigate to the nearest petrol station",
)

@Composable
fun AssistantScreen(vm: AssistantViewModel, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val items by vm.items.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val hasKey by vm.hasApiKey.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<ChatItem.Action?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(items.size, busy) {
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { vm.send(it) }
    }

    fun submit() {
        if (input.isBlank() || busy) return
        vm.send(input)
        input = ""
    }

    ScreenScaffold(
        title = "Assistant",
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Ask or give a command…") },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { submit() }),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { startSpeech(context) { speech.launch(it) } }, enabled = !busy) {
                        Icon(Icons.Outlined.Mic, contentDescription = "Speak")
                    }
                    FilledIconButton(onClick = { submit() }, enabled = input.isNotBlank() && !busy) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!hasKey) item { NoKeyCard(onOpenSettings) }
            if (items.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Try saying:", style = MaterialTheme.typography.labelLarge)
                        SUGGESTIONS.forEach { s -> SuggestionChip(onClick = { vm.send(s) }, label = { Text(s) }) }
                    }
                }
            }
            items(items, key = { it.id }) { item ->
                when (item) {
                    is ChatItem.User -> Bubble(item.text, mine = true)
                    is ChatItem.Bot -> Bubble(item.text, mine = false, isError = item.isError)
                    is ChatItem.Action -> ActionCard(
                        item = item,
                        onConfirm = { vm.confirm(item.id) },
                        onEdit = when (item.command) {
                            is ParsedCommand.CreateReminder, is ParsedCommand.CreateAlarm -> ({ editing = item })
                            else -> null
                        },
                        onCancel = { vm.cancel(item.id) },
                    )
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
    }

    editing?.let { item ->
        when (val cmd = item.command) {
            is ParsedCommand.CreateReminder -> ReminderEditorDialog(
                initial = ReminderDraft(cmd.title, cmd.notes, cmd.at, cmd.recurrence),
                isNew = true,
                onDismiss = { editing = null },
                onSave = { draft ->
                    editing = null
                    vm.saveReminder(item.id, draft)
                },
            )
            is ParsedCommand.CreateAlarm -> AlarmEditorDialog(
                initial = Alarm(hour = cmd.hour, minute = cmd.minute, daysMask = cmd.daysMask, label = cmd.label),
                isNew = true,
                onDismiss = { editing = null },
                onSave = { alarm ->
                    editing = null
                    vm.saveAlarm(item.id, alarm)
                },
                onDelete = null,
            )
            else -> Unit
        }
    }
}

private fun startSpeech(context: Context, launch: (Intent) -> Unit) {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_PROMPT, "What should I do?")
    try {
        launch(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Speech recognition isn't available", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean, isError: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = when {
                mine -> MaterialTheme.colorScheme.primaryContainer
                isError -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun NoKeyCard(onOpenSettings: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Key, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text("Gemini API key needed", style = MaterialTheme.typography.titleMedium)
            }
            Text("Get a key from Google AI Studio and paste it in Settings to use the assistant.")
            Button(onClick = onOpenSettings) { Text("Open Settings") }
        }
    }
}

private data class ActionText(val icon: ImageVector, val title: String, val detail: String)

@Composable
private fun describe(item: ChatItem.Action): ActionText {
    val context = LocalContext.current
    return when (val cmd = item.command) {
        is ParsedCommand.CreateReminder -> ActionText(
            Icons.Outlined.NotificationsActive,
            "Reminder: ${cmd.title}",
            listOf(Fmt.whenText(context, cmd.at), cmd.recurrence.describe()).filter { it.isNotBlank() }.joinToString(" · "),
        )
        is ParsedCommand.CreateAlarm -> ActionText(
            Icons.Outlined.Alarm,
            "Alarm at ${Fmt.time(context, LocalTime.of(cmd.hour, cmd.minute))}",
            listOf(cmd.label, Days.label(cmd.daysMask)).filter { it.isNotBlank() }.joinToString(" · "),
        )
        is ParsedCommand.OpenApp -> ActionText(
            Icons.Outlined.Apps,
            "Open ${item.target?.label ?: cmd.query}",
            if (item.target == null) "No matching app found" else "",
        )
        is ParsedCommand.OpenLink -> ActionText(Icons.Outlined.Link, "Open link", cmd.uri)
        else -> ActionText(Icons.Outlined.Info, "Action", "")
    }
}

@Composable
private fun ActionCard(item: ChatItem.Action, onConfirm: () -> Unit, onEdit: (() -> Unit)?, onCancel: () -> Unit) {
    val text = describe(item)
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(text.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(text.title, style = MaterialTheme.typography.titleMedium)
                    if (text.detail.isNotBlank()) {
                        Text(text.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (item.note.isNotBlank()) {
                Text(
                    item.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.status == ChatItem.Status.DONE) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
            }
            when (item.status) {
                ChatItem.Status.PENDING -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onCancel) { Text("Cancel") }
                    if (onEdit != null) TextButton(onClick = onEdit) { Text("Edit") }
                    Button(onClick = onConfirm) { Text("Confirm") }
                }
                ChatItem.Status.CANCELLED -> Text(
                    "Cancelled",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ChatItem.Status.DONE -> Unit
            }
        }
    }
}
