package com.agam.pixelhub.ui.settings

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.container
import com.agam.pixelhub.data.SettingsStore
import com.agam.pixelhub.health.HealthStatus
import com.agam.pixelhub.ui.health.grouped
import com.agam.pixelhub.ui.health.manageAccess
import com.agam.pixelhub.ui.health.openPrivacy
import com.agam.pixelhub.ui.health.rememberConnectAction
import com.agam.pixelhub.ui.health.rememberHealthAccess
import com.agam.pixelhub.ui.motion.Motion
import com.agam.pixelhub.ui.motion.StatusSwap
import com.agam.pixelhub.ui.motion.toggleSpec
import com.agam.pixelhub.ui.theme.Hub

private const val TRAILING_CHEVRON = "chevron"
private const val TRAILING_MANAGE = "manage"

/**
 * Health Connect in Settings: the connection, background reading (only once connected),
 * the step goal (opens the Health page, where it's changed) and the privacy note.
 */
@Composable
fun HealthSettingsCard(onOpenHealth: () -> Unit, modifier: Modifier = Modifier) {
    val c = Hub.colors
    val context = LocalContext.current
    val access = rememberHealthAccess()
    val connect = rememberConnectAction(access)
    val goal by context.container.settings.stepGoal.collectAsStateWithLifecycle(SettingsStore.DEFAULT_STEP_GOAL)
    val connected = access.connected
    val background = access.background
    // "Allow" on background reading asks again; if the sheet comes back without the background
    // permission (Android may not offer it twice), open Health Connect's own page and say where.
    val repo = context.container.health
    val contract = remember(repo) { repo.permissionContract() }
    val askBackground = rememberLauncherForActivityResult(contract) { granted ->
        if (repo.backgroundPermission !in granted) {
            Toast.makeText(context, "Turn on \"Access data in background\"", Toast.LENGTH_LONG).show()
            manageAccess(context)
        }
        access.refresh()
    }
    val allowBackground: () -> Unit = {
        try {
            askBackground.launch(repo.allPermissions)
        } catch (e: ActivityNotFoundException) {
            manageAccess(context)
        }
    }
    val tint by animateColorAsState(if (connected == true) c.ok else c.ink, Motion.toggleSpec(), label = "healthTint")

    SettingsCard(modifier) {
        val connectLabel = when (access.status) {
            HealthStatus.AVAILABLE -> "Connect"
            HealthStatus.NEEDS_UPDATE -> "Update"
            HealthStatus.UNAVAILABLE -> "Install"
        }
        // Once connected, or after a denial (Android stops asking), the row opens Health Connect's
        // own access page; otherwise it asks.
        val opensManage = connected == true || access.denied
        SettingRow(
            icon = Icons.Outlined.FavoriteBorder,
            title = "Health Connect",
            meta = when {
                connected == null -> "Checking"
                connected == true -> "Connected"
                access.status == HealthStatus.NEEDS_UPDATE -> "Needs an update"
                access.status == HealthStatus.UNAVAILABLE -> "Not installed on this phone"
                else -> "Not connected"
            },
            iconTint = tint,
            onClick = when {
                connected == null -> null
                opensManage -> ({ manageAccess(context) })
                else -> connect
            },
            onClickLabel = if (opensManage) "Manage access" else connectLabel,
            // The end control changes in place: the pill gives way to the chevron once connected.
            // After a denial the request does nothing, so the pill opens Health Connect instead.
            trailing = if (connected == null) null else {
                {
                    val end = when {
                        connected == true -> TRAILING_CHEVRON
                        access.denied && access.status == HealthStatus.AVAILABLE -> TRAILING_MANAGE
                        else -> connectLabel
                    }
                    StatusSwap(end) { state ->
                        when (state) {
                            TRAILING_CHEVRON -> Chevron()
                            TRAILING_MANAGE -> RowAction("Manage", onClick = { manageAccess(context) }, onClickLabel = "Manage Health Connect access")
                            else -> RowAction(state, onClick = connect, onClickLabel = "$state Health Connect")
                        }
                    }
                }
            },
        )
        Expandable(connected == true) {
            SettingRow(
                icon = Icons.Outlined.Sync,
                title = "Background reading",
                meta = if (background == true) "On · nudges work while PixelHub is closed" else "Off · nudges need it",
                onClick = if (background == false && access.status == HealthStatus.AVAILABLE) allowBackground else null,
                onClickLabel = "Allow background reading",
                trailing = if (background == false && access.status == HealthStatus.AVAILABLE) {
                    { RowAction("Allow", onClick = allowBackground, onClickLabel = "Allow background reading") }
                } else null,
            )
        }
        SettingRow(
            icon = Icons.AutoMirrored.Outlined.DirectionsWalk,
            title = "Daily step goal",
            meta = "${grouped(goal)} steps",
            onClick = onOpenHealth,
            onClickLabel = "Change on the Health page",
            trailing = { Chevron() },
        )
        SettingRow(
            icon = Icons.Outlined.PrivacyTip,
            title = "Privacy",
            meta = "Health data never leaves this phone",
            onClick = { openPrivacy(context) },
            onClickLabel = "Read the privacy note",
            trailing = { Chevron() },
        )
    }
}
