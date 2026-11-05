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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Place
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.ai.ParsedCommand
import com.agam.pixelhub.data.Alarm
import com.agam.pixelhub.expenses.Money
import com.agam.pixelhub.scheduling.Days
import com.agam.pixelhub.ui.AppIcon
import com.agam.pixelhub.ui.BackButton
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.Gutter
import com.agam.pixelhub.ui.HubFab
import com.agam.pixelhub.ui.HubListRow
import com.agam.pixelhub.ui.PrimaryButton
import com.agam.pixelhub.ui.QuickAction
import com.agam.pixelhub.ui.ScreenScaffold
import com.agam.pixelhub.ui.alarms.AlarmEditorDialog
import com.agam.pixelhub.ui.expenses.color
import com.agam.pixelhub.ui.expenses.icon
import com.agam.pixelhub.ui.expenses.incomeSoft
import com.agam.pixelhub.ui.expenses.incomeText
import com.agam.pixelhub.ui.hubCard
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.Stagger
import com.agam.pixelhub.ui.motion.ThinkingIndicator
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.motion.rememberStagger
import com.agam.pixelhub.ui.motion.staggerIn
import com.agam.pixelhub.ui.reminders.ReminderDraft
import com.agam.pixelhub.ui.reminders.ReminderEditorDialog
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubColors
import com.agam.pixelhub.ui.theme.HubType
import java.time.LocalTime

private enum class Kind(val label: String) {
    REMINDER("Reminder"), ALARM("Alarm"), EXPENSE("Expense"), APP("Open app"), LINK("Route")
}

private fun Kind.glyph(): ImageVector = when (this) {
    Kind.REMINDER -> Icons.Outlined.NotificationsActive
    Kind.ALARM -> Icons.Outlined.Alarm
    Kind.EXPENSE -> Icons.Outlined.CurrencyRupee
    Kind.APP -> Icons.Outlined.Apps
    Kind.LINK -> Icons.Outlined.Place
}

/** A whole request, sent as is when tapped. */
private data class Suggestion(val text: String, val kind: Kind)

private val SUGGESTIONS = listOf(
    Suggestion("Remind me to call mom at 6pm", Kind.REMINDER),
    Suggestion("Wake me up at 6:30 on weekdays", Kind.ALARM),
    Suggestion("Spent 250 on lunch", Kind.EXPENSE),
    Suggestion("Open Spotify", Kind.APP),
    Suggestion("Navigate to a petrol station", Kind.LINK),
)

/** The start of a request: tapping one types it into the field so the user finishes it. */
private data class Starter(val kind: Kind, val prefill: String)

private val STARTERS = listOf(
    Starter(Kind.REMINDER, "Remind me to "),
    Starter(Kind.ALARM, "Wake me up at "),
    Starter(Kind.EXPENSE, "Spent "),
    Starter(Kind.APP, "Open "),
)

/** Big radius everywhere except the bottom corner nearest the speaker. */
private val MineShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 6.dp, bottomStart = 22.dp)
private val TheirsShape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 6.dp)
private val PillShape = RoundedCornerShape(28.dp)

private val MetaStyle = HubType.small.copy(fontSize = 11.sp, lineHeight = 16.sp)
private val LabelStyle = HubType.small.copy(fontWeight = FontWeight.Medium)

@Composable
fun AssistantScreen(vm: AssistantViewModel, onOpenSettings: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val items by vm.items.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val hasKey by vm.hasApiKey.collectAsStateWithLifecycle()
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue("")) }
    var editing by remember { mutableStateOf<ChatItem.Action?>(null) }
    var listening by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    // Messages already in the conversation when the screen opens rise in with the stagger;
    // anything new slides in from its speaker's side. Nothing animates twice.
    val stagger = rememberStagger()
    val initialIds = remember { items.mapTo(HashSet()) { it.id } }
    val seen = remember { mutableSetOf<Any>() }

    // The newest row, counted from the data: the list's layout info still describes the previous
    // frame when this runs, so it would scroll to the old last row (or nowhere on first open).
    LaunchedEffect(items.size, busy, hasKey) {
        val last = (if (!hasKey) 1 else 0) + (if (items.isEmpty()) 1 else 0) + items.size + (if (busy) 1 else 0) - 1
        if (last > 0) listState.animateScrollToItem(last)
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        listening = false
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { vm.send(it) }
    }

    fun submit() {
        if (input.text.isBlank() || busy) return
        vm.send(input.text)
        input = TextFieldValue("")
    }

    /** Types the start of a request into the field, cursor at the end, keyboard up. */
    fun prefill(text: String) {
        input = TextFieldValue(text, TextRange(text.length))
        focus.requestFocus()
        keyboard?.show()
    }

    fun speak() {
        listening = startSpeech(context) { speech.launch(it) }
    }

    ScreenScaffold(
        title = "Assistant",
        navigationIcon = { BackButton(onBack) },
        bottomBar = {
            InputBar(
                value = input,
                onValueChange = { input = it },
                focus = focus,
                busy = busy,
                listening = listening,
                onSend = { submit() },
                onMic = { speak() },
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
                    Intro(
                        stagger = stagger,
                        onSpeak = { speak() },
                        onStarter = { prefill(it) },
                        // Without a key a sent request can only fail, so a suggestion just fills the field.
                        onSuggest = { if (hasKey) vm.send(it) else prefill(it) },
                        modifier = Modifier.animateItem(),
                    )
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
                // A new key per request, so the bubble slides in each time.
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
 * A new message's entrance: slides 16dp in from its speaker's side while growing from 0.96,
 * anchored at that side's bottom corner. 220ms; a plain fade with reduced motion. Runs once per [key].
 */
private fun Modifier.sideEntrance(seen: MutableSet<Any>, key: Any, fromEnd: Boolean): Modifier = composed {
    val reduced = LocalReducedMotion.current
    val progress = remember(key) { Animatable(if (key in seen) 1f else 0f) }
    val px = with(LocalDensity.current) { 16.dp.toPx() }
    LaunchedEffect(key) {
        if (progress.value < 1f) {
            seen += key
            progress.animateTo(1f, tween(if (reduced) 150 else 220, easing = Motion.EaseOut))
        }
    }
    graphicsLayer {
        val p = progress.value
        alpha = p
        if (!reduced) {
            val s = 0.96f + 0.04f * p
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
 * A raised pill: the text field, the mic and a round send button. Pads itself by the keyboard
 * (and the navigation bar, if nothing above has handled it), so the keyboard never covers it.
 */
@Composable
private fun InputBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    focus: FocusRequester,
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
                .clip(PillShape)
                .background(c.raised)
                .padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = HubType.body.copy(color = c.ink),
                cursorBrush = SolidColor(c.ink),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Send,
                ),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .focusRequester(focus)
                    .semantics { contentDescription = "Message" },
                decorationBox = { inner ->
                    Box(Modifier.heightIn(min = 44.dp).padding(vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                        if (value.text.isEmpty()) {
                            Text("Ask or give a command", style = HubType.body, color = c.inkMuted, maxLines = 1)
                        }
                        inner()
                    }
                },
            )
            Spacer(Modifier.width(6.dp))
            MicButton(enabled = !busy, listening = listening, onClick = onMic)
            Spacer(Modifier.width(6.dp))
            SendButton(enabled = value.text.isNotBlank() && !busy, onClick = onSend)
        }
    }
}

/** The mic in a round well; it takes the reminder tint while the speech screen is launching. */
@Composable
private fun MicButton(enabled: Boolean, listening: Boolean, onClick: () -> Unit) {
    val c = Hub.colors
    val bg by animateColorAsState(if (listening) c.skySoft else c.well, tween(180), label = "micBg")
    val tint by animateColorAsState(
        when {
            !enabled -> c.inkMuted
            listening -> c.sky
            else -> c.ink
        },
        tween(180),
        label = "micTint",
    )
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(bg)
            .pressable(onClick = onClick, enabled = enabled, pressedScale = 0.9f, onClickLabel = "Speak")
            .semantics { contentDescription = "Speak" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Mic, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/**
 * Send: a round chrome button. In the dark theme the chrome tone would vanish into the pill, so
 * it inverts to ink there. Dims to the chip tone while there is nothing to send.
 */
@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    val c = Hub.colors
    val on = if (c.isDark) c.ink else c.chrome
    val onFg = if (c.isDark) c.ground else c.onChrome
    val bg by animateColorAsState(if (enabled) on else c.raised2, tween(180), label = "sendBg")
    val fg by animateColorAsState(if (enabled) onFg else c.inkMuted, tween(180), label = "sendFg")
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(bg)
            .pressable(onClick = onClick, enabled = enabled, pressedScale = 0.9f, onClickLabel = "Send")
            .semantics { contentDescription = "Send" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Empty state and notices
// ---------------------------------------------------------------------------------------------

/**
 * The empty conversation: a yellow hero card with a Speak button, a row of starters that type
 * the beginning of a request, and a list card of whole requests to try.
 */
@Composable
private fun Intro(
    stagger: Stagger,
    onSpeak: () -> Unit,
    onStarter: (String) -> Unit,
    onSuggest: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Hub.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Hero
        Column(
            Modifier
                .staggerIn(stagger, "hero", 0)
                .fillMaxWidth()
                .hubCard(c.sun)
                .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 20.dp),
        ) {
            Text("Assistant", style = LabelStyle, color = c.onSun.copy(alpha = 0.72f))
            Spacer(Modifier.height(4.dp))
            Text(
                "What should I do?",
                style = HubType.title.copy(fontSize = 24.sp, lineHeight = 32.sp),
                color = c.onSun,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Type, speak or tap a shortcut below. Nothing happens until you confirm it.",
                style = HubType.body.copy(fontSize = 14.sp, lineHeight = 21.sp),
                color = c.onSun.copy(alpha = 0.72f),
            )
            Spacer(Modifier.height(18.dp))
            HubFab(icon = Icons.Outlined.Mic, contentDescription = "Speak a request", label = "Speak", onClick = onSpeak)
        }

        // Starters: quick actions that fill the field
        Column(
            Modifier
                .staggerIn(stagger, "starters", 1)
                .fillMaxWidth()
                .hubCard(c.raised)
                .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
        ) {
            Text("Start with", style = HubType.section, color = c.ink, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
            Row(Modifier.fillMaxWidth()) {
                STARTERS.forEach { s ->
                    QuickAction(
                        icon = s.kind.glyph(),
                        label = s.kind.label,
                        onClick = { onStarter(s.prefill) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Whole requests
        Column(
            Modifier
                .staggerIn(stagger, "suggestions", 2)
                .fillMaxWidth()
                .hubCard(c.raised)
                .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        ) {
            Text("Try saying", style = HubType.section, color = c.ink, modifier = Modifier.padding(start = 12.dp, bottom = 4.dp))
            SUGGESTIONS.forEach { s ->
                HubListRow(
                    icon = s.kind.glyph(),
                    title = s.text,
                    meta = s.kind.label,
                    onClick = { onSuggest(s.text) },
                )
            }
        }
    }
}

@Composable
private fun NoKeyNotice(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    val c = Hub.colors
    Column(
        modifier
            .fillMaxWidth()
            .hubCard(c.raised)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(c.well), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Key, contentDescription = null, tint = c.ink, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text("AI key needed", style = HubType.section, color = c.ink)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Add a Gemini or Grok API key in Settings to use the assistant. The shortcuts below still fill in a request for you.",
            style = HubType.body.copy(fontSize = 14.sp, lineHeight = 21.sp),
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
        style = MetaStyle,
        color = Hub.colors.inkMuted,
        modifier = Modifier.padding(start = 12.dp, top = 4.dp),
    )
}

/**
 * A message. The user's sit in the black chrome tone (a step lighter in the dark theme, where
 * chrome would match the cards); replies sit in a raised card.
 */
@Composable
private fun Bubble(text: String, mine: Boolean, enter: Modifier, modifier: Modifier, isError: Boolean = false) {
    val c = Hub.colors
    val bg = when {
        mine -> if (c.isDark) c.chromeWell else c.chrome
        isError -> c.dangerSoft
        else -> c.raised
    }
    Box(modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            text,
            style = HubType.body,
            color = if (mine) c.onChrome else c.ink,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .then(enter)
                .clip(if (mine) MineShape else TheirsShape)
                .background(bg)
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
            ThinkingIndicator(c.ink, size = 24.dp)
            Spacer(Modifier.width(10.dp))
            Text("Thinking", style = HubType.body, color = c.inkMuted)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Action cards
// ---------------------------------------------------------------------------------------------

/** Every colour a draft card needs, so hero (violet / yellow) and plain cards share one layout. */
private data class CardPalette(
    val bg: Color,
    val fg: Color,
    val muted: Color,
    val well: Color,
    val iconTint: Color,
    val confirmBg: Color,
    val confirmFg: Color,
    val quietBg: Color,
    val quietFg: Color,
    val okNote: Color,
    val badNote: Color,
)

/** Reminders: the yellow card with dark text. */
private fun sunPalette(c: HubColors) = CardPalette(
    bg = c.sun, fg = c.onSun, muted = c.onSun.copy(alpha = 0.72f),
    well = c.onSun.copy(alpha = 0.10f), iconTint = c.onSun,
    confirmBg = c.onSun, confirmFg = c.sun,
    quietBg = c.onSun.copy(alpha = 0.10f), quietFg = c.onSun,
    okNote = c.onSun, badNote = c.onSun,
)

/**
 * Alarms: the violet card with white text. The dark theme's violet is a touch too light for
 * small white text (about 4:1), so the card darkens it by a fifth there (about 5.7:1).
 */
private fun dawnPalette(c: HubColors): CardPalette {
    val violet = if (c.isDark) lerp(c.dawn, Color.Black, 0.2f) else c.dawn
    return CardPalette(
        bg = violet, fg = c.onDawn, muted = c.onDawn,
        well = Color.Black.copy(alpha = 0.16f), iconTint = c.onDawn,
        confirmBg = c.onDawn, confirmFg = violet,
        quietBg = Color.Black.copy(alpha = 0.16f), quietFg = c.onDawn,
        okNote = c.onDawn, badNote = c.onDawn,
    )
}

/** Everything else: the plain raised card. */
private fun plainPalette(c: HubColors, well: Color = c.well, iconTint: Color = c.ink) = CardPalette(
    bg = c.raised, fg = c.ink, muted = c.inkMuted,
    well = well, iconTint = iconTint,
    confirmBg = c.ink, confirmFg = c.raised,
    quietBg = c.raised2, quietFg = c.ink,
    okNote = c.ok, badNote = c.danger,
)

private data class ActionLook(
    val kind: String,
    val icon: ImageVector,
    val palette: CardPalette,
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
            kind = "New reminder",
            icon = Icons.Outlined.NotificationsActive,
            palette = sunPalette(c),
            title = cmd.title,
            titleStyle = HubType.section.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
            details = listOf(Fmt.whenText(context, cmd.at), cmd.recurrence.describe(), cmd.notes).filter { it.isNotBlank() },
        )
        is ParsedCommand.CreateAlarm -> ActionLook(
            kind = "New alarm",
            icon = Icons.Outlined.Alarm,
            palette = dawnPalette(c),
            title = Fmt.time(context, LocalTime.of(cmd.hour, cmd.minute)),
            titleStyle = HubType.clockMedium,
            details = listOf(cmd.label, Days.label(cmd.daysMask)).filter { it.isNotBlank() },
        )
        is ParsedCommand.OpenApp -> ActionLook(
            kind = "Open app",
            icon = Icons.Outlined.Apps,
            palette = plainPalette(c),
            title = item.target?.label ?: cmd.query,
            titleStyle = HubType.section,
            details = if (item.target == null) listOf("No matching app found") else emptyList(),
            packageName = item.target?.packageName,
        )
        is ParsedCommand.LogExpense -> {
            val tone = if (cmd.isIncome) c.ok else cmd.category.color(c)
            ActionLook(
                kind = if (cmd.isIncome) "Income" else "Log expense",
                icon = cmd.category.icon,
                palette = if (cmd.isIncome) {
                    plainPalette(c, well = c.incomeSoft(), iconTint = c.ok).copy(fg = c.ok)
                } else {
                    plainPalette(c, well = tone.copy(alpha = 0.16f), iconTint = tone)
                },
                title = if (cmd.isIncome) incomeText(cmd.amountPaise) else Money.format(cmd.amountPaise),
                titleStyle = HubType.clockMedium,
                details = listOfNotNull(
                    cmd.category.label,
                    cmd.note.takeIf { it.isNotBlank() },
                    cmd.at?.let { Fmt.whenText(context, it) },
                ),
            )
        }
        is ParsedCommand.OpenLink -> ActionLook(
            kind = "Open link",
            icon = Icons.Outlined.Link,
            palette = plainPalette(c),
            title = "Open link",
            titleStyle = HubType.section,
            details = listOf(cmd.uri),
        )
        else -> ActionLook(
            kind = "Action",
            icon = Icons.Outlined.Info,
            palette = plainPalette(c),
            title = "Action",
            titleStyle = HubType.section,
            details = emptyList(),
        )
    }
}

/**
 * A proposed action, waiting for the user, as a card: what it is, the big figure, the details,
 * then Cancel / Edit / Confirm. Confirming collapses the buttons and swaps the icon for a check;
 * cancelling dims the whole card.
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
    val look = lookOf(item)
    val p = look.palette
    val pending = item.status == ChatItem.Status.PENDING
    val done = item.status == ChatItem.Status.DONE
    val cancelled = item.status == ChatItem.Status.CANCELLED
    val dim by animateFloatAsState(if (cancelled) 0.55f else 1f, tween(220), label = "cardDim")
    val noteColor by animateColorAsState(if (done) p.okNote else p.badNote, tween(180), label = "noteColor")

    Column(
        modifier
            .fillMaxWidth()
            .then(enter)
            .graphicsLayer { alpha = dim }
            .hubCard(p.bg)
            .padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = 2.dp)) {
                Text(look.kind, style = LabelStyle, color = p.muted)
                Spacer(Modifier.height(2.dp))
                Text(look.title, style = look.titleStyle, color = p.fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(12.dp))
            StatusMark(look = look, done = done)
        }
        if (look.details.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            look.details.forEach { line ->
                Text(
                    line,
                    style = HubType.small.copy(fontSize = 13.sp, lineHeight = 19.sp),
                    color = p.muted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = item.note.isNotBlank(),
            enter = fadeIn(tween(180)) + expandVertically(tween(220, easing = Motion.EaseOut)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(180, easing = Motion.EaseOut)),
        ) {
            Text(
                item.note,
                style = LabelStyle,
                color = noteColor,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        AnimatedVisibility(
            visible = pending,
            enter = fadeIn(tween(160)) + expandVertically(tween(220, easing = Motion.EaseOut)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(200, easing = Motion.EaseOut)),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CardButton("Cancel", bg = p.quietBg, fg = p.quietFg, onClick = onCancel)
                if (onEdit != null) CardButton("Edit", bg = p.quietBg, fg = p.quietFg, onClick = onEdit)
                CardButton("Confirm", bg = p.confirmBg, fg = p.confirmFg, onClick = onConfirm, modifier = Modifier.weight(1f))
            }
        }

        AnimatedVisibility(
            visible = cancelled,
            enter = fadeIn(tween(180)) + expandVertically(tween(220, easing = Motion.EaseOut)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(180, easing = Motion.EaseOut)),
        ) {
            Text("Cancelled", style = LabelStyle, color = p.muted, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

/** A pill button in the card's own colours. */
@Composable
private fun CardButton(text: String, bg: Color, fg: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .background(bg)
            .pressable(onClick = onClick, pressedScale = 0.95f, onClickLabel = text)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = HubType.button, color = fg, maxLines = 1)
    }
}

/**
 * The card's icon in a round well. When the action is done the icon gives way to a check on
 * an inverted disc (the card's text colour), which scales up from 0.7 in 220ms.
 */
@Composable
private fun StatusMark(look: ActionLook, done: Boolean) {
    val p = look.palette
    val reduced = LocalReducedMotion.current
    val pop by animateFloatAsState(
        if (done) 1f else 0f,
        tween(if (reduced) 150 else 220, easing = Motion.EaseOut),
        label = "statusPop",
    )
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(48.dp)
                .graphicsLayer { alpha = 1f - pop }
                .clip(CircleShape)
                .background(p.well),
            contentAlignment = Alignment.Center,
        ) {
            if (look.packageName != null) {
                AppIcon(look.packageName, size = 32.dp, fallback = look.icon)
            } else {
                Icon(look.icon, contentDescription = null, tint = p.iconTint, modifier = Modifier.size(22.dp))
            }
        }
        if (pop > 0f) {
            Box(
                Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        alpha = pop
                        if (!reduced) {
                            val s = 0.7f + 0.3f * pop
                            scaleX = s
                            scaleY = s
                        }
                    }
                    .clip(CircleShape)
                    .background(p.fg)
                    .semantics { contentDescription = "Done" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = p.bg, modifier = Modifier.size(24.dp))
            }
        }
    }
}
