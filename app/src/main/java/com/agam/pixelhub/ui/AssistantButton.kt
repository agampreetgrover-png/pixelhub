package com.agam.pixelhub.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agam.pixelhub.ui.motion.pressable
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType

/** Height of the assistant button, the same as the tab bar; Root adds it to Home's bottom inset. */
val AssistantButtonHeight = 60.dp

/**
 * The assistant, one tap away on Home: a violet pill above the tab bar that opens the
 * Assistant page, where you type or speak your request.
 */
@Composable
fun AssistantButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Hub.colors
    Row(
        modifier
            .fillMaxWidth()
            .height(AssistantButtonHeight)
            // Press first, so the whole pill shrinks, not just its contents.
            .pressable(onClick = onClick, onClickLabel = "Open the assistant", showRipple = false)
            .clip(CircleShape)
            .background(c.dawn)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = c.onDawn, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "Ask PixelHub",
            style = HubType.bodyStrong,
            color = c.onDawn,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
