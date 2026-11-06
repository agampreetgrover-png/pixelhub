package com.agam.pixelhub

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.agam.pixelhub.alarm.AlarmRingActivity
import com.agam.pixelhub.alarm.AlarmRingService
import com.agam.pixelhub.ui.PixelHubRoot
import com.agam.pixelhub.ui.Tab
import com.agam.pixelhub.ui.theme.PixelHubTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.YearMonth

class MainActivity : ComponentActivity() {
    private val tab = mutableStateOf(Tab.HOME)
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            tabFrom(intent)?.let { tab.value = it }
            reportFrom(intent)
            // A force-stop or a restore from backup wipes AlarmManager without any broadcast;
            // opening the app is the first chance to put alarms and reminders back.
            container.appScope.launch(Dispatchers.IO) { container.rearmAll() }
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Rotation, a dark-mode switch or a font change recreates the activity; stay on the same page.
            tabNamed(savedInstanceState.getString(EXTRA_TAB))?.let { tab.value = it }
        }
        setContent { PixelHubTheme { PixelHubRoot(tab) } }
    }

    override fun onResume() {
        super.onResume()
        // An alarm still ringing whose notification was dismissed (or never shown, if notifications
        // are off) would otherwise have no Stop button anywhere: bring its ring screen up.
        if (AlarmRingService.state.value != null) {
            startActivity(Intent(this, AlarmRingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(EXTRA_TAB, tab.value.name)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tabFrom(intent)?.let { tab.value = it }
        reportFrom(intent)
    }

    private fun tabFrom(intent: Intent?): Tab? = tabNamed(intent?.getStringExtra(EXTRA_TAB))

    /** The monthly report notification names a month for the Money page to open. Read once. */
    private fun reportFrom(intent: Intent?) {
        val month = intent?.getStringExtra(EXTRA_REPORT_MONTH)?.let { runCatching { YearMonth.parse(it) }.getOrNull() } ?: return
        intent.removeExtra(EXTRA_REPORT_MONTH)
        container.openReport.value = month
    }

    private fun tabNamed(name: String?): Tab? = name?.let { n -> Tab.entries.firstOrNull { it.name == n } }

    companion object {
        const val EXTRA_TAB = "com.agam.pixelhub.extra.TAB"
        /** "2026-10": open that month's report on the Money page. */
        const val EXTRA_REPORT_MONTH = "com.agam.pixelhub.extra.REPORT_MONTH"
    }
}
