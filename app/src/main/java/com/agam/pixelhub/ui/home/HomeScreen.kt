package com.agam.pixelhub.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.container
import com.agam.pixelhub.data.AlarmRepository
import com.agam.pixelhub.launcher.ShortcutLauncher
import com.agam.pixelhub.ui.AppIcon
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.ScreenScaffold
import com.agam.pixelhub.ui.Tab
import com.agam.pixelhub.ui.assistant.AssistantViewModel
import com.agam.pixelhub.ui.settings.rememberSetupChecks
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HomeScreen(onNavigate: (Tab) -> Unit, assistant: AssistantViewModel) {
    val context = LocalContext.current
    val c = context.container
    val reminders by c.reminders.all.collectAsStateWithLifecycle(emptyList())
    val alarms by c.alarms.all.collectAsStateWithLifecycle(emptyList())
    val shortcuts by c.shortcuts.all.collectAsStateWithLifecycle(emptyList())
    val pendingSetup = rememberSetupChecks().count { !it.ok }
    var ask by rememberSaveable { mutableStateOf("") }

    val nextAlarm = alarms.filter { it.enabled }
        .map { it to AlarmRepository.nextTrigger(it).toInstant().toEpochMilli() }
        .minByOrNull { it.second }
    val endOfToday = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val dueToday = reminders.filter { !it.done && it.triggerAt < endOfToday }.sortedBy { it.triggerAt }

    fun submit() {
        if (ask.isBlank()) return
        assistant.send(ask)
        ask = ""
        onNavigate(Tab.ASSISTANT)
    }

    ScreenScaffold(
        title = "PixelHub",
        actions = {
            IconButton(onClick = { onNavigate(Tab.SETTINGS) }) {
                BadgedBox(badge = { if (pendingSetup > 0) Badge { Text("$pendingSetup") } }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    Text(greeting(), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (pendingSetup > 0) {
                item {
                    Card(
                        onClick = { onNavigate(Tab.SETTINGS) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Warning, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text("$pendingSetup setup step${if (pendingSetup == 1) "" else "s"} left so alarms work reliably. Tap to fix.")
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = ask,
                    onValueChange = { ask = it },
                    placeholder = { Text("Remind me to call mom at 6pm…") },
                    leadingIcon = { Icon(Icons.Outlined.AutoAwesome, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { submit() }, enabled = ask.isNotBlank()) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                ElevatedCard(onClick = { onNavigate(Tab.ALARMS) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Alarm, contentDescription = null)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Next alarm", style = MaterialTheme.typography.labelLarge)
                            if (nextAlarm == null) {
                                Text("No alarms on", style = MaterialTheme.typography.bodyLarge)
                            } else {
                                val (a, at) = nextAlarm
                                val time = Fmt.time(context, LocalTime.of(a.hour, a.minute))
                                Text(
                                    if (a.label.isBlank()) time else "$time · ${a.label}",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Text("in ${Fmt.untilText(at)}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            item {
                ElevatedCard(onClick = { onNavigate(Tab.REMINDERS) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                            Spacer(Modifier.width(16.dp))
                            Text("Today", style = MaterialTheme.typography.labelLarge)
                        }
                        if (dueToday.isEmpty()) {
                            Text("Nothing due today", style = MaterialTheme.typography.bodyLarge)
                        }
                        dueToday.take(5).forEach { r ->
                            Text(
                                "${Fmt.time(context, java.time.Instant.ofEpochMilli(r.triggerAt).atZone(ZoneId.systemDefault()).toLocalTime())}  ${r.title}",
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (dueToday.size > 5) Text("+${dueToday.size - 5} more", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (shortcuts.isNotEmpty()) {
                item {
                  Column {
                    Text("Shortcuts", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(shortcuts, key = { it.id }) { s ->
                            Column(
                                Modifier.width(64.dp).clickable { ShortcutLauncher.launch(context, s) },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AppIcon(s.packageName)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    s.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                  }
                }
            }
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    else -> "Good evening"
}
