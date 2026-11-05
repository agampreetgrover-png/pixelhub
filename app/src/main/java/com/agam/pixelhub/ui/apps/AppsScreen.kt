@file:OptIn(ExperimentalLayoutApi::class)

package com.agam.pixelhub.ui.apps

import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.graphics.shapes.RoundedPolygon
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.container
import com.agam.pixelhub.data.AppShortcut
import com.agam.pixelhub.data.ShortcutKind
import com.agam.pixelhub.launcher.AppEntry
import com.agam.pixelhub.launcher.ShortcutLauncher
import com.agam.pixelhub.ui.AppIcon
import com.agam.pixelhub.ui.HubFab
import com.agam.pixelhub.ui.HubSheet
import com.agam.pixelhub.ui.HubTextField
import com.agam.pixelhub.ui.PrimaryButton
import com.agam.pixelhub.ui.QuietButton
import com.agam.pixelhub.ui.ScreenScaffold
import com.agam.pixelhub.ui.motion.HubShapes
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.PolygonShape
import com.agam.pixelhub.ui.motion.Stagger
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.motion.rememberStagger
import com.agam.pixelhub.ui.motion.staggerIn
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.delay
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

/** The enamel-badge shapes behind shortcut icons, cycled by position. */
private val BADGES: List<Pair<RoundedPolygon, Float>> = listOf(
    HubShapes.cookie to 0f,
    HubShapes.clover to 45f,
    HubShapes.gem to 0f,
    HubShapes.burst to 15f,
    HubShapes.circle to 0f,
)

/** The grid is 12 columns: shortcuts take 4 (three a row), apps take 3 (four a row). */
private const val COLUMNS = 12
private const val SHORTCUT_SPAN = 4
private const val APP_SPAN = 3
private const val PLACEHOLDERS = 8

/** Inner padding of each grid cell; the grid's own padding makes up the rest of the gutter. */
private val CellPad = 6.dp

@Composable
fun AppsScreen() {
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
    var addingLink by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    val shortcutStagger = rememberStagger()
    // A fresh stagger once the apps arrive, so the first load cascades even if it took a while.
    val appStagger = key(loaded) { rememberStagger() }
    val badgeSeen = remember { mutableSetOf<Long>() }
    val bornAt = remember { SystemClock.uptimeMillis() }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    ScreenScaffold(
        title = "Apps",
        floatingActionButton = {
            HubFab(icon = Icons.Outlined.Link, contentDescription = "Add link", onClick = { addingLink = true })
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(COLUMNS),
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 120.dp),
        ) {
            item(key = "h-shortcuts", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                GridHeader("Shortcuts", Modifier.staggerIn(shortcutStagger, "h-shortcuts", 0))
            }
            if (shortcuts?.isEmpty() == true) {
                item(key = "shortcuts-empty", span = { GridItemSpan(maxLineSpan) }, contentType = "hint") {
                    Text(
                        "Long-press any app below to keep it here, or add a link to a chat, a route or a playlist.",
                        style = HubType.body,
                        color = Hub.colors.inkMuted,
                        modifier = Modifier
                            .animateItem()
                            .staggerIn(shortcutStagger, "shortcuts-empty", 1)
                            .padding(horizontal = CellPad)
                            .padding(bottom = 8.dp),
                    )
                }
            }
            itemsIndexed(
                shortcuts.orEmpty(),
                key = { _, s -> "s${s.id}" },
                span = { _, _ -> GridItemSpan(SHORTCUT_SPAN) },
                contentType = { _, _ -> "shortcut" },
            ) { index, s ->
                ShortcutTile(
                    shortcut = s,
                    index = index,
                    stagger = shortcutStagger,
                    badgeSeen = badgeSeen,
                    bornAt = bornAt,
                    modifier = Modifier.animateItem(),
                    onClick = { if (!ShortcutLauncher.launch(context, s)) toast("Couldn't open ${s.label}") },
                    onPin = { if (!ShortcutLauncher.pinToHomeScreen(context, s)) toast("Your launcher doesn't support pinning") },
                    onRemove = { c.appScope.launch { c.shortcuts.delete(s) } },
                )
            }

            item(key = "h-apps", span = { GridItemSpan(maxLineSpan) }, contentType = "header") {
                GridHeader("All apps", Modifier.staggerIn(shortcutStagger, "h-apps", 2))
            }
            item(key = "search", span = { GridItemSpan(maxLineSpan) }, contentType = "search") {
                Box(Modifier.staggerIn(shortcutStagger, "search", 3).padding(horizontal = CellPad).padding(bottom = 12.dp)) {
                    HubTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = "Find an app",
                        placeholder = if (apps.isEmpty()) "Search apps" else "Search ${apps.size} apps",
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Hub.colors.inkMuted) },
                        trailingIcon = { ClearButton(visible = query.isNotEmpty(), onClick = { query = "" }) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    )
                }
            }

            if (!loaded || (apps.isEmpty() && query.isBlank())) {
                // Loading: a few breathing circles where the icons will be.
                items(PLACEHOLDERS, key = { "ph$it" }, span = { GridItemSpan(APP_SPAN) }, contentType = { "placeholder" }) { i ->
                    LoadingDot(index = i, modifier = Modifier.animateItem())
                }
            } else if (filtered.isEmpty()) {
                item(key = "no-results", span = { GridItemSpan(maxLineSpan) }, contentType = "hint") {
                    Text(
                        "No app called “${query.trim()}”",
                        style = HubType.body,
                        color = Hub.colors.inkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .animateItem()
                            .fillMaxWidth()
                            .padding(horizontal = CellPad, vertical = 32.dp),
                    )
                }
            }

            itemsIndexed(
                filtered,
                key = { _, app -> "a${app.packageName}" },
                span = { _, _ -> GridItemSpan(APP_SPAN) },
                contentType = { _, _ -> "app" },
            ) { index, app ->
                AppTile(
                    app = app,
                    index = index,
                    stagger = appStagger,
                    modifier = Modifier.animateItem(),
                    onClick = {
                        val s = AppShortcut(label = app.label, kind = ShortcutKind.APP, packageName = app.packageName)
                        if (!ShortcutLauncher.launch(context, s)) toast("Couldn't open ${app.label}")
                    },
                    onAdd = {
                        c.appScope.launch {
                            c.shortcuts.add(AppShortcut(label = app.label, kind = ShortcutKind.APP, packageName = app.packageName))
                        }
                    },
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

@Composable
private fun GridHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = HubType.section,
        color = Hub.colors.ink,
        modifier = modifier.padding(start = CellPad, end = CellPad, top = 20.dp, bottom = 12.dp),
    )
}

/**
 * A shortcut as an enamel badge: a raised block with a soft polygon behind the icon. The badge
 * spins into place on a bouncy spring as the tile arrives; pressing squashes the whole tile.
 */
@Composable
private fun ShortcutTile(
    shortcut: AppShortcut,
    index: Int,
    stagger: Stagger,
    badgeSeen: MutableSet<Long>,
    bornAt: Long,
    modifier: Modifier,
    onClick: () -> Unit,
    onPin: () -> Unit,
    onRemove: () -> Unit,
) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    var menuOpen by remember { mutableStateOf(false) }
    val (polygon, baseTurn) = BADGES[index % BADGES.size]
    val badge = remember(polygon, baseTurn) { PolygonShape(polygon, baseTurn) }

    val pop = remember(shortcut.id) { Animatable(if (shortcut.id in badgeSeen) 1f else 0f) }
    LaunchedEffect(shortcut.id) {
        if (pop.value < 1f) {
            badgeSeen += shortcut.id
            if (SystemClock.uptimeMillis() - bornAt < 700) delay(index.coerceIn(0, 10) * Motion.STAGGER_MS + 80)
            pop.animateTo(1f, if (reduced) tween(160) else Motion.bouncy())
        }
    }

    Box(modifier.padding(CellPad)) {
        Column(
            Modifier
                .fillMaxWidth()
                .staggerIn(stagger, "s${shortcut.id}", index + 1)
                .pressable(
                    onClick = onClick,
                    onLongClick = { menuOpen = true },
                    pressedScale = 0.9f,
                    onClickLabel = "Open ${shortcut.label}",
                    showRipple = false,
                )
                .clip(RoundedCornerShape(26.dp))
                .background(c.raised)
                .padding(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(68.dp)
                    .graphicsLayer {
                        val p = pop.value
                        if (reduced) {
                            alpha = p.coerceIn(0f, 1f)
                        } else {
                            val s = 0.85f + 0.15f * p
                            scaleX = s
                            scaleY = s
                            rotationZ = (1f - p) * -35f
                            alpha = (p * 1.6f).coerceIn(0f, 1f)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(68.dp).clip(badge).background(c.raised2))
                AppIcon(
                    shortcut.packageName,
                    size = 42.dp,
                    fallback = if (shortcut.kind == ShortcutKind.LINK) Icons.Outlined.Link else Icons.Outlined.Apps,
                    modifier = Modifier.graphicsLayer { if (!reduced) rotationZ = (1f - pop.value) * 35f },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                shortcut.label,
                style = HubType.small,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        HubMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
            HubMenuItem("Pin to home screen", Icons.Outlined.PushPin) { menuOpen = false; onPin() }
            HubMenuItem("Remove", Icons.Outlined.Delete, color = c.danger) { menuOpen = false; onRemove() }
        }
    }
}

/** An installed app: icon and label. Rises in on first load, squashes when pressed. */
@Composable
private fun AppTile(
    app: AppEntry,
    index: Int,
    stagger: Stagger,
    modifier: Modifier,
    onClick: () -> Unit,
    onAdd: () -> Unit,
    onInfo: () -> Unit,
) {
    val c = Hub.colors
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .staggerIn(stagger, app.packageName, index, distance = 16f)
                .pressable(
                    onClick = onClick,
                    onLongClick = { menuOpen = true },
                    pressedScale = 0.9f,
                    onClickLabel = "Open ${app.label}",
                    showRipple = false,
                )
                .padding(horizontal = 2.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(app.packageName, size = 52.dp)
            Spacer(Modifier.height(8.dp))
            Text(
                app.label,
                style = HubType.small,
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        HubMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
            HubMenuItem("Add to shortcuts", Icons.Outlined.Add) { menuOpen = false; onAdd() }
            HubMenuItem("App info", Icons.Outlined.Info) { menuOpen = false; onInfo() }
        }
    }
}

/** A placeholder icon that breathes while the app list loads. Each one is a beat behind the last. */
@Composable
private fun LoadingDot(index: Int, modifier: Modifier = Modifier) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val t = rememberInfiniteTransition(label = "loading")
    val breath by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = Motion.EaseInOut),
            RepeatMode.Reverse,
            initialStartOffset = StartOffset(index * 110),
        ),
        label = "breath",
    )
    Column(modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(52.dp)
                .graphicsLayer {
                    if (reduced) {
                        alpha = 0.6f
                    } else {
                        alpha = 0.35f + 0.65f * breath
                        val s = 0.9f + 0.1f * breath
                        scaleX = s
                        scaleY = s
                    }
                }
                .clip(CircleShape)
                .background(c.raised2),
        )
    }
}

/** The search field's clear button: pops in only while there is a query. */
@Composable
private fun ClearButton(visible: Boolean, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(140)) + scaleIn(Motion.snappy(), initialScale = 0.85f),
        exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.85f),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .pressable(onClick = onClick, pressedScale = 0.88f, onClickLabel = "Clear search")
                .semantics { contentDescription = "Clear search" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, contentDescription = null, tint = Hub.colors.inkMuted, modifier = Modifier.size(20.dp))
        }
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

/** Add a deep link: pick a preset to start from, edit it, try it, save it. */
@Composable
private fun AddLinkSheet(onDismiss: () -> Unit, onSave: (name: String, uri: String) -> Unit) {
    val context = LocalContext.current
    val c = Hub.colors
    val stagger = rememberStagger()
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
    val fg by animateColorAsState(if (selected) c.ground else c.ink, tween(180), label = "presetFg")
    Text(
        text,
        style = HubType.small,
        color = fg,
        modifier = modifier
            .pressable(onClick = onClick, pressedScale = 0.94f, showRipple = false)
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
