package com.agam.pixelhub.ui.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.theme.AppTheme
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubColors
import com.agam.pixelhub.ui.theme.HubType
import com.agam.pixelhub.ui.theme.ThemeStore

/**
 * The theme picker: a grid of small previews, each drawn in its own colours (page, card,
 * the alarm and reminder colours, the black pill), so you can see a theme before you pick it.
 * The whole app changes the moment you tap one.
 */
@Composable
fun ThemeSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val selected by remember { ThemeStore.theme(context) }.collectAsState()
    val systemDark = isSystemInDarkTheme()

    SettingsCard(modifier) {
        SettingRow(
            icon = Icons.Outlined.Palette,
            title = "Theme",
            meta = if (selected == AppTheme.SYSTEM) {
                "System: ${if (systemDark) "Black" else "Light"} right now, following your phone"
            } else {
                selected.label
            },
        )
        Column(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp).selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppTheme.entries.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { t ->
                        ThemeTile(
                            theme = t,
                            selected = t == selected,
                            onClick = {
                                if (t != selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                ThemeStore.set(context, t)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ThemeTile(theme: AppTheme, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val ring by animateDpAsState(if (selected) 2.5.dp else 1.dp, Motion.snappy(), label = "ring")
    val pop by animateFloatAsState(if (selected && !reduced) 1f else 0.97f, Motion.bouncy(), label = "pop")
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(78.dp)
                .scale(pop)
                .clip(shape)
                .border(ring, if (selected) c.ink else c.inkMuted.copy(alpha = 0.35f), shape),
        ) {
            if (theme == AppTheme.SYSTEM) {
                // Half day, half night.
                Row(Modifier.fillMaxWidth().height(78.dp)) {
                    Preview(theme.day, Modifier.weight(1f))
                    Preview(theme.night, Modifier.weight(1f))
                }
            } else {
                Preview(theme.day, Modifier.fillMaxWidth())
            }
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(c.ink),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = c.ground, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            theme.label,
            style = HubType.small,
            color = if (selected) c.ink else c.inkMuted,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/** A tiny screen in [p]'s colours: a card holding the alarm and reminder colours, and the pill. */
@Composable
private fun Preview(p: HubColors, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxHeight().background(p.ground).padding(7.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(p.raised).padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.size(width = 18.dp, height = 18.dp).clip(RoundedCornerShape(5.dp)).background(p.dawn))
            Box(Modifier.size(width = 12.dp, height = 18.dp).clip(RoundedCornerShape(5.dp)).background(p.sun))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 2.dp)) {
                Box(Modifier.width(14.dp).height(4.dp).clip(CircleShape).background(p.ink))
                Box(Modifier.width(10.dp).height(4.dp).clip(CircleShape).background(p.inkMuted))
            }
        }
        Box(
            Modifier.align(Alignment.CenterHorizontally).width(30.dp).height(9.dp).clip(CircleShape).background(p.chrome),
        )
    }
}
