@file:OptIn(ExperimentalLayoutApi::class)

package com.agam.pixelhub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agam.pixelhub.ui.alarms.AlarmsScreen
import com.agam.pixelhub.ui.apps.AppsScreen
import com.agam.pixelhub.ui.assistant.AssistantScreen
import com.agam.pixelhub.ui.assistant.AssistantViewModel
import com.agam.pixelhub.ui.calendar.CalendarScreen
import com.agam.pixelhub.ui.expenses.ExpensesScreen
import com.agam.pixelhub.ui.health.HealthScreen
import com.agam.pixelhub.ui.home.HomeScreen
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.reminders.RemindersScreen
import com.agam.pixelhub.ui.settings.SettingsScreen
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType

/** Tab.name travels in MainActivity.EXTRA_TAB from notifications; keep the value names. */
enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("Home", Icons.Outlined.Home, Icons.Filled.Home),
    REMINDERS("Reminders", Icons.Outlined.Notifications, Icons.Filled.Notifications),
    ALARMS("Alarms", Icons.Outlined.Alarm, Icons.Filled.Alarm),
    APPS("Apps", Icons.Outlined.Apps, Icons.Filled.Apps),
    ASSISTANT("Assistant", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
    HEALTH("Health", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    EXPENSES("Money", Icons.Outlined.AccountBalanceWallet, Icons.Filled.AccountBalanceWallet),
    CALENDAR("Calendar", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
}

/** The five places you work in day to day, in bar order. */
private val BarTabs = listOf(Tab.HOME, Tab.REMINDERS, Tab.ALARMS, Tab.EXPENSES, Tab.HEALTH)

/** Pages that open above the tabs (from Home's menu or a notification) instead of living in the bar. */
private val Tab.isLayer get() = this !in BarTabs

/** Bar 58dp, plus 12dp above and below it. The system navigation bar is added by each screen. */
private val NavBarSpace = 82.dp

/** Home also carries the assistant bar above the tab bar. */
private val HomeBarSpace = NavBarSpace + AssistantBarHeight + 10.dp

@Composable
fun PixelHubRoot(tabState: MutableState<Tab>) {
    var tab by tabState
    // Activity-scoped so Home and the Assistant page share one conversation.
    val assistant: AssistantViewModel = viewModel()
    val reduced = LocalReducedMotion.current
    // A page opened from another page (Assistant -> Settings) goes back to that page, not Home.
    var parent by rememberSaveable { mutableStateOf<String?>(null) }
    fun go(to: Tab) {
        parent = if (to.isLayer && tab.isLayer && to != tab) tab.name else null
        tab = to
    }
    fun back() {
        val p = parent?.let { n -> Tab.entries.firstOrNull { it.name == n } }
        parent = null
        tab = p ?: Tab.HOME
    }
    BackHandler(enabled = tab != Tab.HOME) { back() }

    Box(Modifier.fillMaxSize().background(Hub.colors.ground)) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = { tabTransition(initialState, targetState, reduced) },
            label = "tabs",
        ) { t ->
            val inset = when {
                t.isLayer -> 0.dp
                t == Tab.HOME -> HomeBarSpace
                else -> NavBarSpace
            }
            CompositionLocalProvider(LocalNavInset provides inset) {
                when (t) {
                    Tab.HOME -> HomeScreen(onNavigate = { go(it) }, assistant = assistant)
                    Tab.REMINDERS -> RemindersScreen()
                    Tab.ALARMS -> AlarmsScreen()
                    Tab.APPS -> AppsScreen(onBack = { back() })
                    Tab.ASSISTANT -> AssistantScreen(assistant, onOpenSettings = { go(Tab.SETTINGS) }, onBack = { back() })
                    Tab.SETTINGS -> SettingsScreen(onBack = { back() })
                    Tab.HEALTH -> HealthScreen()
                    Tab.EXPENSES -> ExpensesScreen()
                    Tab.CALENDAR -> CalendarScreen(onBack = { back() })
                }
            }
        }
        // The assistant bar (Home only) above the tab bar. While typing, the tab bar steps
        // aside and the assistant bar rides on top of the keyboard.
        val typing = WindowInsets.isImeVisible
        Column(
            Modifier.align(Alignment.BottomCenter).imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedVisibility(
                visible = tab == Tab.HOME,
                enter = slideInVertically(tween(320, easing = Motion.EaseOut)) { it } + fadeIn(),
                exit = slideOutVertically(tween(200, easing = Motion.EaseOut)) { it } + fadeOut(tween(150)),
            ) {
                AssistantBar(
                    onSubmit = { text ->
                        assistant.send(text)
                        go(Tab.ASSISTANT)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp),
                )
            }
            AnimatedVisibility(
                visible = !tab.isLayer && !typing,
                enter = slideInVertically(tween(320, easing = Motion.EaseOut)) { it * 2 } + fadeIn(),
                exit = slideOutVertically(tween(200, easing = Motion.EaseOut)) { it * 2 } + fadeOut(tween(150)),
            ) {
                TabBar(selected = tab, onSelect = { go(it) })
            }
        }
    }
}

/**
 * Tabs slide in the direction you moved along the bar (a shared horizontal axis), so the
 * screens feel laid out side by side. Pages above the tabs rise and settle.
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
    val dir = if (BarTabs.indexOf(to) > BarTabs.indexOf(from)) 1 else -1
    return (slideInHorizontally(tween(340, easing = Motion.EaseOut)) { w -> dir * w / 7 } + fadeIn(tween(240, delayMillis = 40))) togetherWith
        (slideOutHorizontally(tween(300, easing = Motion.EaseOut)) { w -> -dir * w / 10 } + fadeOut(tween(140)))
}

/**
 * The floating tab bar: a black pill of round buttons. The selected one opens into a white
 * pill with its name; the others stay as dark discs.
 */
@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val c = Hub.colors
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
            .clip(CircleShape)
            .background(c.chrome)
            .padding(6.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarTabs.forEach { t ->
            val isSel = t == selected
            val bg by animateColorAsState(if (isSel) c.onChrome else c.chromeWell, tween(220), label = "tabBg")
            val fg by animateColorAsState(if (isSel) c.chrome else c.onChrome, tween(220), label = "tabFg")
            Row(
                Modifier
                    .height(46.dp)
                    .clip(CircleShape)
                    .background(bg)
                    .selectable(
                        selected = isSel,
                        role = Role.Tab,
                        onClick = {
                            if (!isSel) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(t)
                        },
                    )
                    .semantics { contentDescription = t.label }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (isSel) t.selectedIcon else t.icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
                AnimatedVisibility(
                    visible = isSel,
                    enter = expandHorizontally(Motion.snappy()) + fadeIn(tween(180, delayMillis = 60)),
                    exit = shrinkHorizontally(Motion.snappy()) + fadeOut(tween(90)),
                ) {
                    Row {
                        Spacer(Modifier.width(8.dp))
                        Text(t.label, style = HubType.button, color = fg, maxLines = 1)
                        Spacer(Modifier.width(6.dp))
                    }
                }
            }
        }
    }
}
