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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.container
import com.agam.pixelhub.data.AiProvider
import com.agam.pixelhub.data.SettingsStore
import com.agam.pixelhub.ui.HubSwitch
import com.agam.pixelhub.ui.HubTextField
import com.agam.pixelhub.ui.PrimaryButton
import com.agam.pixelhub.ui.QuietButton
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.openSafely
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * The assistant's settings as one ordinary card: which AI answers, the automatic fallback,
 * and each provider's key and model. Optional; nothing else in PixelHub needs it.
 */
@Composable
fun AssistantAiSection(modifier: Modifier = Modifier) {
    val c = LocalContext.current.container
    val colors = Hub.colors
    val reduced = LocalReducedMotion.current
    val provider by c.settings.aiProvider.collectAsStateWithLifecycle(initialValue = AiProvider.GEMINI)
    val fallback by c.settings.aiFallback.collectAsStateWithLifecycle(initialValue = true)
    val geminiKey by c.settings.apiKey.collectAsStateWithLifecycle(initialValue = null)
    val grokKey by c.settings.grokApiKey.collectAsStateWithLifecycle(initialValue = null)
    var editing by remember { mutableStateOf<AiProvider?>(null) }
    val shown = editing ?: provider
    val forms = rememberSaveableStateHolder()

    fun hasKey(p: AiProvider) = when (p) {
        AiProvider.GEMINI -> !geminiKey.isNullOrBlank()
        AiProvider.GROK -> !grokKey.isNullOrBlank()
    }

    SettingsCard(modifier) {
        SettingRow(
            icon = Icons.Outlined.AutoAwesome,
            title = "Answers come from",
            meta = "Optional. Everything in PixelHub also works by hand.",
        )
        ProviderSwitch(
            selected = provider,
            onSelect = { p ->
                editing = null
                c.appScope.launch { c.settings.setAiProvider(p) }
            },
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 6.dp),
        )
        SettingRow(
            icon = Icons.Outlined.Sync,
            title = "Use the other AI if one hits its limit",
            meta = "Retries on ${provider.other().label} when ${provider.label} is busy or out of quota.",
            onClick = { c.appScope.launch { c.settings.setAiFallback(!fallback) } },
            role = Role.Switch,
            trailing = {
                HubSwitch(
                    checked = fallback,
                    onCheckedChange = { on -> c.appScope.launch { c.settings.setAiFallback(on) } },
                    accent = colors.ink,
                    contentDescription = "Use the other AI if one hits its limit",
                )
            },
        )

        Column(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Keys for each provider; tap the other name to edit its key without switching.
            Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProvider.entries.forEach { p ->
                    KeyTab(
                        label = "${p.shortName} key",
                        selected = p == shown,
                        saved = hasKey(p),
                        onClick = { editing = p },
                    )
                }
            }
            AnimatedContent(
                targetState = shown,
                transitionSpec = {
                    if (reduced) {
                        fadeIn(tween(150)) togetherWith fadeOut(tween(100))
                    } else {
                        val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                        (fadeIn(tween(200, delayMillis = 40)) + slideInHorizontally(tween(260, easing = Motion.EaseOut)) { dir * it / 10 }) togetherWith
                            (fadeOut(tween(120)) + slideOutHorizontally(tween(200, easing = Motion.EaseOut)) { -dir * it / 10 })
                    }
                },
                label = "keyForm",
            ) { p ->
                // Each form keeps its own unsaved text while the other is shown.
                forms.SaveableStateProvider(p.name) {
                    when (p) {
                        AiProvider.GEMINI -> GeminiForm()
                        AiProvider.GROK -> GrokForm()
                    }
                }
            }
            Text(
                "Keys are stored only on this phone. What you type in the assistant is sent to the provider that answers it.",
                style = HubType.small.copy(fontSize = 11.sp, lineHeight = 16.sp),
                color = colors.inkMuted,
            )
        }
    }
}

private val AiProvider.shortName get() = when (this) {
    AiProvider.GEMINI -> "Gemini"
    AiProvider.GROK -> "Groq"
}

private fun AiProvider.other() = if (this == AiProvider.GEMINI) AiProvider.GROK else AiProvider.GEMINI

/** A chip choosing which key form shows; a green dot once that key is saved. */
@Composable
private fun KeyTab(label: String, selected: Boolean, saved: Boolean, onClick: () -> Unit) {
    val c = Hub.colors
    val bg by animateColorAsState(if (selected) c.ink else c.raised2, tween(180), label = "keyTabBg")
    val fg by animateColorAsState(if (selected) c.ground else c.ink, tween(180), label = "keyTabFg")
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(bg)
            .pressable(onClick = onClick, pressedScale = 0.94f, role = Role.Tab)
            .semantics { this.selected = selected }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = HubType.button, color = fg, maxLines = 1)
        if (saved) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(7.dp).clip(CircleShape).background(c.ok))
        }
    }
}

/** Two-way segmented switch; the black half slides on a spring. */
@Composable
private fun ProviderSwitch(selected: AiProvider, onSelect: (AiProvider) -> Unit, modifier: Modifier = Modifier) {
    val colors = Hub.colors
    BoxWithConstraints(
        modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(colors.raised2).padding(4.dp),
    ) {
        val half = maxWidth / 2
        val x by animateDpAsState(if (selected == AiProvider.GEMINI) 0.dp else half, Motion.snappy(), label = "seg")
        Box(Modifier.offset(x = x).width(half).fillMaxHeight().clip(CircleShape).background(colors.ink))
        Row(Modifier.fillMaxSize().selectableGroup()) {
            AiProvider.entries.forEach { p ->
                val sel = p == selected
                val fg by animateColorAsState(if (sel) colors.ground else colors.ink, tween(180), label = "segFg")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .pressable(onClick = { onSelect(p) }, pressedScale = 0.96f, role = Role.RadioButton, showRipple = false)
                        .semantics { this.selected = sel },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(p.label, style = HubType.button, color = fg, maxLines = 1)
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
    // Unsaved edits (null = untouched). Saveable, so switching key tabs or scrolling the card away keeps them.
    var keyDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var modelDraft by rememberSaveable { mutableStateOf<String?>(null) }
    val key = keyDraft ?: savedKey.orEmpty()
    val model = modelDraft ?: savedModel ?: SettingsStore.DEFAULT_MODEL
    KeyForm(
        key = key, onKey = { keyDraft = it },
        model = model, onModel = { modelDraft = it },
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
    var keyDraft by rememberSaveable { mutableStateOf<String?>(null) }
    var modelDraft by rememberSaveable { mutableStateOf<String?>(null) }
    val key = keyDraft ?: savedKey.orEmpty()
    val model = modelDraft ?: savedModel ?: SettingsStore.DEFAULT_GROK_MODEL
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KeyForm(
            key = key, onKey = { keyDraft = it },
            model = model, onModel = { modelDraft = it },
            defaultModel = SettingsStore.DEFAULT_GROK_MODEL,
            keyUrl = "https://console.groq.com/keys",
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
        AnimatedVisibility(
            loadError != null,
            enter = expandVertically(tween(220, easing = Motion.EaseOut)) + fadeIn(tween(160)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
        ) {
            Text(loadError.orEmpty(), style = HubType.small, color = colors.danger)
        }
        AnimatedVisibility(
            models.isNotEmpty(),
            enter = expandVertically(tween(260, easing = Motion.EaseOut)) + fadeIn(tween(180)),
            exit = shrinkVertically(tween(180)) + fadeOut(tween(120)),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                models.forEach { m ->
                    val sel = m == model
                    val bg by animateColorAsState(if (sel) colors.ink else colors.raised2, tween(160), label = "model")
                    val fg by animateColorAsState(if (sel) colors.ground else colors.ink, tween(160), label = "modelFg")
                    Text(
                        m,
                        style = HubType.small,
                        color = fg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(bg)
                            .pressable(onClick = { modelDraft = m }, pressedScale = 0.94f, role = Role.RadioButton)
                            .semantics { selected = sel }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
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
