package com.agam.pixelhub

import android.content.Context
import com.agam.pixelhub.ai.GeminiClient
import com.agam.pixelhub.data.AlarmRepository
import com.agam.pixelhub.data.AppDatabase
import com.agam.pixelhub.data.ReminderRepository
import com.agam.pixelhub.data.SettingsStore
import com.agam.pixelhub.data.ShortcutRepository
import com.agam.pixelhub.launcher.InstalledApps
import com.agam.pixelhub.scheduling.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Hand-wired dependencies, one instance per process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** For writes started from the UI that must finish even if the screen goes away. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val db = AppDatabase.build(appContext)
    val scheduler = Scheduler(appContext)
    val reminders = ReminderRepository(db.reminderDao(), scheduler)
    val alarms = AlarmRepository(db.alarmDao(), scheduler)
    val shortcuts = ShortcutRepository(db.shortcutDao())
    val settings = SettingsStore(appContext)
    val installedApps = InstalledApps(appContext)
    val gemini: GeminiClient by lazy {
        GeminiClient(OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build())
    }
}
