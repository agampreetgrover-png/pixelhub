package com.agam.pixelhub.ui.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.container
import com.agam.pixelhub.data.Note
import com.agam.pixelhub.data.NotesRepository
import com.agam.pixelhub.ui.CheckDot
import com.agam.pixelhub.ui.CountBadge
import com.agam.pixelhub.ui.HubTextField
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long "Note deleted · Undo" stays in the drawer. */
private const val UndoMillis = 5_000L

/**
 * Quick notes at the foot of the drawer: a header that folds the section open and shut, a field
 * to jot a line, then the notes, open ones first. Tick a note when it's dealt with; hold one to
 * copy it. [drawerOpen] false drops the keyboard when the drawer closes.
 */
@Composable
fun QuickNotesSection(drawerOpen: Boolean, modifier: Modifier = Modifier) {
    val app = LocalContext.current.container
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val focus = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    val clipboard = LocalClipboardManager.current
    val notes by app.notes.all.collectAsStateWithLifecycle(initialValue = emptyList())
    var expanded by rememberSaveable { mutableStateOf(true) }
    var draft by rememberSaveable { mutableStateOf("") }
    var deleted by remember { mutableStateOf<Note?>(null) }
    val open = notes.count { !it.done }
    val doneCount = notes.size - open

    LaunchedEffect(drawerOpen) { if (!drawerOpen) focus.clearFocus() }
    LaunchedEffect(deleted) {
        if (deleted != null) {
            delay(UndoMillis)
            deleted = null
        }
    }

    fun add() {
        if (NotesRepository.clean(draft) == null) return
        val text = draft
        draft = ""
        app.appScope.launch { app.notes.add(text) }
    }

    Column(modifier.fillMaxWidth()) {
        // Header: tap to fold. The count of open notes shows even while folded.
        val turn by animateFloatAsState(if (expanded) 180f else 0f, tween(if (reduced) 0 else 220, easing = Motion.EaseOut), label = "notesChevron")
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(28.dp))
                .pressable(
                    onClick = {
                        expanded = !expanded
                        if (!expanded) focus.clearFocus()
                    },
                    pressedScale = 0.98f,
                    showRipple = false,
                    onClickLabel = if (expanded) "Fold quick notes" else "Open quick notes",
                )
                .semantics { stateDescription = if (expanded) "Open" else "Folded" }
                .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Outlined.StickyNote2,contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text("Quick notes", style = HubType.button, color = c.ink, modifier = Modifier.weight(1f))
            if (open > 0) {
                CountBadge(open, c.dawn)
                Spacer(Modifier.width(8.dp))
            }
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(22.dp).rotate(turn))
        }

        AnimatedVisibility(
            visible = expanded,
            enter = if (reduced) fadeIn(tween(150)) else expandVertically(tween(260, easing = Motion.EaseOut)) + fadeIn(tween(200, delayMillis = 60)),
            exit = if (reduced) fadeOut(tween(100)) else shrinkVertically(tween(200, easing = Motion.EaseOut)) + fadeOut(tween(120)),
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                HubTextField(
                    value = draft,
                    onValueChange = { draft = it.take(NotesRepository.MAX_LENGTH) },
                    label = "Add a note",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    trailingIcon = {
                        IconButton(onClick = { add() }, enabled = draft.isNotBlank()) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add note", tint = if (draft.isNotBlank()) c.ink else c.inkMuted)
                        }
                    },
                )

                deleted?.let { note ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Note deleted", style = HubType.small, color = c.inkMuted, modifier = Modifier.weight(1f))
                        Text(
                            "Undo",
                            style = HubType.button,
                            color = c.ink,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .pressable(onClick = {
                                    deleted = null
                                    app.appScope.launch { app.notes.restore(note) }
                                }, showRipple = false)
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        )
                    }
                }

                if (notes.isEmpty() && deleted == null) {
                    Text(
                        "Nothing here yet. Notes stay on this phone.",
                        style = HubType.small,
                        color = c.inkMuted,
                        modifier = Modifier.padding(top = 10.dp, start = 4.dp),
                    )
                }

                Spacer(Modifier.padding(top = 4.dp))
                notes.forEach { note ->
                    NoteRow(
                        note = note,
                        onDone = { done -> app.appScope.launch { app.notes.setDone(note, done) } },
                        onCopy = {
                            clipboard.setText(AnnotatedString(note.text))
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDelete = {
                            deleted = note
                            app.appScope.launch { app.notes.delete(note) }
                        },
                    )
                }

                if (doneCount > 0) {
                    Text(
                        "Clear $doneCount done",
                        style = HubType.small,
                        color = c.inkMuted,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(50))
                            .pressable(onClick = { app.appScope.launch { app.notes.clearDone() } }, showRipple = false)
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteRow(note: Note, onDone: (Boolean) -> Unit, onCopy: () -> Unit, onDelete: () -> Unit) {
    val c = Hub.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CheckDot(
            checked = note.done,
            onCheckedChange = onDone,
            accent = c.dawn,
            size = 22.dp,
            contentDescription = note.text,
        )
        Text(
            note.text,
            style = HubType.body.copy(textDecoration = if (note.done) TextDecoration.LineThrough else null),
            color = if (note.done) c.inkMuted else c.ink,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .pressable(onClick = { onDone(!note.done) }, onLongClick = onCopy, pressedScale = 1f, role = Role.Checkbox, onClickLabel = if (note.done) "Mark not done" else "Mark done", showRipple = false)
                .semantics { contentDescription = note.text + if (note.done) ", done. Hold to copy" else ". Hold to copy" }
                .padding(vertical = 10.dp, horizontal = 4.dp),
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Outlined.Close, contentDescription = "Delete note", tint = c.inkMuted, modifier = Modifier.size(18.dp))
        }
    }
}
