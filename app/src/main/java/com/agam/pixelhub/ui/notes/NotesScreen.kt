package com.agam.pixelhub.ui.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.container
import com.agam.pixelhub.data.Note
import com.agam.pixelhub.data.NotesRepository
import com.agam.pixelhub.ui.BackButton
import com.agam.pixelhub.ui.CircleIconButton
import com.agam.pixelhub.ui.EmptyState
import com.agam.pixelhub.ui.Gutter
import com.agam.pixelhub.ui.HubFab
import com.agam.pixelhub.ui.ScreenScaffold
import com.agam.pixelhub.ui.hubCard
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How long after the last keystroke the note is saved. */
private const val SaveDelayMillis = 500L

/**
 * Notes, like Keep: a two-column grid of cards, last edited first. Tap a card or New note to
 * write on a full page that saves as you type; a note left empty is thrown away.
 */
@Composable
fun NotesScreen(onBack: () -> Unit) {
    val app = LocalContext.current.container
    // Null until Room answers, so the page doesn't flash the empty state on every open.
    val notes by app.notes.all.collectAsStateWithLifecycle<List<Note>?>(initialValue = null)
    // The open note: null = the grid, 0 = a new note, otherwise its id.
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val uiScope = rememberCoroutineScope()

    fun deleted(note: Note) {
        uiScope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val undo = snackbar.showSnackbar("Note deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (undo == SnackbarResult.ActionPerformed) app.appScope.launch { app.notes.restore(note) }
        }
    }

    val list = notes
    val open = openId
    if (open != null) {
        BackHandler { openId = null }
        // Wait for the notes, or a reopened note would start blank.
        if (list == null) return
        // A different note gets a fresh editor, not the last one's text.
        key(open) {
            NoteEditor(initial = list.firstOrNull { it.id == open }, onClose = { openId = null }, onDeleted = ::deleted)
        }
        return
    }

    ScreenScaffold(
        title = "Notes",
        navigationIcon = { BackButton(onBack) },
        floatingActionButton = { HubFab(Icons.Outlined.Add, "New note", onClick = { openId = 0L }) },
        snackbarHost = snackbar,
    ) { padding ->
        if (list != null && list.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
                EmptyState(
                    title = "No notes yet",
                    body = "Tap New note to write one. Notes stay on this phone.",
                    accent = Hub.colors.dawn,
                )
            }
            return@ScreenScaffold
        }
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            // Bottom room so the last cards scroll clear of the New note button.
            contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 4.dp, bottom = 96.dp),
            verticalItemSpacing = 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(list.orEmpty(), key = { it.id }) { note ->
                NoteCard(note, onClick = { openId = note.id }, modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun NoteCard(note: Note, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Hub.colors
    Column(
        modifier
            .fillMaxWidth()
            .pressable(onClick = onClick, pressedScale = 0.97f, onClickLabel = "Open note", showRipple = false)
            .hubCard(c.raised)
            .padding(16.dp),
    ) {
        if (note.title.isNotEmpty()) {
            Text(note.title, style = HubType.bodyStrong, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (note.text.isNotEmpty()) {
            if (note.title.isNotEmpty()) Spacer(Modifier.height(4.dp))
            Text(note.text, style = HubType.body, color = c.ink, maxLines = 10, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The full page for one note ([initial] null = a new one). Saves shortly after you stop typing and on the way out. */
@Composable
private fun NoteEditor(initial: Note?, onClose: () -> Unit, onDeleted: (Note) -> Unit) {
    val app = LocalContext.current.container
    val c = Hub.colors
    var id by rememberSaveable { mutableLongStateOf(initial?.id ?: 0L) }
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var body by rememberSaveable { mutableStateOf(initial?.text.orEmpty()) }
    // What the database last got, so opening a note and leaving doesn't bump its edit time.
    var savedTitle by rememberSaveable { mutableStateOf(title) }
    var savedBody by rememberSaveable { mutableStateOf(body) }
    var gone by remember { mutableStateOf(false) }
    // One write at a time, so a new note's first insert hands its id to the next save.
    // shortcut: the lock lives in this composition, so rotating within the first save's few ms could insert the note twice; move it to the repository if that ever shows up.
    val lock = remember { Mutex() }
    val bodyFocus = remember { FocusRequester() }

    fun save() {
        if (gone || (title == savedTitle && body == savedBody)) return
        savedTitle = title
        savedBody = body
        val t = title
        val b = body
        app.appScope.launch { lock.withLock { id = app.notes.save(id, t, b) } }
    }
    fun delete() {
        gone = true
        val t = title
        val b = body
        app.appScope.launch {
            lock.withLock {
                // Saved first, so Undo brings back the last few letters too.
                val saved = app.notes.save(id, t, b)
                if (saved != 0L) app.notes.delete(saved)?.let(onDeleted)
            }
        }
        onClose()
    }

    LaunchedEffect(title, body) {
        delay(SaveDelayMillis)
        save()
    }
    DisposableEffect(Unit) { onDispose { save() } }
    LaunchedEffect(Unit) { if (id == 0L && title.isEmpty() && body.isEmpty()) bodyFocus.requestFocus() }

    Column(Modifier.fillMaxSize().background(c.ground).systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onClose)
            Spacer(Modifier.weight(1f))
            CircleIconButton(Icons.Outlined.Delete, "Delete note", onClick = ::delete)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Gutter)) {
            NoteField(
                value = title,
                // A title is one line; Enter moves on to the note.
                onValueChange = { title = it.replace('\n', ' ').take(NotesRepository.MAX_TITLE) },
                hint = "Title",
                style = HubType.section,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { bodyFocus.requestFocus() }),
            )
            Spacer(Modifier.height(12.dp))
            NoteField(
                value = body,
                onValueChange = { body = it.take(NotesRepository.MAX_LENGTH) },
                hint = "Note",
                style = HubType.body,
                // Tall, so a tap anywhere below the text still lands in the note.
                modifier = Modifier.focusRequester(bodyFocus).heightIn(min = 320.dp).padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun NoteField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val c = Hub.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        textStyle = style.copy(color = c.ink),
        cursorBrush = SolidColor(c.ink),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(hint, style = style, color = c.inkMuted)
                inner()
            }
        },
    )
}
