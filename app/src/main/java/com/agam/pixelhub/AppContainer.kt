package com.agam.pixelhub

import android.content.Context
import com.agam.pixelhub.ai.AssistantLog
import com.agam.pixelhub.alarm.AlarmRingService
import com.agam.pixelhub.ui.Fmt
import java.time.LocalTime
import com.agam.pixelhub.calendar.CalendarSync
import com.agam.pixelhub.data.AlarmRepository
import com.agam.pixelhub.data.AppDatabase
import com.agam.pixelhub.data.HealthNudgeRepository
import com.agam.pixelhub.data.NotesRepository
import com.agam.pixelhub.data.ReminderRepository
import com.agam.pixelhub.data.SettingsStore
import com.agam.pixelhub.data.ShortcutRepository
import com.agam.pixelhub.expenses.ExpenseRepository
import com.agam.pixelhub.health.HealthRepository
import com.agam.pixelhub.notify.BudgetNotifier
import com.agam.pixelhub.notify.ReminderNotifier
import com.agam.pixelhub.notify.ReportNotifier
import com.agam.pixelhub.expenses.ReportMath
import com.agam.pixelhub.launcher.InstalledApps
import com.agam.pixelhub.ledger.LedgerRepository
import com.agam.pixelhub.scheduling.Scheduler
import com.agam.pixelhub.widget.WidgetSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

/** Hand-wired dependencies, one instance per process. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    /** For writes started from the UI that must finish even if the screen goes away. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val db = AppDatabase.build(appContext)
    val settings = SettingsStore(appContext)

    /** The step goal, read once and kept: screens open on the real goal instead of rolling up from 8,000. */
    val stepGoal: StateFlow<Long> =
        settings.stepGoal.stateIn(appScope, SharingStarted.Eagerly, SettingsStore.DEFAULT_STEP_GOAL)
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

    /** Who owes whom: people and the money between you. Kept apart from expenses. */
    val ledger = LedgerRepository(db.peopleDao(), db.ledgerDao())

    /** Quick notes, in the drawer. */
    val notes = NotesRepository(db.noteDao())

    /** Every assistant request and how the rules handled it, shown in Settings. */
    val assistantLog = AssistantLog(File(appContext.filesDir, "assistant_log.json"), appScope)

    /** Redraws home-screen widgets when their data changes. */
    val widgetSync = WidgetSync(appContext, appScope).also { it.start(this) }

    init {
        // The assistant no longer uses Gemini or Groq: delete any API keys saved by older versions.
        appScope.launch { settings.forgetCloudAi() }
    }

    /**
     * Puts every alarm, snooze, nudge and reminder back into AlarmManager, which forgets them on
     * reboot, force-stop and restore. Safe to repeat: each one replaces its own PendingIntent.
     * Reminders missed in the meantime are shown now.
     */
    suspend fun rearmAll() {
        scheduler.restoreSnoozes()
        alarms.rescheduleAll().forEach { a ->
            AlarmRingService.notifyMissed(appContext, a.id, a.label, "Due at ${Fmt.time(appContext, LocalTime.of(a.hour, a.minute))} while the phone was off")
        }
        healthNudges.rescheduleAll()
        reminders.rescheduleAll().forEach { ReminderNotifier.show(appContext, it) }
        scheduleMonthlyReport()
    }

    /** A month whose report the Money page should open (from the report notification); the page clears it. */
    val openReport = MutableStateFlow<YearMonth?>(null)

    /**
     * Arms last month's report for 9:00 on the coming 1st, or for now if one is still owed (the
     * phone was off on the 1st). A fresh install starts counting from this month, so it never
     * opens with a report for a month before PixelHub was used.
     */
    suspend fun scheduleMonthlyReport() {
        val zone = ZoneId.systemDefault()
        val last = settings.lastReportMonth()
            ?: YearMonth.now(zone).minusMonths(1).also { settings.setLastReportMonth(it) }
        val at = ReportMath.nextReportAt(LocalDateTime.now(zone), last)
        scheduler.scheduleMonthlyReport(at.atZone(zone).toInstant().toEpochMilli())
    }

    /** The report alarm went off: sum up last month if anything happened in it, then arm the next one. */
    suspend fun onMonthlyReport() {
        val month = YearMonth.now().minusMonths(1)
        val last = settings.lastReportMonth()
        if (last == null || last < month) {
            val report = expenses.report(month).first()
            if (report.summary.entries.isNotEmpty()) ReportNotifier.show(appContext, report)
            settings.setLastReportMonth(month)
        }
        scheduleMonthlyReport()
    }
}
