package com.agam.pixelhub.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.agam.pixelhub.scheduling.Recurrence
import java.time.LocalDateTime

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    /** First occurrence in local wall-clock time. Gives the time of day and where intervals start. */
    val anchor: LocalDateTime,
    /** Next time this reminder fires, epoch millis. */
    val triggerAt: Long,
    @Embedded(prefix = "rec_") val recurrence: Recurrence = Recurrence.NONE,
    val done: Boolean = false,
    /** Last time a notification was shown, epoch millis (0 = never). */
    val notifiedAt: Long = 0,
    /** The copy of this reminder in the phone/Google calendar, if calendar sync made one. */
    val calendarEventId: Long? = null,
)

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,
    val minute: Int,
    /** Repeat days bitmask (see Days); 0 = rings once at the next occurrence. */
    val daysMask: Int = 0,
    val label: String = "",
    val enabled: Boolean = true,
    val ringtoneUri: String? = null,
    val vibrate: Boolean = true,
    val snoozeMinutes: Int = 10,
)

enum class ShortcutKind { APP, LINK }

enum class ExpenseCategory(val label: String) {
    FOOD("Food & dining"),
    GROCERIES("Groceries"),
    TRANSPORT("Transport"),
    SHOPPING("Shopping"),
    BILLS("Bills & utilities"),
    HEALTH("Health"),
    ENTERTAINMENT("Entertainment"),
    TRAVEL("Travel"),
    OTHER("Other"),
}

/** One expense. Money is stored in paise (₹1 = 100) to avoid floating-point errors. */
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountPaise: Long,
    val category: ExpenseCategory = ExpenseCategory.OTHER,
    val note: String = "",
    /** When the money was spent, epoch millis. */
    val spentAt: Long,
)

/** A monthly limit. [key] is [Budget.TOTAL] or an ExpenseCategory name. */
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val key: String,
    val limitPaise: Long,
) {
    companion object {
        const val TOTAL = "TOTAL"
        fun keyFor(category: ExpenseCategory?) = category?.name ?: TOTAL
    }
}

/** What a health nudge measures, read from Health Connect for "today so far". */
enum class NudgeMetric { STEPS, EXERCISE_MINUTES, ACTIVE_CALORIES }

/**
 * "If I'm under [goal] by hh:mm on these days, nudge me."
 * Checked at that time; notifies only when behind.
 */
@Entity(tableName = "health_nudges")
data class HealthNudge(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val metric: NudgeMetric = NudgeMetric.STEPS,
    val goal: Long,
    val hour: Int,
    val minute: Int,
    /** Days to check (see Days); 0 = every day. */
    val daysMask: Int = 0,
    val enabled: Boolean = true,
)

@Entity(tableName = "shortcuts")
data class AppShortcut(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val kind: ShortcutKind,
    /** APP: the app to open. LINK: optional app that should handle the link. */
    val packageName: String? = null,
    /** LINK only: https:, tel:, geo:, spotify:, ... */
    val uri: String? = null,
    val position: Int = 0,
)
