@file:OptIn(ExperimentalLayoutApi::class)

package com.agam.pixelhub.ui.settings

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.container
import com.agam.pixelhub.data.AiProvider
import com.agam.pixelhub.data.SettingsStore
import com.agam.pixelhub.ui.HubSwitch
import com.agam.pixelhub.ui.HubTextField
import com.agam.pixelhub.ui.QuietButton
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.StatusSwap
import com.agam.pixelhub.ui.motion.ThinkingIndicator
import com.agam.pixelhub.ui.motion.popEnter
import com.agam.pixelhub.ui.motion.popExit
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.motion.toggleSpec
import com.agam.pixelhub.ui.openSafely
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The assistant settings as one compact card: which AI answers (a switch in the row), the
 * automatic fallback, and one row per provider key that opens its form in place.
 * Optional; nothing else in PixelHub needs it.
 */
@Composable
fun AssistantAiSection(modifier: Modifier = Modifier) {
    val c = LocalContext.current.container
    val colors = Hub.colors
    val provider by c.settings.aiProvider.collectAsStateWithLifecycle(initialValue = AiProvider.GEMINI)
    val fallback by c.settings.aiFallback.collectAsStateWithLifecycle(initialValue = true)
    val geminiKey by c.settings.apiKey.collectAsStateWithLifecycle(initialValue = null)
    val grokKey by c.settings.grokApiKey.collectAsStateWithLifecycle(initialValue = null)
    val geminiModel by c.settings.model.collectAsStateWithLifecycle(initialValue = null)
    val grokModel by c.settings.grokModel.collectAsStateWithLifecycle(initialValue = null)
    // Which key form is open (by provider name); none at first so the card stays short.
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    // Keeps each form's unsaved text while it is closed.
    val forms = rememberSaveableStateHolder()

    fun hasKey(p: AiProvider) = when (p) {
        AiProvider.GEMINI -> !geminiKey.isNullOrBlank()
        AiProvider.GROK -> !grokKey.isNullOrBlank()
    }
    fun modelOf(p: AiProvider) = when (p) {
        AiProvider.GEMINI -> geminiModel ?: SettingsStore.DEFAULT_MODEL
        AiProvider.GROK -> grokModel ?: SettingsStore.DEFAULT_GROK_MODEL
    }

    SettingsCard(modifier) {
        SettingRow(
            icon = Icons.Outlined.AutoAwesome,
            title = "AI provider",
            meta = "Optional",
            trailing = {
                ProviderSwitch(
                    selected = provider,
                    onSelect = { p -> c.appScope.launch { c.settings.setAiProvider(p) } },
                    modifier = Modifier.padding(end = 4.dp),
                )
            },
        )
        SettingRow(
            icon = Icons.Outlined.Sync,
            title = "Fall back to ${provider.other().shortName}",
            meta = "If ${provider.shortName} hits a limit",
            onClick = { c.appScope.launch { c.settings.setAiFallback(!fallback) } },
            role = Role.Switch,
            trailing = {
                HubSwitch(
                    checked = fallback,
                    onCheckedChange = { on -> c.appScope.launch { c.settings.setAiFallback(on) } },
                    accent = colors.ink,
                    contentDescription = "Fall back to the other AI",
                    modifier = Modifier.padding(end = 8.dp),
                )
            },
        )
        AiProvider.entries.forEach { p ->
            val isOpen = open == p.name
            val keyTint by animateColorAsState(if (hasKey(p)) colors.ok else colors.ink, Motion.toggleSpec(), label = "keyTint")
            SettingRow(
                icon = Icons.Outlined.VpnKey,
                title = "${p.shortName} API key",
                meta = if (hasKey(p)) "Saved · ${modelOf(p)}" else "Not set",
                iconTint = keyTint,
                onClick = { open = if (isOpen) null else p.name },
                onClickLabel = if (isOpen) "Close" else "Edit ${p.shortName} key",
                trailing = { ExpandChevron(isOpen) },
            )
            Expandable(isOpen) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    forms.SaveableStateProvider(p.name) {
                        when (p) {
                            AiProvider.GEMINI -> GeminiForm()
                            AiProvider.GROK -> GrokForm()
                        }
                    }
                    Text(
                        "Stored only on this phone. What you ask the assistant goes to the provider that answers.",
                        style = HubType.small.copy(fontSize = 11.sp, lineHeight = 16.sp),
                        color = colors.inkMuted,
                    )
                }
            }
        }
    }
}

private val AiProvider.shortName get() = when (this) {
    AiProvider.GEMINI -> "Gemini"
    AiProvider.GROK -> "Groq"
}

private fun AiProvider.other() = if (this == AiProvider.GEMINI) AiProvider.GROK else AiProvider.GEMINI

/**
 * A small two-way switch; the ink half slides on a spring. Both halves are as wide as the
 * longer label (at least 66dp), so large font sizes don't clip it, and 48dp tall to the touch
 * around a 40dp track.
 */
@Composable
private fun ProviderSwitch(selected: AiProvider, onSelect: (AiProvider) -> Unit, modifier: Modifier = Modifier) {
    val colors = Hub.colors
    val reduced = LocalReducedMotion.current
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    val travel by animateFloatAsState(
        if (selected == AiProvider.GEMINI) 0f else 1f,
        if (reduced) snap() else Motion.snappy(),
        label = "seg",
    )
    Box(modifier.width(IntrinsicSize.Max).height(48.dp)) {
        Box(Modifier.matchParentSize().padding(vertical = 4.dp).clip(CircleShape).background(colors.raised2))
        Box(Modifier.matchParentSize().padding(horizontal = 3.dp, vertical = 7.dp)) {
            // Moves in the draw phase: one half's width, no relayout per frame.
            Box(
                Modifier
                    .fillMaxWidth(0.5f)
                    .fillMaxHeight()
                    .graphicsLayer { translationX = travel * size.width * direction }
                    .clip(CircleShape)
                    .background(colors.ink),
            )
        }
        Row(Modifier.fillMaxHeight().padding(horizontal = 3.dp).selectableGroup()) {
            AiProvider.entries.forEach { p ->
                val sel = p == selected
                val fg by animateColorAsState(if (sel) colors.ground else colors.ink, Motion.toggleSpec(), label = "segFg")
                Box(
                    Modifier
                        .weight(1f)
                        .widthIn(min = 66.dp)
                        .fillMaxHeight()
                        .pressable(onClick = { onSelect(p) }, pressedScale = 0.96f, role = Role.RadioButton, showRipple = false)
                        .semantics { this.selected = sel; contentDescription = p.label }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(p.shortName, style = HubType.button.copy(fontSize = 13.sp, lineHeight = 18.sp), color = fg, maxLines = 1)
                }
            }
        }
    }
}

/** True for [ms] after each new [trigger] (a save count), so a confirmation can show in place. */
@Composable
private fun rememberFlash(trigger: Int, ms: Long = 1500): Boolean {
    var on by remember { mutableStateOf(false) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            on = true
            delay(ms)
            on = false
        }
    }
    return on
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
    var saves by remember { mutableIntStateOf(0) }
    KeyForm(
        key = key, onKey = { keyDraft = it },
        model = model, onModel = { modelDraft = it },
        defaultModel = SettingsStore.DEFAULT_MODEL,
        keyUrl = "https://aistudio.google.com/apikey",
        // A blank key would wipe the saved one; an unchanged form has nothing to save.
        canSave = key.isNotBlank() && (key != savedKey.orEmpty() || model != (savedModel ?: SettingsStore.DEFAULT_MODEL)),
        saved = rememberFlash(saves),
        onSave = {
            c.appScope.launch {
                c.settings.save(key, model)
                saves++
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
    var saves by remember { mutableIntStateOf(0) }
    val reduced = LocalReducedMotion.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KeyForm(
            key = key, onKey = { keyDraft = it },
            model = model, onModel = { modelDraft = it },
            defaultModel = SettingsStore.DEFAULT_GROK_MODEL,
            keyUrl = "https://console.groq.com/keys",
            // A blank key would wipe the saved one; an unchanged form has nothing to save.
            canSave = key.isNotBlank() && (key != savedKey.orEmpty() || model != (savedModel ?: SettingsStore.DEFAULT_GROK_MODEL)),
            saved = rememberFlash(saves),
            onSave = {
                c.appScope.launch {
                    c.settings.saveGrok(key, model)
                    saves++
                }
            },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            AnimatedVisibility(loading, enter = popEnter(reduced), exit = popExit()) {
                ThinkingIndicator(colors.inkMuted, modifier = Modifier.padding(start = 12.dp), size = 16.dp)
            }
        }
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
                            .pressable(
                                onClick = {
                                    modelDraft = m
                                    // The chips sit below Save, so a pick is saved at once (the key
                                    // just listed these models, so it works); Save confirms it.
                                    if (key.isNotBlank()) {
                                        c.appScope.launch {
                                            c.settings.saveGrok(key, m)
                                            saves++
                                        }
                                    }
                                },
                                pressedScale = 0.94f,
                                role = Role.RadioButton,
                                showRipple = false,
                            )
                            .clip(CircleShape)
                            .background(bg)
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
    canSave: Boolean,
    saved: Boolean,
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
            SaveButton(saved = saved, enabled = canSave, onClick = onSave)
            QuietButton(
                "Get a key",
                icon = Icons.AutoMirrored.Outlined.OpenInNew,
                onClick = { openSafely(context, Intent(Intent.ACTION_VIEW, keyUrl.toUri())) },
            )
        }
    }
}

/**
 * Save, drawn like the app's primary pill. Dims while there is nothing to save; after a save
 * its label turns into a check and "Saved" for a moment instead of a toast.
 */
@Composable
private fun SaveButton(saved: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = Hub.colors
    val active = enabled || saved
    val bg by animateColorAsState(if (active) colors.ink else colors.raised2, Motion.toggleSpec(), label = "saveBg")
    val fg by animateColorAsState(if (active) colors.ground else colors.inkMuted, Motion.toggleSpec(), label = "saveFg")
    Box(
        Modifier
            .heightIn(min = 52.dp)
            .pressable(onClick = onClick, enabled = enabled && !saved, onClickLabel = "Save", showRipple = false)
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        StatusSwap(saved, alignment = Alignment.Center) { done ->
            if (done) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Saved", style = HubType.button, color = fg)
                }
            } else {
                Text("Save", style = HubType.button, color = fg)
            }
        }
    }
}
