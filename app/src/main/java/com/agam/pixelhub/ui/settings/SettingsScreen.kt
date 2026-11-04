package com.agam.pixelhub.ui.settings

import android.Manifest
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.container
import com.agam.pixelhub.data.SettingsStore
import com.agam.pixelhub.ui.ScreenScaffold
import com.agam.pixelhub.ui.SectionHeader
import com.agam.pixelhub.ui.openSafely
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val checks = rememberSetupChecks()
    val notificationsFix = checks.first { it.kind == CheckKind.NOTIFICATIONS }.fix
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // After two denials Android stops showing the prompt; send the user to settings instead.
        if (!granted) openSafely(context, notificationsFix)
    }

    val savedKey by c.settings.apiKey.collectAsStateWithLifecycle(initialValue = null)
    val savedModel by c.settings.model.collectAsStateWithLifecycle(initialValue = null)
    var key by remember(savedKey) { mutableStateOf(savedKey.orEmpty()) }
    var model by remember(savedModel) { mutableStateOf(savedModel ?: SettingsStore.DEFAULT_MODEL) }
    var showKey by remember { mutableStateOf(false) }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }

    ScreenScaffold(
        title = "Settings",
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { SectionHeader("Setup checklist") }
            item {
                Text(
                    "Grant these so alarms and reminders fire on time, even in Doze and on the lock screen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(checks, key = { it.kind }) { check ->
                ListItem(
                    headlineContent = { Text(check.title) },
                    supportingContent = { Text(check.detail) },
                    leadingContent = {
                        if (check.ok) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = "Done", tint = MaterialTheme.colorScheme.primary)
                        } else {
                            Icon(Icons.Outlined.Warning, contentDescription = "Needs attention", tint = MaterialTheme.colorScheme.error)
                        }
                    },
                    trailingContent = {
                        if (!check.ok) {
                            FilledTonalButton(onClick = {
                                if (check.kind == CheckKind.NOTIFICATIONS) {
                                    askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    openSafely(context, check.fix)
                                }
                            }) { Text("Fix") }
                        }
                    },
                )
            }

            item { SectionHeader("Gemini assistant") }
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        label = { Text("API key") },
                        singleLine = true,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (showKey) "Hide key" else "Show key",
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model") },
                        supportingText = { Text("Default: ${SettingsStore.DEFAULT_MODEL}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            c.appScope.launch {
                                c.settings.save(key, model)
                                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("Save") }
                        OutlinedButton(onClick = {
                            openSafely(context, Intent(Intent.ACTION_VIEW, "https://aistudio.google.com/apikey".toUri()))
                        }) { Text("Get an API key") }
                    }
                    Text(
                        "The key is stored only on this phone. What you type in the assistant is sent to Google's Gemini API.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { SectionHeader("About") }
            item { ListItem(headlineContent = { Text("PixelHub") }, supportingContent = { Text("Version $version") }) }
        }
    }
}
