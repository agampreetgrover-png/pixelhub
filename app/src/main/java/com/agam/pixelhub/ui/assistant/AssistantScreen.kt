@file:OptIn(ExperimentalLayoutApi::class)

package com.agam.pixelhub.ui.assistant

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.toPath
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.ai.ParsedCommand
import com.agam.pixelhub.data.Alarm
import com.agam.pixelhub.scheduling.Days
import com.agam.pixelhub.ui.AppIcon
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.Gutter
import com.agam.pixelhub.ui.PrimaryButton
import com.agam.pixelhub.ui.QuietButton
import com.agam.pixelhub.ui.ScreenScaffold
import com.agam.pixelhub.ui.alarms.AlarmEditorDialog
import com.agam.pixelhub.ui.motion.HubShapes
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.MorphShape
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.PolygonShape
import com.agam.pixelhub.ui.motion.Stagger
import com.agam.pixelhub.ui.motion.ThinkingIndicator
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.motion.rememberStagger
import com.agam.pixelhub.ui.motion.staggerIn
import com.agam.pixelhub.ui.reminders.ReminderDraft
import com.agam.pixelhub.ui.reminders.ReminderEditorDialog
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import java.time.LocalTime
import androidx.compose.material.icons.outlined.CurrencyRupee
import com.agam.pixelhub.expenses.Money
import com.agam.pixelhub.ui.expenses.color
import com.agam.pixelhub.ui.expenses.icon

private enum class Kind { REMINDER, ALARM, EXPENSE, APP, LINK }

private data class Suggestion(val text: String, val kind: Kind)

private val SUGGESTIONS = listOf(
    Suggestion("Remind me to call mom at 6pm", Kind.REMINDER),
    Suggestion("Wake me up at 6:30 on weekdays", Kind.ALARM),
    Suggestion("Remind me to water the plants every 3 days at 8am", Kind.REMINDER),
    Suggestion("Spent 250 on lunch", Kind.EXPENSE),
    Suggestion("Open Spotify", Kind.APP),
    Suggestion("Navigate to the nearest petrol station", Kind.LINK),
)

/** Big radius everywhere except the bottom corner nearest the speaker. */
private val MineShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 6.dp, bottomStart = 22.dp)
private val TheirsShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 6.dp)
private val BlockShape = RoundedCornerShape(28.dp)

@Composable
fun AssistantScreen(vm: AssistantViewModel, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val items by vm.items.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val hasKey by vm.hasApiKey.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<ChatItem.Action?>(null) }
    var listening by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Messages already in the conversation when the screen opens rise in with the stagger;
    // anything new springs in from its speaker's side. Nothing animates twice.
    val stagger = rememberStagger()
    val initialIds = remember { items.mapTo(HashSet()) { it.id } }
    val seen = remember { mutableSetOf<Any>() }

    LaunchedEffect(items.size, busy) {
        val count = listState.layoutInfo.totalItemsCount
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        listening = false
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { vm.send(it) }
    }

    fun submit() {
        if (input.isBlank() || busy) return
        vm.send(input)
        input = ""
    }

    ScreenScaffold(
        title = "Assistant",
        bottomBar = {
            InputBar(
                value = input,
                onValueChange = { input = it },
                busy = busy,
                listening = listening,
                onSend = { submit() },
                onMic = { listening = startSpeech(context) { speech.launch(it) } },
            )
        },
    ) { padding ->
        // When the input bar grows (keyboard opening, more lines), keep the newest message in view
        // by scrolling the list by the same amount.
        val bottomPx = with(LocalDensity.current) { padding.calculateBottomPadding().roundToPx() }
        val lastBottom = remember { intArrayOf(-1) }
        LaunchedEffect(bottomPx) {
            val previous = lastBottom[0]
            lastBottom[0] = bottomPx
            if (previous >= 0 && bottomPx > previous) listState.dispatchRawDelta((bottomPx - previous).toFloat())
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!hasKey) {
                item(key = "no-key", contentType = "notice") {
                    NoKeyNotice(
                        onOpenSettings = onOpenSettings,
                        modifier = Modifier.animateItem().staggerIn(stagger, "no-key", 0),
                    )
                }
            }
            if (items.isEmpty()) {
                item(key = "intro", contentType = "intro") {
                    Intro(stagger = stagger, onSuggest = { vm.send(it) }, modifier = Modifier.animateItem())
                }
            }
            itemsIndexed(
                items,
                key = { _, item -> item.id },
                contentType = { _, item ->
                    when (item) {
                        is ChatItem.User -> "user"
                        is ChatItem.Bot -> "bot"
                        is ChatItem.Action -> "action"
                    }
                },
            ) { index, item ->
                val fromEnd = item is ChatItem.User
                val enter = if (item.id in initialIds) {
                    Modifier.staggerIn(stagger, item.id, (items.lastIndex - index).coerceAtLeast(0))
                } else {
                    Modifier.sideEntrance(seen, item.id, fromEnd)
                }
                when (item) {
                    is ChatItem.User -> Bubble(item.text, mine = true, enter = enter, modifier = Modifier.animateItem())
                    is ChatItem.Bot -> Column(Modifier.animateItem()) {
                        Bubble(item.text, mine = false, isError = item.isError, enter = enter, modifier = Modifier)
                        item.via?.let { ViaNote(it) }
                    }
                    is ChatItem.Action -> Column(Modifier.animateItem()) {
                        ActionCard(
                            item = item,
                            onConfirm = { vm.confirm(item.id) },
                            onEdit = when (item.command) {
                                is ParsedCommand.CreateReminder, is ParsedCommand.CreateAlarm -> ({ editing = item })
                                else -> null
                            },
                            onCancel = { vm.cancel(item.id) },
                            enter = enter,
                            modifier = Modifier,
                        )
                        item.via?.let { ViaNote(it) }
                    }
                }
            }
            if (busy) {
                // A new key per request, so the bubble springs in each time.
                val key = "thinking-${items.lastOrNull()?.id ?: 0L}"
                item(key = key, contentType = "thinking") {
                    ThinkingBubble(enter = Modifier.sideEntrance(seen, key, fromEnd = false), modifier = Modifier.animateItem())
                }
            }
        }
    }

    editing?.let { item ->
        when (val cmd = item.command) {
            is ParsedCommand.CreateReminder -> ReminderEditorDialog(
                initial = ReminderDraft(cmd.title, cmd.notes, cmd.at, cmd.recurrence),
                isNew = true,
                onDismiss = { editing = null },
                onSave = { draft ->
                    editing = null
                    vm.saveReminder(item.id, draft)
                },
            )
            is ParsedCommand.CreateAlarm -> AlarmEditorDialog(
                initial = Alarm(hour = cmd.hour, minute = cmd.minute, daysMask = cmd.daysMask, label = cmd.label),
                isNew = true,
                onDismiss = { editing = null },
                onSave = { alarm ->
                    editing = null
                    vm.saveAlarm(item.id, alarm)
                },
                onDelete = null,
            )
            else -> Unit
        }
    }
}

/** Launches the system speech recogniser. Returns false (after telling the user) if there is none. */
private fun startSpeech(context: Context, launch: (Intent) -> Unit): Boolean {
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_PROMPT, "What should I do?")
    return try {
        launch(intent)
        true
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Speech recognition isn't available", Toast.LENGTH_SHORT).show()
        false
    }
}

/**
 * A new message's entrance: springs in 24dp from its speaker's side while growing from 0.92,
 * anchored at that side's bottom corner. Runs once per [key].
 */
private fun Modifier.sideEntrance(seen: MutableSet<Any>, key: Any, fromEnd: Boolean): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val progress = remember(key) { Animatable(if (key in seen) 1f else 0f) }
    val px = with(LocalDensity.current) { 24.dp.toPx() }
    LaunchedEffect(key) {
        if (progress.value < 1f) {
            seen += key
            progress.animateTo(1f, if (reduced) tween(160) else spring(dampingRatio = 0.78f, stiffness = 420f))
        }
    }
    graphicsLayer {
        val p = progress.value
        alpha = (p * 1.4f).coerceIn(0f, 1f)
        if (!reduced) {
            val s = 0.92f + 0.08f * p
            scaleX = s
            scaleY = s
            translationX = (1f - p) * px * (if (fromEnd) 1f else -1f)
            transformOrigin = TransformOrigin(if (fromEnd) 1f else 0f, 1f)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Input bar
// ---------------------------------------------------------------------------------------------

/**
 * A raised pill: the text field, the mic and, once there is something to send, the send button.
 * Pads itself by the keyboard (and the navigation bar, if nothing above has handled it), so the
 * keyboard never covers it.
 */
@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    busy: Boolean,
    listening: Boolean,
    onSend: () -> Unit,
    onMic: () -> Unit,
) {
    val c = Hub.colors
    Box(
        Modifier
            .fillMaxWidth()
            .background(c.ground)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clip(BlockShape)
                .background(c.raised)
                .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = HubType.body.copy(color = c.ink),
                cursorBrush = SolidColor(c.dawn),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .semantics { contentDescription = "Message" },
                decorationBox = { inner ->
                    Box(Modifier.heightIn(min = 44.dp).padding(vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text("Ask or give a command", style = HubType.body, color = c.inkMuted, maxLines = 1)
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(4.dp))
            MicButton(enabled = !busy, listening = listening, onClick = onMic)
            AnimatedVisibility(
                visible = value.isNotBlank(),
                enter = fadeIn(tween(140)) +
                    scaleIn(spring(dampingRatio = 0.6f, stiffness = 500f), initialScale = 0.85f) +
                    expandHorizontally(expandFrom = Alignment.Start),
                exit = fadeOut(tween(120)) +
                    scaleOut(tween(140), targetScale = 0.85f) +
                    shrinkHorizontally(tween(180, easing = Motion.EaseOut), shrinkTowards = Alignment.Start),
            ) {
                Row {
                    Spacer(Modifier.width(4.dp))
                    SendButton(enabled = !busy, onClick = onSend)
                }
            }
        }
    }
}

/** The mic. While the speech screen is launching, a soft ring pulses out from it. */
@Composable
private fun MicButton(enabled: Boolean, listening: Boolean, onClick: () -> Unit) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(listening, enter = fadeIn(tween(160)), exit = fadeOut(tween(220))) {
            val t = rememberInfiniteTransition(label = "micPulse")
            val wave by t.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(1100, easing = Motion.EaseOut), RepeatMode.Restart),
                label = "wave",
            )
            Canvas(Modifier.size(44.dp)) {
                val r = size.minDimension / 2f
                drawCircle(c.skySoft, radius = r)
                if (!reduced) {
                    drawCircle(
                        c.sky.copy(alpha = 0.6f * (1f - wave)),
                        radius = r * (1f + 0.5f * wave),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .pressable(onClick = onClick, enabled = enabled, pressedScale = 0.86f, onClickLabel = "Speak")
                .semantics { contentDescription = "Speak" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Mic,
                contentDescription = null,
                tint = when {
                    !enabled -> c.inkMuted
                    listening -> c.sky
                    else -> c.ink
                },
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * Send: blooms from a circle into a turning cookie as it appears; settles back to a dim circle
 * while a request is running.
 */
@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val morph = remember { Morph(HubShapes.circle, HubShapes.cookie) }
    val bloom = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { bloom.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 320f)) }
    val ready by animateFloatAsState(if (enabled) 1f else 0f, Motion.snappy(), label = "sendReady")
    val bg by animateColorAsState(if (enabled) c.dawn else c.raised2, tween(180), label = "sendBg")
    val fg by animateColorAsState(if (enabled) c.onDawn else c.inkMuted, tween(180), label = "sendFg")
    val progress = (bloom.value * ready).coerceIn(0f, 1f)
    val turn = (1f - bloom.value) * -60f
    Box(
        Modifier
            .size(44.dp)
            .clip(MorphShape(morph, progress, turn))
            .background(bg)
            .pressable(onClick = onClick, enabled = enabled, pressedScale = 0.86f, onClickLabel = "Send")
            .semantics { contentDescription = "Send" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Empty state and notices
// ---------------------------------------------------------------------------------------------

@Composable
private fun Intro(stagger: Stagger, onSuggest: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Hub.colors
    Column(
        modifier.fillMaxWidth().padding(top = 24.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BreathingMark(c.sky, Modifier.staggerIn(stagger, "mark", 0), size = 112.dp)
        Spacer(Modifier.height(28.dp))
        Text(
            "Ask for a reminder, an alarm or an app",
            style = HubType.section,
            color = c.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.staggerIn(stagger, "intro-title", 1),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Type or speak. Nothing happens until you confirm it.",
            style = HubType.body,
            color = c.inkMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.staggerIn(stagger, "intro-body", 2),
        )
        Spacer(Modifier.height(24.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            SUGGESTIONS.forEachIndexed { i, s ->
                SuggestionPill(
                    suggestion = s,
                    modifier = Modifier.staggerIn(stagger, "suggestion$i", i + 3, distance = 14f),
                    onClick = { onSuggest(s.text) },
                )
            }
        }
    }
}

/** A soft sky shape that slowly breathes between a cookie and a clover while turning. */
@Composable
private fun BreathingMark(color: Color, modifier: Modifier = Modifier, size: Dp = 112.dp) {
    val reduced = LocalReducedMotion.current
    val c = Hub.colors
    val morph = remember { Morph(HubShapes.cookie, HubShapes.clover) }
    val t = rememberInfiniteTransition(label = "mark")
    val phase by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(3600, easing = Motion.EaseInOut), RepeatMode.Reverse),
        label = "phase",
    )
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "spin")
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawCircle(c.skySoft, radius = w / 2f, center = Offset(w / 2f, h / 2f))
        val path = morph.toPath(if (reduced) 0f else phase, android.graphics.Path())
        val m = android.graphics.Matrix()
        if (!reduced) m.postRotate(spin)
        m.postScale(w * 0.34f, h * 0.34f)
        m.postTranslate(w / 2f, h / 2f)
        path.transform(m)
        drawPath(path.asComposePath(), color)
    }
}

@Composable
private fun SuggestionPill(suggestion: Suggestion, modifier: Modifier, onClick: () -> Unit) {
    val c = Hub.colors
    val (icon, tint) = kindLook(suggestion.kind)
    Row(
        modifier
            .pressable(onClick = onClick, pressedScale = 0.95f, showRipple = false)
            .clip(CircleShape)
            .background(c.raised)
            .padding(start = 14.dp, end = 18.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(suggestion.text, style = HubType.small, color = c.ink)
    }
}

@Composable
private fun kindLook(kind: Kind): Pair<ImageVector, Color> {
    val c = Hub.colors
    return when (kind) {
        Kind.REMINDER -> Icons.Outlined.NotificationsActive to c.sky
        Kind.ALARM -> Icons.Outlined.Alarm to c.dawn
        Kind.EXPENSE -> Icons.Outlined.CurrencyRupee to c.ink
        Kind.APP -> Icons.Outlined.Apps to c.ink
        Kind.LINK -> Icons.Outlined.Link to c.ink
    }
}

@Composable
private fun NoKeyNotice(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val c = Hub.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(BlockShape)
            .background(c.raised)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(PolygonShape(HubShapes.gem)).background(c.raised2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Key, contentDescription = null, tint = c.ink, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text("AI key needed", style = HubType.section, color = c.ink)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Add a Gemini or Grok API key in Settings to use the assistant.",
            style = HubType.body,
            color = c.inkMuted,
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Open Settings", onClick = onOpenSettings, modifier = Modifier.fillMaxWidth())
    }
}

// ---------------------------------------------------------------------------------------------
// Messages
// ---------------------------------------------------------------------------------------------

/** Small caption under a turn the backup AI answered. */
@Composable
private fun ViaNote(text: String) {
    Text(
        text,
        style = HubType.small,
        color = Hub.colors.inkMuted,
        modifier = Modifier.padding(start = 12.dp, top = 4.dp),
    )
}

@Composable
private fun Bubble(text: String, mine: Boolean, enter: Modifier, modifier: Modifier, isError: Boolean = false) {
    val c = Hub.colors
    Box(modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text,
            style = HubType.body,
            color = c.ink,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .then(enter)
                .clip(if (mine) MineShape else TheirsShape)
                .background(
                    when {
                        mine -> c.raised2
                        isError -> c.dangerSoft
                        else -> c.raised
                    },
                )
                .padding(horizontal = 16.dp, vertical = 11.dp),
        )
    }
}

@Composable
private fun ThinkingBubble(enter: Modifier, modifier: Modifier) {
    val c = Hub.colors
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Row(
            enter
                .clip(TheirsShape)
                .background(c.raised)
                .padding(start = 12.dp, end = 18.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ThinkingIndicator(Hub.colors.sky, size = 28.dp)
            Spacer(Modifier.width(10.dp))
            Text("Thinking", style = HubType.body, color = c.inkMuted)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Action cards
// ---------------------------------------------------------------------------------------------

private data class ActionLook(
    val icon: ImageVector,
    val accent: Color,
    val onAccent: Color,
    val soft: Color,
    val title: String,
    val titleStyle: TextStyle,
    val details: List<String>,
    /** OpenApp: show the app's own icon instead of the glyph. */
    val packageName: String? = null,
)

@Composable
private fun lookOf(item: ChatItem.Action): ActionLook {
    val context = LocalContext.current
    val c = Hub.colors
    return when (val cmd = item.command) {
        is ParsedCommand.CreateReminder -> ActionLook(
            icon = Icons.Outlined.NotificationsActive,
            accent = c.sky, onAccent = c.onSky, soft = c.skySoft,
            title = cmd.title,
            titleStyle = HubType.section,
            details = listOf(Fmt.whenText(context, cmd.at), cmd.recurrence.describe(), cmd.notes).filter { it.isNotBlank() },
        )
        is ParsedCommand.CreateAlarm -> ActionLook(
            icon = Icons.Outlined.Alarm,
            accent = c.dawn, onAccent = c.onDawn, soft = c.dawnSoft,
            title = Fmt.time(context, LocalTime.of(cmd.hour, cmd.minute)),
            titleStyle = HubType.clockMedium,
            details = listOf(cmd.label, Days.label(cmd.daysMask)).filter { it.isNotBlank() },
        )
        is ParsedCommand.OpenApp -> ActionLook(
            icon = Icons.Outlined.Apps,
            accent = c.ink, onAccent = c.ground, soft = c.raised2,
            title = "Open ${item.target?.label ?: cmd.query}",
            titleStyle = HubType.section,
            details = if (item.target == null) listOf("No matching app found") else emptyList(),
            packageName = item.target?.packageName,
        )
        is ParsedCommand.LogExpense -> ActionLook(
            icon = cmd.category.icon,
            accent = cmd.category.color(c), onAccent = c.ground, soft = cmd.category.color(c).copy(alpha = 0.16f),
            title = Money.format(cmd.amountPaise),
            titleStyle = HubType.clockMedium,
            details = listOfNotNull(
                cmd.category.label,
                cmd.note.takeIf { it.isNotBlank() },
                cmd.at?.let { Fmt.whenText(context, it) },
            ),
        )
        is ParsedCommand.OpenLink -> ActionLook(
            icon = Icons.Outlined.Link,
            accent = c.ink, onAccent = c.ground, soft = c.raised2,
            title = "Open link",
            titleStyle = HubType.section,
            details = listOf(cmd.uri),
        )
        else -> ActionLook(
            icon = Icons.Outlined.Info,
            accent = c.ink, onAccent = c.ground, soft = c.raised2,
            title = "Action",
            titleStyle = HubType.section,
            details = emptyList(),
        )
    }
}

/**
 * A proposed action, waiting for the user. Confirming collapses the buttons and swaps the
 * icon for a burst-shaped check that pops in; cancelling dims the whole card.
 */
@Composable
private fun ActionCard(
    item: ChatItem.Action,
    onConfirm: () -> Unit,
    onEdit: (() -> Unit)?,
    onCancel: () -> Unit,
    enter: Modifier,
    modifier: Modifier,
) {
    val c = Hub.colors
    val look = lookOf(item)
    val pending = item.status == ChatItem.Status.PENDING
    val done = item.status == ChatItem.Status.DONE
    val cancelled = item.status == ChatItem.Status.CANCELLED
    val dim by animateFloatAsState(if (cancelled) 0.5f else 1f, tween(260), label = "cardDim")
    val noteColor by animateColorAsState(if (done) c.ok else c.danger, tween(200), label = "noteColor")

    Column(
        modifier
            .fillMaxWidth()
            .then(enter)
            .graphicsLayer { alpha = dim }
            .clip(BlockShape)
            .background(c.raised)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            StatusMark(look = look, done = done)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(top = 2.dp)) {
                Text(look.title, style = look.titleStyle, color = c.ink)
                look.details.forEach { line ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        line,
                        style = HubType.body,
                        color = c.inkMuted,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = item.note.isNotBlank(),
            enter = fadeIn(tween(200)) + expandVertically(spring(dampingRatio = 0.9f, stiffness = 500f)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
        ) {
            Text(
                item.note,
                style = HubType.small,
                color = noteColor,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        AnimatedVisibility(
            visible = pending,
            enter = fadeIn(tween(160)) + expandVertically(),
            exit = fadeOut(tween(140)) + shrinkVertically(spring(dampingRatio = 0.9f, stiffness = 500f)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuietButton("Cancel", onClick = onCancel, color = c.inkMuted)
                if (onEdit != null) QuietButton("Edit", onClick = onEdit)
                PrimaryButton(
                    "Confirm",
                    onClick = onConfirm,
                    container = look.accent,
                    content = look.onAccent,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        AnimatedVisibility(
            visible = cancelled,
            enter = fadeIn(tween(200)) + expandVertically(),
            exit = fadeOut(tween(120)) + shrinkVertically(),
        ) {
            Text("Cancelled", style = HubType.small, color = c.inkMuted, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/**
 * The card's icon disc in the accent tone. When the action is done it shrinks away and a
 * burst-shaped check pops in on a bouncy spring, turning as it lands.
 */
@Composable
private fun StatusMark(look: ActionLook, done: Boolean) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val pop = remember { Animatable(if (done) 1f else 0f) }
    LaunchedEffect(done) {
        if (done) pop.animateTo(1f, if (reduced) tween(160) else Motion.bouncy()) else pop.snapTo(0f)
    }
    val burst = remember { PolygonShape(HubShapes.burst) }
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(48.dp)
                .graphicsLayer {
                    val p = pop.value.coerceIn(0f, 1f)
                    alpha = 1f - p
                    val s = 1f - 0.15f * p
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .background(look.soft),
            contentAlignment = Alignment.Center,
        ) {
            if (look.packageName != null) {
                AppIcon(look.packageName, size = 32.dp, fallback = look.icon)
            } else {
                Icon(look.icon, contentDescription = null, tint = look.accent, modifier = Modifier.size(24.dp))
            }
        }
        if (done) {
            Box(
                Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        val p = pop.value
                        alpha = (p * 1.5f).coerceIn(0f, 1f)
                        if (!reduced) {
                            val s = 0.85f + 0.15f * p
                            scaleX = s
                            scaleY = s
                            rotationZ = (1f - p) * -50f
                        }
                    }
                    .clip(burst)
                    .background(c.ok)
                    .semantics { contentDescription = "Done" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = c.ground, modifier = Modifier.size(24.dp))
            }
        }
    }
}
