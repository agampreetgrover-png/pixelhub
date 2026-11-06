@file:OptIn(ExperimentalLayoutApi::class)

package com.agam.pixelhub.ui

import androidx.compose.ui.text.style.TextOverflow
import com.agam.pixelhub.ui.settings.rememberSetupChecks
import com.agam.pixelhub.container
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.LaunchedEffect
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
import com.agam.pixelhub.ui.assistant.AssistantScreen
import com.agam.pixelhub.ui.assistant.AssistantViewModel
import com.agam.pixelhub.ui.calendar.CalendarScreen
import com.agam.pixelhub.ui.expenses.ExpensesScreen
import com.agam.pixelhub.ui.health.HealthScreen
import com.agam.pixelhub.ui.home.HomeScreen
import com.agam.pixelhub.ui.motion.LocalReducedMotion
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.toggleSpec
import com.agam.pixelhub.ui.reminders.RemindersScreen
import com.agam.pixelhub.ui.people.PeopleScreen
import com.agam.pixelhub.ui.settings.SettingsScreen
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import com.agam.pixelhub.ui.notes.QuickNotesSection

/** Tab.name travels in MainActivity.EXTRA_TAB from notifications; keep the value names. */
enum class Tab(val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    HOME("Home", Icons.Outlined.Home, Icons.Filled.Home),
    REMINDERS("Reminders", Icons.Outlined.Notifications, Icons.Filled.Notifications),
    ALARMS("Alarms", Icons.Outlined.Alarm, Icons.Filled.Alarm),
    ASSISTANT("Assistant", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
    SETTINGS("Settings", Icons.Outlined.Settings, Icons.Filled.Settings),
    HEALTH("Health", Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
    EXPENSES("Money", Icons.Outlined.AccountBalanceWallet, Icons.Filled.AccountBalanceWallet),
    CALENDAR("Calendar", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
    PEOPLE("People", Icons.Outlined.People, Icons.Filled.People),
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
    // Pages opened from a page above the tabs go back to it (Assistant -> Settings -> Health,
    // back, back), and so do pages opened from a bar tab (Money -> People, back). Moving between
    // bar tabs starts over, so back from a bar tab goes Home.
    val stack = rememberSaveable(saver = TabStackSaver) { mutableStateListOf<String>() }
    // The tab this code last moved to; anything else means the activity switched tabs itself
    // (a notification tap), which starts a fresh trail.
    var navigated by rememberSaveable { mutableStateOf(tab.name) }
    fun go(to: Tab) {
        if (to == tab) return
        // Into a page above the tabs, remember where it was opened from (Money -> People, back,
        // is Money again). Bar tab to bar tab starts over, so the trail stays short.
        if (tab.isLayer || to.isLayer) stack.add(tab.name) else stack.clear()
        tab = to
        navigated = to.name
    }
    fun back() {
        val p = stack.removeLastOrNull()?.let { n -> Tab.entries.firstOrNull { it.name == n } }
        tab = p ?: Tab.HOME
        navigated = tab.name
    }
    BackHandler(enabled = tab != Tab.HOME) { back() }

    // The navigation drawer, opened from Home's menu button (top left). Swiping only closes it,
    // so it never fights the horizontal swipes on Home's cards.
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    // Counts as open from the moment it starts opening, so Back mid-animation closes the drawer
    // instead of the app, and a tap on the scrim works straight away.
    val drawerShown = drawer.isOpen || drawer.targetValue == DrawerValue.Open
    BackHandler(enabled = drawerShown) { scope.launch { drawer.close() } }
    LaunchedEffect(tab) {
        if (tab.name != navigated) {
            // Opened from outside (a notification): drop the old trail and the drawer.
            stack.clear()
            navigated = tab.name
            drawer.snapTo(DrawerValue.Closed)
        }
    }
    // Each page keeps its own saved state (scroll, drafts, an open sheet) while you visit others.
    val pages = rememberSaveableStateHolder()

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = drawerShown,
        scrimColor = Color.Black.copy(alpha = 0.45f),
        drawerContent = {
            // The counts behind the red badge on Home's menu button, on the items they belong to.
            val reminders by LocalContext.current.container.reminders.all
                .collectAsStateWithLifecycle(initialValue = emptyList())
            val checks = rememberSetupChecks()
            // Re-read each time the drawer opens, so it counts what's overdue now, like Home's badge.
            val nowMs = remember(drawer.targetValue, reminders) { System.currentTimeMillis() }
            val badges = mapOf(
                Tab.REMINDERS to reminders.count { !it.done && it.triggerAt < nowMs },
                Tab.SETTINGS to checks.count { !it.ok },
            )
            NavDrawer(selected = tab, badges = badges, drawerOpen = drawerShown, onPick = { picked ->
                scope.launch { drawer.close() }
                if (picked != tab) go(picked)
            })
        },
    ) {
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
              pages.SaveableStateProvider(t.name) {
                when (t) {
                    Tab.HOME -> HomeScreen(
                        onNavigate = { go(it) },
                        assistant = assistant,
                        onOpenMenu = { scope.launch { drawer.open() } },
                    )
                    Tab.REMINDERS -> RemindersScreen()
                    Tab.ALARMS -> AlarmsScreen()
                    Tab.ASSISTANT -> AssistantScreen(assistant, onBack = { back() })
                    Tab.SETTINGS -> SettingsScreen(onBack = { back() }, onOpen = { go(it) })
                    Tab.HEALTH -> HealthScreen()
                    Tab.EXPENSES -> ExpensesScreen(onOpenPeople = { go(Tab.PEOPLE) })
                    Tab.PEOPLE -> PeopleScreen(onBack = { back() })
                    Tab.CALENDAR -> CalendarScreen(onBack = { back() })
                }
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
                enter = if (reduced) fadeIn(tween(150)) else slideInVertically(tween(320, easing = Motion.EaseOut)) { it } + fadeIn(),
                exit = if (reduced) fadeOut(tween(100)) else slideOutVertically(tween(200, easing = Motion.EaseOut)) { it } + fadeOut(tween(150)),
            ) {
                // Keeps a half-typed question while you visit another page.
                pages.SaveableStateProvider("assistant_bar") {
                    AssistantBar(
                        onSubmit = { text ->
                            assistant.send(text)
                            go(Tab.ASSISTANT)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 10.dp),
                    )
                }
            }
            AnimatedVisibility(
                visible = !tab.isLayer && !typing,
                enter = if (reduced) fadeIn(tween(150)) else slideInVertically(tween(320, easing = Motion.EaseOut)) { it * 2 } + fadeIn(),
                exit = if (reduced) fadeOut(tween(100)) else slideOutVertically(tween(200, easing = Motion.EaseOut)) { it * 2 } + fadeOut(tween(150)),
            ) {
                TabBar(selected = tab, onSelect = { go(it) })
            }
        }
    }
    }
}

/** Drawer order: the everyday places first, then the pages that open above them. */
private val DrawerMain = listOf(Tab.HOME, Tab.REMINDERS, Tab.ALARMS, Tab.EXPENSES, Tab.HEALTH, Tab.CALENDAR)
private val DrawerMore = listOf(Tab.ASSISTANT, Tab.SETTINGS)

/** The left-side navigation drawer: every page in the app, the current one highlighted, then quick notes. */
@Composable
private fun NavDrawer(selected: Tab, badges: Map<Tab, Int>, drawerOpen: Boolean, onPick: (Tab) -> Unit) {
    val c = Hub.colors
    val itemColors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = c.well,
        unselectedContainerColor = Color.Transparent,
        selectedIconColor = c.ink,
        unselectedIconColor = c.inkMuted,
        selectedTextColor = c.ink,
        unselectedTextColor = c.ink,
    )
    @Composable
    fun Item(t: Tab) {
        val isSelected = t == selected
        NavigationDrawerItem(
            label = { Text(t.label, style = HubType.button) },
            icon = { Icon(if (isSelected) t.selectedIcon else t.icon, contentDescription = null) },
            badge = (badges[t] ?: 0).takeIf { it > 0 }?.let { n -> { CountBadge(n, c.danger) } },
            selected = isSelected,
            onClick = { onPick(t) },
            colors = itemColors,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerContainerColor = c.raised,
        drawerContentColor = c.ink,
    ) {
        // imePadding: while typing a note, the drawer scrolls the field up above the keyboard.
        Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
            Text(
                "PixelHub",
                style = HubType.title,
                color = c.ink,
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
            )
            DrawerMain.forEach { Item(it) }
            Text(
                "More",
                style = HubType.small,
                color = c.inkMuted,
                modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 8.dp),
            )
            DrawerMore.forEach { Item(it) }
            Spacer(Modifier.height(12.dp))
            QuickNotesSection(drawerOpen = drawerOpen)
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
        return (slideInVertically(tween(300, easing = Motion.Drawer)) { it / 5 } + fadeIn(tween(220)) +
            scaleIn(tween(300, easing = Motion.Drawer), initialScale = 0.98f)) togetherWith
            (fadeOut(tween(180)) + scaleOut(tween(300), targetScale = 0.96f))
    }
    if (from.isLayer) {
        return (fadeIn(tween(260, delayMillis = 60)) + scaleIn(tween(320, easing = Motion.EaseOut), initialScale = 0.96f)) togetherWith
            (slideOutVertically(tween(260, easing = Motion.EaseOut)) { it / 5 } + fadeOut(tween(200)))
    }
    val dir = if (BarTabs.indexOf(to) > BarTabs.indexOf(from)) 1 else -1
    // Quick, since tabs are switched many times a day: the new screen glides in a short way while
    // the old one simply fades, so two moving screens never overlap.
    return (slideInHorizontally(tween(220, easing = Motion.EaseOut)) { w -> dir * w / 12 } + fadeIn(tween(160, delayMillis = 30))) togetherWith
        fadeOut(tween(90))
}

/**
 * The floating tab bar: a black pill of round buttons. The selected one opens into a white
 * pill with its name; the others stay as dark discs.
 */
@Composable
private fun TabBar(selected: Tab, onSelect: (Tab) -> Unit) {
    val c = Hub.colors
    val haptics = LocalHapticFeedback.current
    val reduced = LocalReducedMotion.current
    Row(
        Modifier
            .navigationBarsPadding()
            // Never wider than the screen: at large font sizes the selected label shortens instead
            // of pushing the last tab out of the pill.
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .clip(CircleShape)
            .background(c.chrome)
            .padding(6.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BarTabs.forEach { t ->
            val isSel = t == selected
            val bg by animateColorAsState(if (isSel) c.onChrome else c.chromeWell, Motion.toggleSpec(), label = "tabBg")
            val fg by animateColorAsState(if (isSel) c.chrome else c.onChrome, Motion.toggleSpec(), label = "tabFg")
            Row(
                Modifier
                    .then(if (isSel) Modifier.weight(1f, fill = false) else Modifier)
                    .height(48.dp)
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
                    enter = if (reduced) fadeIn(tween(120)) else expandHorizontally(Motion.snappy()) + fadeIn(tween(180, delayMillis = 60)),
                    exit = if (reduced) fadeOut(tween(80)) else shrinkHorizontally(Motion.snappy()) + fadeOut(tween(90)),
                ) {
                    Row {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            t.label,
                            style = HubType.button,
                            color = fg,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                }
            }
        }
    }
}

/** Saves the back trail of page names. */
private val TabStackSaver = listSaver<SnapshotStateList<String>, String>(
    save = { it.toList() },
    restore = { mutableStateListOf<String>().apply { addAll(it) } },
)
