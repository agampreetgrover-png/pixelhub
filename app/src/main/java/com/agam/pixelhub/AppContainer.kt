package com.agam.pixelhub

import android.content.Context
import com.agam.pixelhub.ai.AssistantAi
import com.agam.pixelhub.ai.GeminiClient
import com.agam.pixelhub.ai.GrokClient
import com.agam.pixelhub.calendar.CalendarSync
import com.agam.pixelhub.data.AlarmRepository
import com.agam.pixelhub.data.AppDatabase
import com.agam.pixelhub.data.HealthNudgeRepository
import com.agam.pixelhub.data.ReminderRepository
import com.agam.pixelhub.data.SettingsStore
import com.agam.pixelhub.data.ShortcutRepository
import com.agam.pixelhub.expenses.ExpenseRepository
import com.agam.pixelhub.health.HealthRepository
import com.agam.pixelhub.notify.BudgetNotifier
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
    val settings = SettingsStore(appContext)
    val scheduler = Scheduler(appContext)
    val calendar = CalendarSync(appContext, settings)
    val reminders = ReminderRepository(db.reminderDao(), scheduler, calendar)
    val alarms = AlarmRepository(db.alarmDao(), scheduler)
    val shortcuts = ShortcutRepository(db.shortcutDao())
    val health = HealthRepository(appContext)
    val healthNudges = HealthNudgeRepository(db.healthNudgeDao(), scheduler)
    val expenses = ExpenseRepository(
        db.expenseDao(), db.budgetDao(),
        onBudgetAlert = { BudgetNotifier.show(appContext, it) },
    )
    val installedApps = InstalledApps(appContext)
    private val http by lazy { OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build() }
    val gemini: GeminiClient by lazy { GeminiClient(http) }
    val grok: GrokClient by lazy { GrokClient(http) }

    /** What the Assistant should call: picks Gemini or Grok from Settings. */
    val ai = AssistantAi(settings, { gemini }, { grok })
}
