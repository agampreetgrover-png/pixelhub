package com.agam.pixelhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.theme.Hub

/** Size of the assistant button; Root adds it to Home's bottom inset. */
val AssistantButtonSize = 56.dp

/**
 * The assistant, one tap away on Home: a violet disc with a chat bubble, above the right end of
 * the tab bar. It opens the Assistant page, where you type or speak your request.
 */
@Composable
fun AssistantButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Hub.colors
    Box(
        modifier
            .size(AssistantButtonSize)
            // Press first, so the whole disc shrinks, not just the icon.
            .pressable(onClick = onClick, onClickLabel = "Open the assistant", showRipple = false)
            .clip(CircleShape)
            .background(c.dawn),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Assistant, contentDescription = "Ask PixelHub", tint = c.onDawn, modifier = Modifier.size(24.dp))
    }
}
