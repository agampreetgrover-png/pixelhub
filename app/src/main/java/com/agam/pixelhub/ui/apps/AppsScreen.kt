@file:OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)

package com.agam.pixelhub.ui.apps

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.container
import com.agam.pixelhub.data.AppShortcut
import com.agam.pixelhub.data.ShortcutKind
import com.agam.pixelhub.launcher.AppEntry
import com.agam.pixelhub.launcher.ShortcutLauncher
import com.agam.pixelhub.ui.AppIcon
import com.agam.pixelhub.ui.EmptyHint
import com.agam.pixelhub.ui.ScreenScaffold
import com.agam.pixelhub.ui.SectionHeader
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

@Composable
fun AppsScreen() {
    val context = LocalContext.current
    val c = context.container
    val shortcuts by c.shortcuts.all.collectAsStateWithLifecycle(emptyList())
    var apps by remember { mutableStateOf(emptyList<AppEntry>()) }
    LaunchedEffect(Unit) { apps = c.installedApps.list(refresh = true) }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    var addingLink by remember { mutableStateOf(false) }

    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    ScreenScaffold(
        title = "Apps",
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addingLink = true },
                icon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                text = { Text("Link") },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(84.dp),
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 96.dp),
        ) {
            fullWidth { SectionHeader("Shortcuts") }
            if (shortcuts.isEmpty()) {
                fullWidth {
                    EmptyHint("Long-press any app below to add it here, or tap Link to add a deep link (WhatsApp chat, Maps route, Spotify playlist…).")
                }
            }
            items(shortcuts, key = { "s${it.id}" }) { s ->
                Tile(
                    label = s.label,
                    packageName = s.packageName,
                    onClick = { if (!ShortcutLauncher.launch(context, s)) toast("Couldn't open ${s.label}") },
                ) { close ->
                    DropdownMenuItem(text = { Text("Pin to home screen") }, onClick = {
                        close()
                        if (!ShortcutLauncher.pinToHomeScreen(context, s)) toast("Your launcher doesn't support pinning")
                    })
                    DropdownMenuItem(text = { Text("Remove") }, onClick = {
                        close()
                        c.appScope.launch { c.shortcuts.delete(s) }
                    })
                }
            }
            fullWidth {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search ${apps.size} apps") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                )
            }
            items(filtered, key = { "a${it.packageName}" }) { app ->
                Tile(
                    label = app.label,
                    packageName = app.packageName,
                    onClick = {
                        val s = AppShortcut(label = app.label, kind = ShortcutKind.APP, packageName = app.packageName)
                        if (!ShortcutLauncher.launch(context, s)) toast("Couldn't open ${app.label}")
                    },
                ) { close ->
                    DropdownMenuItem(text = { Text("Add to shortcuts") }, onClick = {
                        close()
                        c.appScope.launch {
                            c.shortcuts.add(AppShortcut(label = app.label, kind = ShortcutKind.APP, packageName = app.packageName))
                        }
                    })
                    DropdownMenuItem(text = { Text("App info") }, onClick = {
                        close()
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${app.packageName}".toUri())
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    })
                }
            }
        }
    }

    if (addingLink) {
        AddLinkDialog(
            onDismiss = { addingLink = false },
            onSave = { name, uri ->
                addingLink = false
                c.appScope.launch { c.shortcuts.add(AppShortcut(label = name, kind = ShortcutKind.LINK, uri = uri)) }
            },
        )
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@Composable
private fun Tile(
    label: String,
    packageName: String?,
    onClick: () -> Unit,
    fallback: ImageVector = Icons.Outlined.Link,
    menu: @Composable (close: () -> Unit) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(onClick = onClick, onLongClick = { open = true })
                .padding(vertical = 10.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(packageName, fallback = fallback)
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) { menu { open = false } }
    }
}

@Composable
private fun AddLinkDialog(onDismiss: () -> Unit, onSave: (name: String, uri: String) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var uri by remember { mutableStateOf("") }
    val valid = name.isNotBlank() && ':' in uri
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add link shortcut") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Start from an example, then edit it:", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LINK_PRESETS.forEach { (presetName, presetUri) ->
                        AssistChip(onClick = { name = presetName; uri = presetUri }, label = { Text(presetName) })
                    }
                }
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uri, onValueChange = { uri = it },
                    label = { Text("Link / URI") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(enabled = ':' in uri, onClick = {
                    if (!ShortcutLauncher.openUri(context, uri)) {
                        Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Test link") }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(name.trim(), uri.trim()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
