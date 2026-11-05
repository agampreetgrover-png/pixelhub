@file:OptIn(ExperimentalLayoutApi::class)

package com.agam.pixelhub.ui.apps

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.container
import com.agam.pixelhub.data.AppShortcut
import com.agam.pixelhub.data.ShortcutKind
import com.agam.pixelhub.launcher.AppEntry
import com.agam.pixelhub.launcher.ShortcutLauncher
import com.agam.pixelhub.ui.AppIcon
import com.agam.pixelhub.ui.BackButton
import com.agam.pixelhub.ui.CardRadius
import com.agam.pixelhub.ui.CircleIconButton
import com.agam.pixelhub.ui.Gutter
import com.agam.pixelhub.ui.HubFab
import com.agam.pixelhub.ui.HubSheet
import com.agam.pixelhub.ui.HubTextField
import com.agam.pixelhub.ui.PrimaryButton
import com.agam.pixelhub.ui.QuietButton
import com.agam.pixelhub.ui.ScreenScaffold
import com.agam.pixelhub.ui.hubCard
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.Stagger
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.motion.rememberStagger
import com.agam.pixelhub.ui.motion.staggerIn
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.launch

/** Starting points for link shortcuts; the user edits the number/place/search afterwards. */
private val LINK_PRESETS = listOf(
    "WhatsApp chat" to "https://wa.me/15551234567",
    "Navigate home" to "google.navigation:q=Home",
    "Call someone" to "tel:+15551234567",
    "Spotify search" to "spotify:search:focus",
    "YouTube search" to "https://www.youtube.com/results?search_query=lofi",
    "Email" to "mailto:someone@example.com",
)

/** Shortcuts per row in the pinned card, like the quick-action row on Home. */
private const val PER_ROW = 4
private const val PLACEHOLDERS = 6

/** The all-apps card is drawn piece by piece (one lazy item per row), so it scrolls cheaply. */
private val CardTop = RoundedCornerShape(topStart = CardRadius, topEnd = CardRadius)
private val CardBottom = RoundedCornerShape(bottomStart = CardRadius, bottomEnd = CardRadius)

private val TitleStyle = HubType.bodyStrong.copy(fontSize = 14.sp, lineHeight = 20.sp)
private val MetaStyle = HubType.small.copy(fontSize = 11.sp, lineHeight = 16.sp)

@Composable
fun AppsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val shortcuts by c.shortcuts.all.collectAsStateWithLifecycle<List<AppShortcut>?>(initialValue = null)
    var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        // Show the cached list straight away (if any), then refresh it.
        apps = c.installedApps.list()
        loaded = true
        apps = c.installedApps.list(refresh = true)
    }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    /** Package name to its shortcut, for the pin toggles in the app list. */
    val pinned = remember(shortcuts) {
        shortcuts.orEmpty().filter { it.kind == ShortcutKind.APP && it.packageName != null }.associateBy { it.packageName!! }
    }
    var addingLink by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    val stagger = rememberStagger()
    // A fresh stagger once the apps arrive, so the first load cascades even if it took a while.
    val appStagger = key(loaded) { rememberStagger() }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    fun remove(s: AppShortcut) {
        c.appScope.launch { c.shortcuts.delete(s) }
    }
    fun pin(app: AppEntry) {
        c.appScope.launch {
            c.shortcuts.add(AppShortcut(label = app.label, kind = ShortcutKind.APP, packageName = app.packageName))
        }
    }

    // Leaving edit mode once there is nothing left to edit.
    LaunchedEffect(shortcuts?.isEmpty()) { if (shortcuts?.isEmpty() == true) editing = false }

    ScreenScaffold(
        title = "Apps",
        navigationIcon = { BackButton(onBack) },
        floatingActionButton = {
            HubFab(icon = Icons.Outlined.Add, contentDescription = "Add link", onClick = { addingLink = true })
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 4.dp, bottom = 120.dp),
        ) {
            item(key = "search", contentType = "search") {
                SearchPill(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = if (apps.isEmpty()) "Search apps" else "Search ${apps.size} apps",
                    onSearch = { keyboard?.hide() },
                    modifier = Modifier.staggerIn(stagger, "search", 0),
                )
            }

            item(key = "shortcuts", contentType = "shortcuts") {
                ShortcutsCard(
                    shortcuts = shortcuts,
                    editing = editing,
                    onToggleEdit = { editing = !editing },
                    onOpen = { s -> if (!ShortcutLauncher.launch(context, s)) toast("Couldn't open ${s.label}") },
                    onPinHome = { s ->
                        if (!ShortcutLauncher.pinToHomeScreen(context, s)) {
                            val supported = context.getSystemService(android.content.pm.ShortcutManager::class.java).isRequestPinShortcutSupported
                            toast(if (supported) "Couldn't pin ${s.label}. Is the app still installed?" else "Your launcher doesn't support pinning")
                        }
                    },
                    onRemove = { s -> remove(s) },
                    modifier = Modifier.padding(top = 16.dp).staggerIn(stagger, "shortcuts", 1),
                )
            }

            item(key = "apps-head", contentType = "apps-head") {
                Row(
                    Modifier
                        .padding(top = 16.dp)
                        .staggerIn(stagger, "apps-head", 2)
                        .fillMaxWidth()
                        .clip(CardTop)
                        .background(Hub.colors.raised)
                        .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("All apps", style = HubType.section, color = Hub.colors.ink, modifier = Modifier.weight(1f))
                    if (loaded && apps.isNotEmpty()) {
                        Text(
                            if (query.isBlank()) "${apps.size}" else "${filtered.size} of ${apps.size}",
                            style = HubType.small,
                            color = Hub.colors.inkMuted,
                        )
                    }
                }
            }

            when {
                !loaded || (apps.isEmpty() && query.isBlank()) -> {
                    items(PLACEHOLDERS, key = { "ph$it" }, contentType = { "placeholder" }) { i ->
                        PlaceholderRow(last = i == PLACEHOLDERS - 1)
                    }
                }
                filtered.isEmpty() -> {
                    item(key = "no-results", contentType = "hint") {
                        Text(
                            "No app called “${query.trim()}”",
                            style = HubType.body,
                            color = Hub.colors.inkMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(CardBottom)
                                .background(Hub.colors.raised)
                                .padding(horizontal = 20.dp, vertical = 28.dp),
                        )
                    }
                }
                else -> itemsIndexed(
                    filtered,
                    key = { _, app -> "a${app.packageName}" },
                    contentType = { _, _ -> "app" },
                ) { index, app ->
                    val shortcut = pinned[app.packageName]
                    AppRow(
                        app = app,
                        pinned = shortcut != null,
                        last = index == filtered.lastIndex,
                        enter = Modifier.staggerIn(appStagger, app.packageName, index, distance = 12f),
                        onOpen = {
                            val s = AppShortcut(label = app.label, kind = ShortcutKind.APP, packageName = app.packageName)
                            if (!ShortcutLauncher.launch(context, s)) toast("Couldn't open ${app.label}")
                        },
                        onTogglePin = { if (shortcut != null) remove(shortcut) else pin(app) },
                        onInfo = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${app.packageName}".toUri())
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    )
                }
            }
        }
    }

    if (addingLink) {
        AddLinkSheet(
            onDismiss = { addingLink = false },
            onSave = { name, uri ->
                addingLink = false
                c.appScope.launch { c.shortcuts.add(AppShortcut(label = name, kind = ShortcutKind.LINK, uri = uri)) }
            },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------------------------

/** A raised pill: search glyph, the field, and a clear button while there is a query. */
@Composable
private fun SearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Hub.colors
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(CircleShape)
            .background(c.raised)
            .padding(start = 6.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(c.well), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = c.ink, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = HubType.body.copy(color = c.ink),
            cursorBrush = SolidColor(c.ink),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Find an app" },
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = HubType.body, color = c.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
            },
        )
        ClearButton(visible = value.isNotEmpty(), onClick = { onValueChange("") })
    }
}

/** The search field's clear button: pops in only while there is a query. */
@Composable
private fun ClearButton(visible: Boolean, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(140)) + scaleIn(tween(180, easing = Motion.EaseOut), initialScale = 0.85f),
        exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.85f),
    ) {
        CircleIconButton(
            icon = Icons.Outlined.Close,
            contentDescription = "Clear search",
            onClick = onClick,
            tone = Hub.colors.well,
            size = 44.dp,
            iconSize = 18.dp,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Shortcuts
// ---------------------------------------------------------------------------------------------

/**
 * The pinned shortcuts: a raised card holding rows of icons in round wells with their names
 * under them. "Edit" puts a remove mark on every tile; tapping a tile then removes it.
 */
@Composable
private fun ShortcutsCard(
    shortcuts: List<AppShortcut>?,
    editing: Boolean,
    onToggleEdit: () -> Unit,
    onOpen: (AppShortcut) -> Unit,
    onPinHome: (AppShortcut) -> Unit,
    onRemove: (AppShortcut) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Hub.colors
    Column(modifier.fillMaxWidth().hubCard(c.raised).padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 14.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Shortcuts", style = HubType.section, color = c.ink, modifier = Modifier.weight(1f))
            if (!shortcuts.isNullOrEmpty()) {
                PillToggle(text = if (editing) "Done" else "Edit", on = editing, onClick = onToggleEdit)
            }
        }
        when {
            shortcuts == null -> Spacer(Modifier.height(84.dp))
            shortcuts.isEmpty() -> Text(
                "Tap the pin next to any app below to keep it here, or add a link to a chat, a route or a playlist.",
                style = HubType.small,
                color = c.inkMuted,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 6.dp),
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                shortcuts.chunked(PER_ROW).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { s ->
                            key(s.id) {
                                ShortcutTile(
                                    shortcut = s,
                                    editing = editing,
                                    onOpen = { onOpen(s) },
                                    onPinHome = { onPinHome(s) },
                                    onRemove = { onRemove(s) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        repeat(PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** A small pill that inverts while on ("Edit" / "Done"). */
@Composable
private fun PillToggle(text: String, on: Boolean, onClick: () -> Unit) {
    val c = Hub.colors
    val bg by animateColorAsState(if (on) c.ink else c.well, tween(180), label = "toggleBg")
    val fg by animateColorAsState(if (on) c.raised else c.ink, tween(180), label = "toggleFg")
    Box(
        Modifier
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .background(bg)
            .pressable(onClick = onClick, pressedScale = 0.94f, showRipple = false, onClickLabel = text)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = HubType.small.copy(fontWeight = FontWeight.Medium), color = fg)
    }
}

/** One shortcut, styled like a quick action: the icon in a round well, its name under it. */
@Composable
private fun ShortcutTile(
    shortcut: AppShortcut,
    editing: Boolean,
    onOpen: () -> Unit,
    onPinHome: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier,
) {
    val c = Hub.colors
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .pressable(
                    onClick = if (editing) onRemove else onOpen,
                    onLongClick = { menuOpen = true },
                    pressedScale = 0.94f,
                    onClickLabel = if (editing) "Remove ${shortcut.label}" else "Open ${shortcut.label}",
                    showRipple = false,
                )
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(54.dp).clip(CircleShape).background(c.well), contentAlignment = Alignment.Center) {
                    if (shortcut.kind == ShortcutKind.APP && shortcut.packageName != null) {
                        AppIcon(shortcut.packageName, size = 34.dp)
                    } else {
                        Icon(Icons.Outlined.Link, contentDescription = null, tint = c.ink, modifier = Modifier.size(22.dp))
                    }
                }
                // Edit mode: a remove mark at the well's corner. Qualified so the Column's
                // scoped overload isn't picked up inside this Box.
                androidx.compose.animation.AnimatedVisibility(
                    visible = editing,
                    modifier = Modifier.align(Alignment.TopEnd),
                    enter = fadeIn(tween(140)) + scaleIn(tween(180, easing = Motion.EaseOut), initialScale = 0.6f),
                    exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.6f),
                ) {
                    Box(
                        Modifier.offset(x = 2.dp, y = (-2).dp).size(22.dp).clip(CircleShape).background(c.ink),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = null, tint = c.raised, modifier = Modifier.size(14.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                shortcut.label,
                style = HubType.small.copy(fontWeight = FontWeight.Medium),
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            )
        }
        HubMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
            HubMenuItem("Pin to home screen", Icons.Outlined.PushPin) { menuOpen = false; onPinHome() }
            HubMenuItem("Remove from shortcuts", Icons.Outlined.Delete, color = c.danger) { menuOpen = false; onRemove() }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// All apps
// ---------------------------------------------------------------------------------------------

/**
 * One installed app as a row of the all-apps card: icon, name, and a round pin toggle that
 * adds it to (or removes it from) the shortcuts. Long-press for app info.
 */
@Composable
private fun AppRow(
    app: AppEntry,
    pinned: Boolean,
    last: Boolean,
    enter: Modifier,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    onInfo: () -> Unit,
) {
    val c = Hub.colors
    var menuOpen by remember { mutableStateOf(false) }
    val pinTone by animateColorAsState(if (pinned) c.ink else c.well, tween(180), label = "pinTone")
    val pinTint by animateColorAsState(if (pinned) c.raised else c.ink, tween(180), label = "pinTint")
    Box(
        Modifier
            .fillMaxWidth()
            .then(if (last) Modifier.clip(CardBottom) else Modifier)
            .background(c.raised)
            .padding(start = 8.dp, end = 8.dp, bottom = if (last) 10.dp else 0.dp),
    ) {
        Row(
            enter
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .pressable(
                    onClick = onOpen,
                    onLongClick = { menuOpen = true },
                    pressedScale = 0.98f,
                    onClickLabel = "Open ${app.label}",
                )
                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app.packageName, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.label, style = TitleStyle, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (pinned) Text("In shortcuts", style = MetaStyle, color = c.inkMuted, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            CircleIconButton(
                icon = if (pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                contentDescription = if (pinned) "Remove ${app.label} from shortcuts" else "Add ${app.label} to shortcuts",
                onClick = onTogglePin,
                tone = pinTone,
                tint = pinTint,
                size = 44.dp,
                iconSize = 18.dp,
            )
        }
        HubMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
            HubMenuItem(
                if (pinned) "Remove from shortcuts" else "Add to shortcuts",
                if (pinned) Icons.Outlined.Delete else Icons.Outlined.PushPin,
            ) { menuOpen = false; onTogglePin() }
            HubMenuItem("App info", Icons.Outlined.Info) { menuOpen = false; onInfo() }
        }
    }
}

/** A still row in the shape of an app row while the list loads. */
@Composable
private fun PlaceholderRow(last: Boolean) {
    val c = Hub.colors
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (last) Modifier.clip(CardBottom) else Modifier)
            .background(c.raised)
            .padding(start = 20.dp, end = 20.dp, top = 9.dp, bottom = if (last) 19.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(c.well))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.height(12.dp).width(120.dp).clip(CircleShape).background(c.well))
    }
}

/** Long-press menu on the raised2 tone; no shadow. */
@Composable
private fun HubMenu(expanded: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = Hub.colors.raised2,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) { content() }
}

@Composable
private fun HubMenuItem(text: String, icon: ImageVector, color: Color = Hub.colors.ink, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = HubType.body) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
        onClick = onClick,
        colors = MenuDefaults.itemColors(textColor = color, leadingIconColor = color),
    )
}

// ---------------------------------------------------------------------------------------------
// Add link
// ---------------------------------------------------------------------------------------------

/** Add a deep link: pick a preset to start from, edit it, try it, save it. */
@Composable
private fun AddLinkSheet(onDismiss: () -> Unit, onSave: (name: String, uri: String) -> Unit) {
    val context = LocalContext.current
    val c = Hub.colors
    val stagger: Stagger = rememberStagger()
    var name by remember { mutableStateOf("") }
    var uri by remember { mutableStateOf("") }
    val valid = name.isNotBlank() && ':' in uri

    HubSheet(onDismiss = onDismiss) {
        Text("Add a link", style = HubType.section, color = c.ink, modifier = Modifier.staggerIn(stagger, "title", 0))
        Spacer(Modifier.height(6.dp))
        Text(
            "Open a chat, a route or a search in one tap. Start from an example and edit it.",
            style = HubType.body,
            color = c.inkMuted,
            modifier = Modifier.staggerIn(stagger, "body", 1),
        )
        Spacer(Modifier.height(18.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LINK_PRESETS.forEachIndexed { i, (presetName, presetUri) ->
                PresetPill(
                    text = presetName,
                    selected = uri == presetUri && name == presetName,
                    modifier = Modifier.staggerIn(stagger, "preset$i", i + 2, distance = 12f),
                    onClick = { name = presetName; uri = presetUri },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        HubTextField(
            value = name,
            onValueChange = { name = it },
            label = "Name",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Spacer(Modifier.height(10.dp))
        HubTextField(
            value = uri,
            onValueChange = { uri = it },
            label = "Link",
            placeholder = "https://, tel:, geo:",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
        )
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            QuietButton(
                "Test link",
                enabled = ':' in uri,
                onClick = {
                    if (!ShortcutLauncher.openUri(context, uri)) {
                        Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
                    }
                },
            )
            Spacer(Modifier.width(10.dp))
            PrimaryButton(
                "Save link",
                enabled = valid,
                onClick = { onSave(name.trim(), uri.trim()) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PresetPill(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = Hub.colors
    val bg by animateColorAsState(if (selected) c.ink else c.raised2, tween(180), label = "presetBg")
    val fg by animateColorAsState(if (selected) c.raised else c.ink, tween(180), label = "presetFg")
    Text(
        text,
        style = HubType.small.copy(fontWeight = FontWeight.Medium),
        color = fg,
        modifier = modifier
            .pressable(onClick = onClick, pressedScale = 0.94f, showRipple = false)
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
