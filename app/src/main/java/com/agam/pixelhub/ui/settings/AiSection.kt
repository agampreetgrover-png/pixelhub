@file:OptIn(ExperimentalLayoutApi::class)

package com.agam.pixelhub.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.container
import com.agam.pixelhub.data.AiProvider
import com.agam.pixelhub.data.SettingsStore
import com.agam.pixelhub.ui.Gutter
import com.agam.pixelhub.ui.HubSwitch
import com.agam.pixelhub.ui.HubTextField
import com.agam.pixelhub.ui.PrimaryButton
import com.agam.pixelhub.ui.QuietButton
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.openSafely
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Which AI answers the assistant, the automatic fallback, and each provider's key and model.
 * The key form under the switch follows the selected provider and slides between them.
 */
@Composable
fun AssistantAiSection(modifier: Modifier = Modifier) {
    val c = LocalContext.current.container
    val colors = Hub.colors
    val provider by c.settings.aiProvider.collectAsStateWithLifecycle(initialValue = AiProvider.GEMINI)
    val fallback by c.settings.aiFallback.collectAsStateWithLifecycle(initialValue = true)
    var editing by remember { mutableStateOf<AiProvider?>(null) }
    val shown = editing ?: provider

    Column(modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Answers come from", style = HubType.body, color = colors.inkMuted)
        ProviderSwitch(selected = provider, onSelect = { p ->
            editing = null
            c.appScope.launch { c.settings.setAiProvider(p) }
        })
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(colors.raised).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text("Use the other AI if one hits its limit", style = HubType.bodyStrong, color = colors.ink)
                Text("Retries on ${provider.other().label} when ${provider.label} is busy or out of quota.", style = HubType.small, color = colors.inkMuted)
            }
            HubSwitch(
                checked = fallback,
                onCheckedChange = { on -> c.appScope.launch { c.settings.setAiFallback(on) } },
                accent = colors.sky,
                contentDescription = "Use the other AI if one hits its limit",
            )
        }

        // Keys for each provider; tap the other name to edit its key without switching.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiProvider.entries.forEach { p ->
                val sel = p == shown
                val bg by animateColorAsState(if (sel) colors.raised2 else colors.ground, tween(180), label = "keyTab")
                Text(
                    "${p.shortName} key",
                    style = HubType.button,
                    color = if (sel) colors.ink else colors.inkMuted,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(bg)
                        .pressable(onClick = { editing = p })
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
        AnimatedContent(
            targetState = shown,
            transitionSpec = {
                val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                (fadeIn(tween(220, delayMillis = 60)) + androidx.compose.animation.slideInHorizontally(tween(320, easing = Motion.EaseOut)) { dir * it / 8 }) togetherWith
                    (fadeOut(tween(120)) + androidx.compose.animation.slideOutHorizontally(tween(260, easing = Motion.EaseOut)) { -dir * it / 8 })
            },
            label = "keyForm",
        ) { p ->
            when (p) {
                AiProvider.GEMINI -> GeminiForm()
                AiProvider.GROK -> GrokForm()
            }
        }
        Text(
            "Keys are stored only on this phone. What you type in the assistant is sent to the provider that answers it.",
            style = HubType.small, color = colors.inkMuted,
        )
    }
}

private val AiProvider.shortName get() = when (this) {
    AiProvider.GEMINI -> "Gemini"
    AiProvider.GROK -> "Grok"
}

private fun AiProvider.other() = if (this == AiProvider.GEMINI) AiProvider.GROK else AiProvider.GEMINI

/** Two-way segmented switch; the filled half slides on a spring. */
@Composable
private fun ProviderSwitch(selected: AiProvider, onSelect: (AiProvider) -> Unit) {
    val colors = Hub.colors
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(56.dp).clip(CircleShape).background(colors.raised).padding(4.dp),
    ) {
        val half = maxWidth / 2
        val x by animateDpAsState(if (selected == AiProvider.GEMINI) 0.dp else half, Motion.snappy(), label = "seg")
        Box(Modifier.offset(x = x).width(half).fillMaxHeight().clip(CircleShape).background(colors.sky))
        Row(Modifier.fillMaxSize().selectableGroup()) {
            AiProvider.entries.forEach { p ->
                val sel = p == selected
                val fg by animateColorAsState(if (sel) colors.onSky else colors.ink, tween(180), label = "segFg")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                        .selectable(selected = sel, role = Role.RadioButton, onClick = { onSelect(p) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(p.label, style = HubType.button, color = fg)
                }
            }
        }
    }
}

@Composable
private fun GeminiForm() {
    val context = LocalContext.current
    val c = context.container
    val savedKey by c.settings.apiKey.collectAsStateWithLifecycle(initialValue = null)
    val savedModel by c.settings.model.collectAsStateWithLifecycle(initialValue = null)
    var key by remember(savedKey) { mutableStateOf(savedKey.orEmpty()) }
    var model by remember(savedModel) { mutableStateOf(savedModel ?: SettingsStore.DEFAULT_MODEL) }
    KeyForm(
        key = key, onKey = { key = it },
        model = model, onModel = { model = it },
        defaultModel = SettingsStore.DEFAULT_MODEL,
        keyUrl = "https://aistudio.google.com/apikey",
        onSave = {
            c.appScope.launch {
                c.settings.save(key, model)
                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
            }
        },
    )
}

@Composable
private fun GrokForm() {
    val context = LocalContext.current
    val c = context.container
    val colors = Hub.colors
    val savedKey by c.settings.grokApiKey.collectAsStateWithLifecycle(initialValue = null)
    val savedModel by c.settings.grokModel.collectAsStateWithLifecycle(initialValue = null)
    var key by remember(savedKey) { mutableStateOf(savedKey.orEmpty()) }
    var model by remember(savedModel) { mutableStateOf(savedModel ?: SettingsStore.DEFAULT_GROK_MODEL) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KeyForm(
            key = key, onKey = { key = it },
            model = model, onModel = { model = it },
            defaultModel = SettingsStore.DEFAULT_GROK_MODEL,
            keyUrl = "https://console.x.ai",
            onSave = {
                c.appScope.launch {
                    c.settings.saveGrok(key, model)
                    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                }
            },
        )
        QuietButton(
            if (loading) "Loading models" else "Load models",
            enabled = key.isNotBlank() && !loading,
            onClick = {
                loading = true
                loadError = null
                c.appScope.launch {
                    try {
                        models = c.ai.listGrokModels(key.trim())
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        loadError = e.message ?: "Couldn't load models"
                    } finally {
                        loading = false
                    }
                }
            },
        )
        AnimatedVisibility(loadError != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Text(loadError.orEmpty(), style = HubType.small, color = colors.danger)
        }
        AnimatedVisibility(models.isNotEmpty(), enter = expandVertically(tween(280, easing = Motion.EaseOut)) + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                models.forEach { m ->
                    val sel = m == model
                    val bg by animateColorAsState(if (sel) colors.sky else colors.raised2, tween(160), label = "model")
                    Text(
                        m,
                        style = HubType.small,
                        color = if (sel) colors.onSky else colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clip(CircleShape).background(bg).pressable(onClick = { model = m }).padding(horizontal = 14.dp, vertical = 9.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyForm(
    key: String,
    onKey: (String) -> Unit,
    model: String,
    onModel: (String) -> Unit,
    defaultModel: String,
    keyUrl: String,
    onSave: () -> Unit,
) {
    val context = LocalContext.current
    var showKey by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HubTextField(
            value = key,
            onValueChange = onKey,
            label = "API key",
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        contentDescription = if (showKey) "Hide key" else "Show key",
                        tint = Hub.colors.inkMuted,
                    )
                }
            },
        )
        HubTextField(value = model, onValueChange = onModel, label = "Model", supportingText = "Default: $defaultModel")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Save", onClick = onSave)
            QuietButton(
                "Get a key",
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
                onClick = { openSafely(context, Intent(Intent.ACTION_VIEW, keyUrl.toUri())) },
            )
        }
    }
}
