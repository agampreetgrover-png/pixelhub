package com.agam.pixelhub.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.agam.pixelhub.R
import com.agam.pixelhub.ui.motion.pressable

/** Size of the assistant button; Root adds it to Home's bottom inset. */
val AssistantButtonSize = 56.dp

/**
 * The assistant, one tap away on Home: the app's own logo on a white disc, above the right end
 * of the tab bar. It opens the Assistant page, where you type or speak your request.
 */
@Composable
fun AssistantButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(AssistantButtonSize)
            // Press first, so the whole disc shrinks, not just the logo.
            .pressable(onClick = onClick, onClickLabel = "Open the assistant", showRipple = false)
            // Lifts the white disc off Home's light grey page.
            .shadow(3.dp, CircleShape)
            .clip(CircleShape)
            // White in both themes: the logo's black pill needs a light ground to show.
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        // The launcher foreground itself, so the button always matches the app icon. Its art fills
        // the middle 48 of 108 units; at 72dp the pill is 32dp wide. The empty margin is clipped.
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = "Ask PixelHub",
            modifier = Modifier.requiredSize(72.dp),
        )
    }
}
