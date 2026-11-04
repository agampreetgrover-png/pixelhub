package com.agam.pixelhub.alarm

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.agam.pixelhub.ui.Fmt
import com.agam.pixelhub.ui.theme.PixelHubTheme
import java.time.LocalTime

/** Full-screen alarm UI, shown over the lock screen. */
class AlarmRingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        val activity = this
        setContent {
            PixelHubTheme {
                val ringing by AlarmRingService.state.collectAsStateWithLifecycle()
                LaunchedEffect(ringing) { if (ringing == null) activity.finish() }
                ringing?.let {
                    RingScreen(
                        ringing = it,
                        onSnooze = { AlarmRingService.send(activity, AlarmRingService.ACTION_SNOOZE) },
                        onDismiss = { AlarmRingService.send(activity, AlarmRingService.ACTION_DISMISS) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RingScreen(ringing: AlarmRingService.Ringing, onSnooze: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Spacer(Modifier.height(48.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Outlined.Alarm, contentDescription = null, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    Fmt.time(context, LocalTime.of(ringing.hour, ringing.minute)),
                    style = MaterialTheme.typography.displayLarge,
                )
                Text(ringing.label.ifBlank { "Alarm" }, style = MaterialTheme.typography.headlineSmall)
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                    Text("Dismiss", style = MaterialTheme.typography.titleLarge)
                }
                FilledTonalButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth().height(64.dp)) {
                    Text("Snooze ${ringing.snoozeMinutes} min", style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }
}
