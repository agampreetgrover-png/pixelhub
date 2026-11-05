package com.agam.pixelhub.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.popEnter
import com.agam.pixelhub.ui.motion.popExit
import com.agam.pixelhub.ui.motion.toggleSpec
import com.agam.pixelhub.ui.theme.AppTheme
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubColors
import com.agam.pixelhub.ui.theme.HubType
import com.agam.pixelhub.ui.theme.ThemeStore

/**
 * The theme picker: one row of small previews, each drawn in its own colours (page, card,
 * the alarm and reminder colours, the pill), scrolled so the current theme is in view.
 * The whole app changes the moment you tap one.
 */
@Composable
fun ThemeSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val selected by remember { ThemeStore.theme(context) }.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (AppTheme.entries.indexOf(selected) - 1).coerceAtLeast(0))

    SettingsCard(modifier) {
        SettingRow(
            icon = Icons.Outlined.Palette,
            title = "Theme",
            meta = if (selected == AppTheme.SYSTEM) {
                "Follows your phone · ${if (systemDark) "Black" else "Light"} now"
            } else {
                selected.label
            },
        )
        LazyRow(
            state = list,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp).selectableGroup(),
            contentPadding = PaddingValues(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(AppTheme.entries, key = { it.name }) { t ->
                ThemeTile(
                    theme = t,
                    selected = t == selected,
                    onClick = {
                        if (t != selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        ThemeStore.set(context, t)
                    },
                    modifier = Modifier.width(78.dp),
                )
            }
        }
    }
}

@Composable
private fun ThemeTile(theme: AppTheme, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    val ring by animateDpAsState(if (selected) 2.dp else 1.dp, Motion.snappy(), label = "ring")
    // Unselected previews sit a touch smaller; under reduced motion every tile stays full size.
    val pop by animateFloatAsState(if (selected || reduced) 1f else 0.97f, Motion.bouncy(), label = "pop")
    // Press feedback from the selectable's own interactions, so the radio semantics stay intact.
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(
        if (pressed && !reduced) 0.96f else 1f,
        if (pressed) spring(dampingRatio = 0.9f, stiffness = 1400f) else Motion.snappy(),
        label = "press",
    )
    val labelColor by animateColorAsState(if (selected) c.ink else c.inkMuted, Motion.toggleSpec(), label = "label")
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .graphicsLayer { scaleX = press; scaleY = press }
            .clip(RoundedCornerShape(16.dp))
            .selectable(
                selected = selected,
                interactionSource = source,
                indication = ripple(),
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .scale(pop)
                .clip(shape)
                .border(ring, if (selected) c.ink else c.inkMuted.copy(alpha = 0.35f), shape),
        ) {
            if (theme == AppTheme.SYSTEM) {
                // Half day, half night.
                Row(Modifier.fillMaxWidth().height(54.dp)) {
                    Preview(theme.day, Modifier.weight(1f))
                    Preview(theme.night, Modifier.weight(1f))
                }
            } else {
                Preview(theme.day, Modifier.fillMaxWidth())
            }
            SelectedBadge(selected, Modifier.align(Alignment.TopEnd))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            theme.label,
            style = HubType.small,
            color = labelColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The check on the current theme's preview: pops in when picked, shrinks away when another is. */
@Composable
private fun SelectedBadge(visible: Boolean, modifier: Modifier = Modifier) {
    val c = Hub.colors
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(visible = visible, modifier = modifier, enter = popEnter(reduced), exit = popExit()) {
        Box(
            Modifier
                .padding(4.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(c.ink),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = c.ground, modifier = Modifier.size(11.dp))
        }
    }
}

/** A tiny screen in [p]'s colours: a card holding the alarm and reminder colours, and the pill. */
@Composable
private fun Preview(p: HubColors, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxHeight().background(p.ground).padding(5.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(p.raised).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Box(Modifier.size(width = 14.dp, height = 14.dp).clip(RoundedCornerShape(5.dp)).background(p.dawn))
            Box(Modifier.size(width = 9.dp, height = 14.dp).clip(RoundedCornerShape(5.dp)).background(p.sun))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 2.dp)) {
                Box(Modifier.width(12.dp).height(3.dp).clip(CircleShape).background(p.ink))
                Box(Modifier.width(8.dp).height(3.dp).clip(CircleShape).background(p.inkMuted))
            }
        }
        Box(
            Modifier.align(Alignment.CenterHorizontally).width(24.dp).height(7.dp).clip(CircleShape).background(p.chrome),
        )
    }
}
