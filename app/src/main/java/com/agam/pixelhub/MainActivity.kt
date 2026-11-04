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
import com.agam.pixelhub.ui.PixelHubRoot
import com.agam.pixelhub.ui.Tab
import com.agam.pixelhub.ui.theme.PixelHubTheme

class MainActivity : ComponentActivity() {
    private val tab = mutableStateOf(Tab.HOME)
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) {
            tabFrom(intent)?.let { tab.value = it }
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { PixelHubTheme { PixelHubRoot(tab) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tabFrom(intent)?.let { tab.value = it }
    }

    private fun tabFrom(intent: Intent?): Tab? =
        intent?.getStringExtra(EXTRA_TAB)?.let { name -> Tab.entries.firstOrNull { it.name == name } }

    companion object {
        const val EXTRA_TAB = "com.agam.pixelhub.extra.TAB"
    }
}
