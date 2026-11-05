package com.agam.pixelhub.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.agam.pixelhub.ui.Gutter
import com.agam.pixelhub.ui.PrimaryButton
import com.agam.pixelhub.ui.ScreenScaffold
import com.agam.pixelhub.ui.motion.Stagger
import com.agam.pixelhub.ui.motion.rememberStagger
import com.agam.pixelhub.ui.motion.staggerIn
import com.agam.pixelhub.ui.theme.Hub
import com.agam.pixelhub.ui.theme.HubType
import com.agam.pixelhub.ui.theme.PixelHubTheme

/**
 * Health Connect refuses to show its permission dialog unless the app has a screen
 * explaining how it uses health data (VIEW_PERMISSION_USAGE / HEALTH_PERMISSIONS in the manifest).
 * Health Connect also links here from its "PixelHub" page.
 */
class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PixelHubTheme {
                PrivacyPage(onClose = { finish() })
            }
        }
    }
}

@Composable
private fun PrivacyPage(onClose: () -> Unit) {
    val c = Hub.colors
    val stagger = rememberStagger()
    ScreenScaffold(
        title = "Health data",
        navigationIcon = {
            IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Close") }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gutter)
                .padding(top = 4.dp, bottom = 24.dp),
        ) {
            Row(Modifier.staggerIn(stagger, "lead", 0), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(c.ok.copy(alpha = if (c.isDark) 0.16f else 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.PrivacyTip, contentDescription = null, tint = c.ok, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    "Your activity stays on this phone.",
                    style = HubType.section,
                    color = c.ink,
                    modifier = Modifier.weight(1f),
                )
            }

            Part(
                stagger, 1,
                "What PixelHub reads",
                "Steps, distance, active calories and exercise sessions from Health Connect, written there by apps such as Fitbit and Google Fit.",
            )
            Part(
                stagger, 2,
                "What it does with them",
                "It shows today's activity on Home and the Health page, and checks the nudges you set up, such as a reminder to walk if you're under 6,000 steps by 18:00. " +
                    "Background access lets those checks run while PixelHub is closed.",
            )
            Part(
                stagger, 3,
                "What it never does",
                "PixelHub never writes health data. Nothing is uploaded or shared, and health data is never sent to the Gemini assistant.",
            )

            Column(
                Modifier
                    .padding(top = 28.dp)
                    .staggerIn(stagger, "revoke", 4)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(c.raised)
                    .padding(18.dp),
            ) {
                Text("Turning it off", style = HubType.bodyStrong, color = c.ink)
                Spacer(Modifier.height(4.dp))
                Text(
                    "You can revoke access at any time in Settings, then Security & privacy, Privacy controls, Health Connect, App permissions, PixelHub.",
                    style = HubType.body,
                    color = c.inkMuted,
                )
            }

            PrimaryButton(
                text = "Close",
                onClick = onClose,
                container = c.ok,
                content = c.ground,
                modifier = Modifier.padding(top = 28.dp).fillMaxWidth().staggerIn(stagger, "close", 5),
            )
        }
    }
}

@Composable
private fun Part(stagger: Stagger, index: Int, heading: String, body: String) {
    val c = Hub.colors
    Column(Modifier.padding(top = 28.dp).staggerIn(stagger, heading, index)) {
        Text(heading, style = HubType.section, color = c.ink)
        Spacer(Modifier.height(6.dp))
        Text(body, style = HubType.body, color = c.inkMuted)
    }
}
