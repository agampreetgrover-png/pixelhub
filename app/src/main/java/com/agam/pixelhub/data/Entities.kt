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
