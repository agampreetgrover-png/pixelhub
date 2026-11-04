package com.agam.pixelhub.ui.alarms

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.container
import com.agam.pixelhub.data.Alarm
import com.agam.pixelhub.data.AlarmRepository
import com.agam.pixelhub.scheduling.Days
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.ScreenScaffold
import kotlinx.coroutines.launch
import java.time.LocalTime

@Composable
fun AlarmsScreen() {
    val context = LocalContext.current
    val c = context.container
    val alarms by c.alarms.all.collectAsStateWithLifecycle(emptyList())
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Alarm?>(null) }
    val next = alarms.filter { it.enabled }.minOfOrNull { AlarmRepository.nextTrigger(it).toInstant().toEpochMilli() }

    fun save(alarm: Alarm) {
        c.appScope.launch {
            val saved = c.alarms.save(alarm)
            if (saved.enabled) {
                val at = AlarmRepository.nextTrigger(saved).toInstant().toEpochMilli()
                Toast.makeText(context, "Alarm set for ${Fmt.untilText(at)} from now", Toast.LENGTH_SHORT).show()
            }
        }
    }

    ScreenScaffold(
        title = "Alarms",
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Alarm") },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    if (next != null) "Next alarm in ${Fmt.untilText(next)}" else "No alarms on",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(alarms, key = { it.id }) { a ->
                AlarmCard(a, onToggle = { on -> save(a.copy(enabled = on)) }, onClick = { editing = a })
            }
        }
    }

    if (creating) {
        AlarmEditorDialog(
            initial = Alarm(hour = 7, minute = 0),
            isNew = true,
            onDismiss = { creating = false },
            onSave = { creating = false; save(it) },
            onDelete = null,
        )
    }
    editing?.let { a ->
        key(a.id) {
            AlarmEditorDialog(
                initial = a,
                isNew = false,
                onDismiss = { editing = null },
                onSave = { editing = null; save(it) },
                onDelete = {
                    editing = null
                    c.appScope.launch { c.alarms.delete(a) }
                },
            )
        }
    }
}

@Composable
private fun AlarmCard(a: Alarm, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val context = LocalContext.current
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    Fmt.time(context, LocalTime.of(a.hour, a.minute)),
                    style = MaterialTheme.typography.displaySmall,
                    color = if (a.enabled) MaterialTheme.colorScheme.onSurface else dim,
                )
                Text(
                    listOf(a.label, Days.label(a.daysMask)).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = dim,
                )
            }
            Switch(checked = a.enabled, onCheckedChange = onToggle)
        }
    }
}
