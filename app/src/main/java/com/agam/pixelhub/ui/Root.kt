package com.agam.pixelhub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agam.pixelhub.ui.alarms.AlarmsScreen
import com.agam.pixelhub.ui.apps.AppsScreen
import com.agam.pixelhub.ui.assistant.AssistantScreen
import com.agam.pixelhub.ui.assistant.AssistantViewModel
import com.agam.pixelhub.ui.home.HomeScreen
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.reminders.RemindersScreen
import com.agam.pixelhub.ui.settings.SettingsScreen
import com.agam.pixelhub.ui.health.HealthScreen
import com.agam.pixelhub.ui.expenses.ExpensesScreen
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.filled.AccountBalanceWallet
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubColors
import com.agam.pixelhub.ui.theme.HubType

/** Tab.name travels in MainActivity.EXTRA_TAB from notifications; keep the value names. */
enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("Home", Icons.Outlined.Home, Icons.Filled.Home),
    REMINDERS("Reminders", Icons.Outlined.NotificationsActive, Icons.Filled.NotificationsActive),
    ALARMS("Alarms", Icons.Outlined.Alarm, Icons.Filled.Alarm),
    APPS("Apps", Icons.Outlined.Apps, Icons.Filled.Apps),
    ASSISTANT("Assistant", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
    HEALTH("Health", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    EXPENSES("Expenses", Icons.Outlined.AccountBalanceWallet, Icons.Filled.AccountBalanceWallet),
}

/** Pages that open above the tabs (from Home or a notification) instead of living in the bar. */
private val Tab.isLayer get() = this == Tab.SETTINGS || this == Tab.HEALTH || this == Tab.EXPENSES

private val BarTabs = Tab.entries.filter { !it.isLayer }

/** The colour each tab is known by: alarms are dawn, reminders are sky, the rest ink. */
private fun HubColors.accentFor(tab: Tab): Color = when (tab) {
    Tab.ALARMS -> dawn
    Tab.REMINDERS -> sky
    Tab.ASSISTANT -> sky
    else -> ink
}

@Composable
fun PixelHubRoot(tabState: MutableState<Tab>) {
    var tab by tabState
    // Activity-scoped so Home's quick-ask box and the Assistant tab share one conversation.
    val assistant: AssistantViewModel = viewModel()
    val reduced = LocalReducedMotion.current
    BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }

    Scaffold(
        containerColor = Hub.colors.ground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(
                visible = !tab.isLayer,
                enter = slideInVertically(tween(320, easing = Motion.EaseOut)) { it } + fadeIn(),
                exit = slideOutVertically(tween(220, easing = Motion.EaseOut)) { it } + fadeOut(),
            ) {
                TabBar(selected = tab, onSelect = { tab = it })
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            AnimatedContent(
                targetState = tab,
                transitionSpec = { tabTransition(initialState, targetState, reduced) },
                label = "tabs",
            ) { t ->
                when (t) {
                    Tab.HOME -> HomeScreen(onNavigate = { tab = it }, assistant = assistant)
                    Tab.REMINDERS -> RemindersScreen()
                    Tab.ALARMS -> AlarmsScreen()
                    Tab.APPS -> AppsScreen()
                    Tab.ASSISTANT -> AssistantScreen(assistant, onOpenSettings = { tab = Tab.SETTINGS })
                    Tab.SETTINGS -> SettingsScreen(onBack = { tab = Tab.HOME })
                    Tab.HEALTH -> HealthScreen(onBack = { tab = Tab.HOME })
                    Tab.EXPENSES -> ExpensesScreen(onBack = { tab = Tab.HOME })
                }
            }
        }
    }
}

/**
 * Tabs slide in the direction you moved along the bar (a shared horizontal axis), so the
 * screens feel laid out side by side. Settings is a layer above: it rises and settles.
 */
private fun tabTransition(from: Tab, to: Tab, reduced: Boolean): ContentTransform {
    if (reduced) return fadeIn(tween(150)) togetherWith fadeOut(tween(120))
    if (to.isLayer && !from.isLayer) {
        return (slideInVertically(tween(380, easing = Motion.Drawer)) { it / 5 } + fadeIn(tween(260)) +
            scaleIn(tween(380, easing = Motion.Drawer), initialScale = 0.98f)) togetherWith
            (fadeOut(tween(180)) + scaleOut(tween(300), targetScale = 0.96f))
    }
    if (from.isLayer) {
        return (fadeIn(tween(260, delayMillis = 60)) + scaleIn(tween(320, easing = Motion.EaseOut), initialScale = 0.96f)) togetherWith
            (slideOutVertically(tween(260, easing = Motion.EaseOut)) { it / 5 } + fadeOut(tween(200)))
    }
    val dir = if (to.ordinal > from.ordinal) 1 else -1
    return (slideInHorizontally(tween(340, easing = Motion.EaseOut)) { w -> dir * w / 7 } + fadeIn(tween(240, delayMillis = 40))) togetherWith
        (slideOutHorizontally(tween(300, easing = Motion.EaseOut)) { w -> -dir * w / 10 } + fadeOut(tween(140)))
}

/**
 * The bottom bar: a pill of the tab's own colour slides under the selected icon on a spring,
 * the icon fills and lifts, and its label brightens.
 */
@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val c = Hub.colors
    val haptics = LocalHapticFeedback.current
    val index = BarTabs.indexOf(selected).coerceAtLeast(0)
    val pillColor by animateColorAsState(c.accentFor(selected).copy(alpha = if (c.isDark) 0.18f else 0.14f), tween(240), label = "pill")
    Column(Modifier.fillMaxWidth().background(c.raised).navigationBarsPadding()) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(76.dp)) {
            val slot = maxWidth / BarTabs.size
            val x by animateDpAsState(slot * index, Motion.snappy(), label = "pillX")
            // The sliding pill behind the selected icon.
            Box(
                Modifier
                    .offset(x = x + (slot - 60.dp) / 2, y = 10.dp)
                    .size(width = 60.dp, height = 32.dp)
                    .clip(CircleShape)
                    .background(pillColor),
            )
            Row(Modifier.fillMaxSize().selectableGroup()) {
                BarTabs.forEach { t ->
                    val isSel = t == selected
                    val tint by animateColorAsState(if (isSel) c.accentFor(t).let { if (t.accentIsInk()) c.ink else it } else c.inkMuted, tween(200), label = "tint")
                    val lift by animateFloatAsState(if (isSel) 1f else 0f, Motion.bouncy(), label = "lift")
                    Column(
                        Modifier
                            .width(slot)
                            .fillMaxHeight()
                            .selectable(
                                selected = isSel,
                                role = Role.Tab,
                                interactionSource = null,
                                indication = null,
                                onClick = {
                                    if (!isSel) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelect(t)
                                },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(14.dp))
                        Icon(
                            if (isSel) t.selectedIcon else t.icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(24.dp).graphicsLayer {
                                val s = 1f + 0.08f * lift
                                scaleX = s; scaleY = s
                                translationY = -2.dp.toPx() * lift
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            t.label,
                            style = HubType.small,
                            color = if (isSel) c.ink else c.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun Tab.accentIsInk() = this == Tab.HOME || this == Tab.APPS || isLayer
