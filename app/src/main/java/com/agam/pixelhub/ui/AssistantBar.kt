package com.agam.pixelhub.ui

import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import android.os.SystemClock
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType

/** Height of the assistant bar; Root adds it to Home's bottom inset. */
val AssistantBarHeight = 60.dp

/**
 * The assistant, always one tap away on Home: a violet pill sitting just above the tab bar.
 * Type and send, or tap the mic and speak; either way the request goes to the shared
 * conversation and the Assistant page opens to show the answer.
 */
@Composable
fun AssistantBar(onSubmit: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = Hub.colors
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val reduced = LocalReducedMotion.current
    var text by rememberSaveable { mutableStateOf("") }
    // When Send was last tapped. Sending swaps the button to the mic, so a quick double tap on
    // Send would land on the mic and open speech input as the page changes; ignore it.
    var sentAt by remember { mutableLongStateOf(0L) }

    fun submit(value: String) {
        val t = value.trim()
        if (t.isEmpty()) return
        sentAt = SystemClock.uptimeMillis()
        text = ""
        focus.clearFocus()
        onSubmit(t)
    }

    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { submit(it) }
    }
    fun listen() {
        if (SystemClock.uptimeMillis() - sentAt < 800L) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "What should I do?")
        try {
            speech.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Speech recognition isn't available", Toast.LENGTH_SHORT).show()
        }
    }

    Row(
        modifier
            .fillMaxWidth()
            .height(AssistantBarHeight)
            .clip(CircleShape)
            .background(c.dawn)
            .padding(start = 8.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(c.onDawn), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = c.dawn, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        // The field fills the whole middle of the pill, so a tap above or below the text still
        // lands on it; its text is centred inside.
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) {
                Text(
                    "Ask PixelHub anything",
                    style = HubType.bodyStrong,
                    color = c.onDawn.copy(alpha = 0.86f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = HubType.bodyStrong.copy(color = c.onDawn),
                cursorBrush = SolidColor(c.onDawn),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submit(text) }),
                modifier = Modifier.fillMaxWidth().fillMaxHeight().semantics { contentDescription = "Ask PixelHub" },
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { inner() }
                },
            )
        }
        Spacer(Modifier.width(8.dp))
        // The mic turns into send as soon as there's something to send.
        AnimatedContent(
            targetState = text.isBlank(),
            transitionSpec = {
                if (reduced) fadeIn(tween(120)) togetherWith fadeOut(tween(80))
                else (fadeIn(tween(140)) + scaleIn(tween(160), initialScale = 0.85f)) togetherWith
                    (fadeOut(tween(100)) + scaleOut(tween(100), targetScale = 0.85f))
            },
            label = "askAction",
        ) { empty ->
            Box(
                Modifier
                    .size(48.dp)
                    // Press first, so the whole disc shrinks, not just the icon.
                    .pressable(
                        onClick = { if (empty) listen() else submit(text) },
                        pressedScale = 0.9f,
                        onClickLabel = if (empty) "Speak to PixelHub" else "Send",
                        showRipple = false,
                    )
                    .clip(CircleShape)
                    .background(c.chrome),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (empty) Icons.Outlined.Mic else Icons.AutoMirrored.Filled.Send,
                    contentDescription = if (empty) "Speak" else "Send",
                    tint = c.onChrome,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
