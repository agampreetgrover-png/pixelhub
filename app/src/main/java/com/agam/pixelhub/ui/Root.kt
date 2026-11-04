package com.agam.pixelhub.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agam.pixelhub.ui.alarms.AlarmsScreen
import com.agam.pixelhub.ui.apps.AppsScreen
import com.agam.pixelhub.ui.assistant.AssistantScreen
import com.agam.pixelhub.ui.assistant.AssistantViewModel
import com.agam.pixelhub.ui.home.HomeScreen
import com.agam.pixelhub.ui.reminders.RemindersScreen
import com.agam.pixelhub.ui.settings.SettingsScreen

enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Outlined.Home),
    REMINDERS("Reminders", Icons.Outlined.NotificationsActive),
    ALARMS("Alarms", Icons.Outlined.Alarm),
    APPS("Apps", Icons.Outlined.Apps),
    ASSISTANT("Assistant", Icons.Outlined.AutoAwesome),
    SETTINGS("Settings", Icons.Outlined.Settings),
}

@Composable
fun PixelHubRoot(tabState: MutableState<Tab>) {
    var tab by tabState
    // Activity-scoped so Home's quick-ask box and the Assistant tab share one conversation.
    val assistant: AssistantViewModel = viewModel()
    BackHandler(enabled = tab != Tab.HOME) { tab = Tab.HOME }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (tab != Tab.SETTINGS) {
                NavigationBar {
                    Tab.entries.filter { it != Tab.SETTINGS }.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when (tab) {
                Tab.HOME -> HomeScreen(onNavigate = { tab = it }, assistant = assistant)
                Tab.REMINDERS -> RemindersScreen()
                Tab.ALARMS -> AlarmsScreen()
                Tab.APPS -> AppsScreen()
                Tab.ASSISTANT -> AssistantScreen(assistant, onOpenSettings = { tab = Tab.SETTINGS })
                Tab.SETTINGS -> SettingsScreen(onBack = { tab = Tab.HOME })
            }
        }
    }
}
