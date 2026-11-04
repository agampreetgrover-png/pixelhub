package com.agam.pixelhub.ui.reminders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.container
import com.agam.pixelhub.data.Reminder
import com.agam.pixelhub.notify.ReminderNotifier
import com.agam.pixelhub.ui.EmptyHint
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.ScreenScaffold
import com.agam.pixelhub.ui.SectionHeader
import kotlinx.coroutines.launch

@Composable
fun RemindersScreen() {
    val context = LocalContext.current
    val c = context.container
    val reminders by c.reminders.all.collectAsStateWithLifecycle(emptyList())
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Reminder?>(null) }

    val (done, active) = reminders.partition { it.done }
    val (repeating, oneOff) = active.partition { it.recurrence.isRepeating }

    fun delete(r: Reminder) {
        ReminderNotifier.cancel(context, r.id)
        c.appScope.launch { c.reminders.delete(r) }
    }

    ScreenScaffold(
        title = "Reminders",
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Reminder") },
            )
        },
    ) { padding ->
        if (reminders.isEmpty()) {
            EmptyHint("No reminders yet. Tap Reminder to add one, or ask the Assistant.", Modifier.padding(padding))
        } else {
            LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 96.dp)) {
                section("Upcoming", oneOff) { r ->
                    ReminderRow(r, onToggleDone = { d -> c.appScope.launch { c.reminders.setDone(r, d) } },
                        onClick = { editing = r }, onDelete = { delete(r) })
                }
                section("Repeating", repeating) { r ->
                    ReminderRow(r, onToggleDone = null, onClick = { editing = r }, onDelete = { delete(r) })
                }
                section("Done", done) { r ->
                    ReminderRow(r, onToggleDone = { d -> c.appScope.launch { c.reminders.setDone(r, d) } },
                        onClick = { editing = r }, onDelete = { delete(r) })
                }
            }
        }
    }

    if (creating) {
        ReminderEditorDialog(
            initial = ReminderDraft(),
            isNew = true,
            onDismiss = { creating = false },
            onSave = { d ->
                creating = false
                c.appScope.launch { c.reminders.save(0, d.title, d.notes, d.at, d.recurrence) }
            },
        )
    }
    editing?.let { r ->
        key(r.id) {
            ReminderEditorDialog(
                initial = ReminderDraft(r.title, r.notes, r.anchor, r.recurrence),
                isNew = false,
                onDismiss = { editing = null },
                onSave = { d ->
                    editing = null
                    c.appScope.launch { c.reminders.save(r.id, d.title, d.notes, d.at, d.recurrence) }
                },
            )
        }
    }
}

private fun LazyListScope.section(title: String, reminders: List<Reminder>, row: @Composable (Reminder) -> Unit) {
    if (reminders.isEmpty()) return
    item(key = "header_$title") { SectionHeader(title) }
    items(reminders, key = { it.id }) { row(it) }
}

@Composable
private fun ReminderRow(
    r: Reminder,
    onToggleDone: ((Boolean) -> Unit)?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val overdue = !r.done && !r.recurrence.isRepeating && r.triggerAt < System.currentTimeMillis()
    val supporting = buildString {
        append(Fmt.whenText(context, r.triggerAt))
        r.recurrence.describe().takeIf { it.isNotEmpty() }?.let { append(" · ").append(it) }
        if (r.notes.isNotBlank()) append('\n').append(r.notes)
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(r.title, textDecoration = if (r.done) TextDecoration.LineThrough else null)
        },
        supportingContent = {
            Text(supporting, color = if (overdue) MaterialTheme.colorScheme.error else Color.Unspecified)
        },
        leadingContent = {
            if (onToggleDone != null) Checkbox(checked = r.done, onCheckedChange = onToggleDone)
            else Icon(Icons.Outlined.Repeat, contentDescription = "Repeating")
        },
        trailingContent = {
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Delete") }
        },
    )
}
