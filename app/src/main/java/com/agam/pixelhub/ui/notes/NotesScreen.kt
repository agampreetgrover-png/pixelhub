package com.agam.pixelhub.ui.notes

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatColorReset
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.TextUnit
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalFocusManager
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
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.Gutter
import com.agam.pixelhub.ui.HubFab
import com.agam.pixelhub.ui.HubTextField
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

    val c = Hub.colors
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }

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
        val shown = list.orEmpty().filter { NotesRepository.matches(it, query) }
        // The list comes pinned first, so this split keeps each group's order.
        val (pinned, others) = shown.partition { it.pinned }
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding),
            // Bottom room so the last cards scroll clear of the New note button.
            contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 4.dp, bottom = 96.dp),
            verticalItemSpacing = 12.dp,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "search", span = StaggeredGridItemSpan.FullLine) {
                HubTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = "Search notes",
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = c.inkMuted) },
                    trailingIcon = if (query.isEmpty()) null else {
                        { IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, contentDescription = "Clear search", tint = c.inkMuted) } }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                )
            }
            // Only a search can empty a loaded list here; with no notes at all the empty state shows instead.
            if (list != null && shown.isEmpty()) {
                item(key = "noMatch", span = StaggeredGridItemSpan.FullLine) {
                    Text("No notes match “${query.trim()}”", style = HubType.body, color = c.inkMuted, modifier = Modifier.padding(4.dp))
                }
            }
            if (pinned.isNotEmpty()) {
                item(key = "pinnedLabel", span = StaggeredGridItemSpan.FullLine) { GroupLabel("Pinned") }
                items(pinned, key = { it.id }) { note ->
                    NoteCard(note, onClick = { openId = note.id }, modifier = Modifier.animateItem())
                }
                if (others.isNotEmpty()) {
                    item(key = "othersLabel", span = StaggeredGridItemSpan.FullLine) { GroupLabel("Others") }
                }
            }
            items(others, key = { it.id }) { note ->
                NoteCard(note, onClick = { openId = note.id }, modifier = Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(text, style = HubType.small, color = Hub.colors.inkMuted, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

/** Note colours as (name, light, dark). A note stores the position + 1, so only ever add to the end. */
private val NoteTints = listOf(
    Triple("Rose", Color(0xFFF8D7DA), Color(0xFF4A2026)),
    Triple("Peach", Color(0xFFFBE0C8), Color(0xFF4D3020)),
    Triple("Sand", Color(0xFFFAF0C2), Color(0xFF47401C)),
    Triple("Mint", Color(0xFFD9F2DC), Color(0xFF1F3F27)),
    Triple("Teal", Color(0xFFCDEDEA), Color(0xFF16403D)),
    Triple("Sky", Color(0xFFD6E8F7), Color(0xFF1D3550)),
    Triple("Lavender", Color(0xFFE6DDF6), Color(0xFF352A50)),
    Triple("Clay", Color(0xFFEDE3D6), Color(0xFF3E352B)),
)

/** The tint for colour [index] (0 = none) in the current theme. */
@Composable
private fun noteTint(index: Int): Color? = NoteTints.getOrNull(index - 1)?.let { if (Hub.colors.isDark) it.third else it.second }

/** Body text without a fixed line height, so heading lines grow to fit. */
private val NoteBody = HubType.body.copy(lineHeight = TextUnit.Unspecified)

@Composable
private fun NoteCard(note: Note, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Hub.colors
    val palette = NoteFormat.Palette(c.inkMuted, c.dawn)
    Column(
        modifier
            .fillMaxWidth()
            .pressable(onClick = onClick, pressedScale = 0.97f, onClickLabel = "Open note", showRipple = false)
            .hubCard(noteTint(note.color) ?: c.raised)
            .padding(16.dp),
    ) {
        if (note.title.isNotEmpty()) {
            Text(note.title, style = HubType.bodyStrong, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (note.text.isNotEmpty()) {
            if (note.title.isNotEmpty()) Spacer(Modifier.height(4.dp))
            val styled = remember(note.text, palette.muted, palette.accent) { NoteFormat.styled(note.text, palette, forEditor = false) }
            Text(styled, style = NoteBody, color = c.ink, maxLines = 10, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** The full page for one note ([initial] null = a new one). Saves shortly after you stop typing and on the way out. */
@Composable
private fun NoteEditor(initial: Note?, onClose: () -> Unit, onDeleted: (Note) -> Unit) {
    val context = LocalContext.current
    val app = context.container
    val c = Hub.colors
    var id by rememberSaveable { mutableLongStateOf(initial?.id ?: 0L) }
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    // A TextFieldValue, so the formatting buttons know what's selected.
    var body by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(initial?.text.orEmpty())) }
    var pinned by rememberSaveable { mutableStateOf(initial?.pinned ?: false) }
    var color by rememberSaveable { mutableIntStateOf(initial?.color ?: 0) }
    var choosingColor by rememberSaveable { mutableStateOf(false) }
    // 0 until a new note is first saved.
    var editedAt by rememberSaveable { mutableLongStateOf(initial?.updatedAt ?: 0L) }
    // What the database last got, so opening a note and leaving doesn't bump its edit time.
    var savedTitle by rememberSaveable { mutableStateOf(title) }
    var savedBody by rememberSaveable { mutableStateOf(body.text) }
    var gone by remember { mutableStateOf(false) }
    // One write at a time, so a new note's first insert hands its id to the next save.
    // shortcut: the lock lives in this composition, so rotating within the first save's few ms could insert the note twice; move it to the repository if that ever shows up.
    val lock = remember { Mutex() }
    val bodyFocus = remember { FocusRequester() }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    fun save() {
        if (gone || (title == savedTitle && body.text == savedBody)) return
        savedTitle = title
        savedBody = body.text
        editedAt = System.currentTimeMillis()
        val t = title
        val b = body.text
        val p = pinned
        val k = color
        app.appScope.launch { lock.withLock { id = app.notes.save(id, t, b, p, k) } }
    }
    // Pin and colour of a note not saved yet go in with its first save.
    fun togglePin() {
        pinned = !pinned
        val p = pinned
        app.appScope.launch { lock.withLock { if (id != 0L) app.notes.setPinned(id, p) } }
    }
    fun pickColor(k: Int) {
        color = k
        app.appScope.launch { lock.withLock { if (id != 0L) app.notes.setColor(id, k) } }
    }
    fun format(change: (TextFieldValue) -> TextFieldValue) {
        body = change(body)
        bodyFocus.requestFocus()
    }
    fun share() {
        val text = listOf(title.trim(), body.text.trim()).filter { it.isNotEmpty() }.joinToString("\n\n")
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, null))
    }
    fun delete() {
        gone = true
        val t = title
        val b = body.text
        val p = pinned
        val k = color
        app.appScope.launch {
            lock.withLock {
                // Saved first, so Undo brings back the last few letters too.
                val saved = app.notes.save(id, t, b, p, k)
                if (saved != 0L) app.notes.delete(saved)?.let(onDeleted)
            }
        }
        onClose()
    }

    LaunchedEffect(title, body.text) {
        delay(SaveDelayMillis)
        save()
    }
    DisposableEffect(Unit) { onDispose { save() } }
    LaunchedEffect(Unit) { if (id == 0L && title.isEmpty() && body.text.isEmpty()) bodyFocus.requestFocus() }

    val ground by animateColorAsState(noteTint(color) ?: c.ground, label = "noteColor")
    val palette = NoteFormat.Palette(c.inkMuted, c.dawn)
    val styling = remember(palette.muted, palette.accent) {
        VisualTransformation { TransformedText(NoteFormat.styled(it.text, palette, forEditor = true), OffsetMapping.Identity) }
    }

    Column(Modifier.fillMaxSize().background(ground).systemBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(onClose)
            Spacer(Modifier.weight(1f))
            CircleIconButton(
                if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                if (pinned) "Unpin note" else "Pin note",
                onClick = ::togglePin,
            )
            if (title.isNotBlank() || body.text.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                CircleIconButton(Icons.Outlined.Share, "Share note", onClick = ::share)
            }
            Spacer(Modifier.width(8.dp))
            CircleIconButton(Icons.Outlined.Delete, "Delete note", onClick = ::delete)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Gutter)) {
            BasicTextField(
                value = title,
                // A title is one line; Enter moves on to the note.
                onValueChange = { title = it.replace('\n', ' ').take(NotesRepository.MAX_TITLE) },
                modifier = Modifier.fillMaxWidth(),
                textStyle = HubType.section.copy(color = c.ink),
                cursorBrush = SolidColor(c.ink),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { bodyFocus.requestFocus() }),
                decorationBox = { inner -> Hinted(title.isEmpty(), "Title", HubType.section, inner) },
            )
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = body,
                onValueChange = { new ->
                    val next = NoteFormat.continueList(body, new)
                    body = if (next.text.length > NotesRepository.MAX_LENGTH) body else next
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(bodyFocus)
                    // A tap on a checklist box ticks it instead of moving the cursor there.
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(pass = PointerEventPass.Initial)
                            val l = layout ?: return@awaitEachGesture
                            if (down.position.y > l.size.height) return@awaitEachGesture
                            val ticked = NoteFormat.toggleCheckAt(body.text, l.getOffsetForPosition(down.position)) ?: return@awaitEachGesture
                            down.consume()
                            val up = waitForUpOrCancellation(PointerEventPass.Initial) ?: return@awaitEachGesture
                            up.consume()
                            if ((up.position - down.position).getDistance() < viewConfiguration.touchSlop) body = body.copy(text = ticked)
                        }
                    }
                    // Tall, so a tap anywhere below the text still lands in the note.
                    .heightIn(min = 320.dp)
                    .padding(bottom = 24.dp),
                textStyle = NoteBody.copy(color = c.ink),
                cursorBrush = SolidColor(c.ink),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                visualTransformation = styling,
                onTextLayout = { layout = it },
                decorationBox = { inner -> Hinted(body.text.isEmpty(), "Note", NoteBody, inner) },
            )
        }
        if (editedAt > 0L) {
            Text(
                "Edited ${Fmt.whenText(context, editedAt)}",
                style = HubType.small,
                color = c.inkMuted,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp),
            )
        }
        if (choosingColor) ColorRow(selected = color, onPick = ::pickColor)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ToolText("H1", "Heading") { format { NoteFormat.toggleBlock(it, NoteFormat.Block.H1) } }
            ToolText("H2", "Subheading") { format { NoteFormat.toggleBlock(it, NoteFormat.Block.H2) } }
            ToolIcon(Icons.Outlined.FormatBold, "Bold") { format { NoteFormat.wrap(it, "**") } }
            ToolIcon(Icons.Outlined.FormatItalic, "Italic") { format { NoteFormat.wrap(it, "_") } }
            ToolIcon(Icons.Outlined.FormatStrikethrough, "Strikethrough") { format { NoteFormat.wrap(it, "~~") } }
            ToolIcon(Icons.AutoMirrored.Outlined.FormatListBulleted, "Bulleted list") { format { NoteFormat.toggleBlock(it, NoteFormat.Block.BULLET) } }
            ToolIcon(Icons.Outlined.Checklist, "Checklist") { format { NoteFormat.toggleBlock(it, NoteFormat.Block.CHECK) } }
            ToolIcon(Icons.Outlined.Palette, if (choosingColor) "Hide colours" else "Note colour", tint = if (choosingColor) c.dawn else c.ink) {
                choosingColor = !choosingColor
            }
        }
    }
}

/** A field's hint, shown while it's empty. */
@Composable
private fun Hinted(empty: Boolean, hint: String, style: TextStyle, inner: @Composable () -> Unit) {
    Box {
        if (empty) Text(hint, style = style, color = Hub.colors.inkMuted)
        inner()
    }
}

@Composable
private fun ToolIcon(icon: ImageVector, label: String, tint: Color = Hub.colors.ink, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = label, tint = tint) }
}

@Composable
private fun ToolText(text: String, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Text(text, style = HubType.button, color = Hub.colors.ink)
    }
}

/** No colour, then the tints, as round swatches; the chosen one carries a tick. */
@Composable
private fun ColorRow(selected: Int, onPick: (Int) -> Unit) {
    val c = Hub.colors
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        (0..NoteTints.size).forEach { k ->
            val name = if (k == 0) "No colour" else NoteTints[k - 1].first
            Box(
                Modifier
                    .size(40.dp)
                    .pressable(onClick = { onPick(k) }, pressedScale = 0.9f, onClickLabel = name, showRipple = false)
                    .clip(CircleShape)
                    .background(noteTint(k) ?: c.raised)
                    .border(1.dp, c.inkMuted.copy(alpha = 0.4f), CircleShape)
                    .semantics {
                        contentDescription = name
                        this.selected = k == selected
                    },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    k == selected -> Icon(Icons.Outlined.Check, contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
                    k == 0 -> Icon(Icons.Outlined.FormatColorReset, contentDescription = null, tint = c.inkMuted, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}
