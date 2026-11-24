package com.agam.pixelhub.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY done ASC, triggerAt ASC")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE done = 0")
    suspend fun active(): List<Reminder>

    /** Finished repeating reminders whose calendar event couldn't be removed yet. */
    @Query("SELECT * FROM reminders WHERE done = 1 AND calendarEventId IS NOT NULL")
    suspend fun doneWithEvent(): List<Reminder>

    @Insert suspend fun insert(reminder: Reminder): Long
    @Delete suspend fun delete(reminder: Reminder)

    // Column-level writes: each path writes only what it owns, so a calendar sync, a fire and a
    // notification's Done running at once can't write back each other's stale copy of the row.

    /** Every column except calendarEventId, which only the calendar sync writes. */
    @Query(
        "UPDATE reminders SET title = :title, notes = :notes, anchor = :anchor, triggerAt = :triggerAt, " +
            "rec_type = :recType, rec_daysMask = :recDaysMask, rec_interval = :recInterval, " +
            "done = :done, notifiedAt = :notifiedAt WHERE id = :id",
    )
    suspend fun edit(
        id: Long, title: String, notes: String, anchor: LocalDateTime, triggerAt: Long,
        recType: String, recDaysMask: Int, recInterval: Int, done: Boolean, notifiedAt: Long,
    )

    @Query("UPDATE reminders SET calendarEventId = :eventId WHERE id = :id")
    suspend fun setCalendarEventId(id: Long, eventId: Long?)

    @Query("UPDATE reminders SET done = 1 WHERE id = :id")
    suspend fun markDone(id: Long)

    @Query("UPDATE reminders SET triggerAt = :triggerAt, notifiedAt = :notifiedAt WHERE id = :id")
    suspend fun setTiming(id: Long, triggerAt: Long, notifiedAt: Long)

    /** Claims a one-off's single notification: 1 for the first caller, 0 once shown (or done). */
    @Query("UPDATE reminders SET notifiedAt = :now WHERE id = :id AND notifiedAt = 0 AND done = 0")
    suspend fun claimNotify(id: Long, now: Long): Int
}

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC")
    fun observeAll(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun get(id: Long): Alarm?

    @Query("SELECT * FROM alarms WHERE enabled = 1")
    suspend fun enabled(): List<Alarm>

    @Insert suspend fun insert(alarm: Alarm): Long
    @Update suspend fun update(alarm: Alarm)
    @Delete suspend fun delete(alarm: Alarm)
}

@Dao
interface HealthNudgeDao {
    @Query("SELECT * FROM health_nudges ORDER BY hour ASC, minute ASC")
    fun observeAll(): Flow<List<HealthNudge>>

    @Query("SELECT * FROM health_nudges WHERE id = :id")
    suspend fun get(id: Long): HealthNudge?

    @Query("SELECT * FROM health_nudges WHERE enabled = 1")
    suspend fun enabled(): List<HealthNudge>

    @Insert suspend fun insert(nudge: HealthNudge): Long
    @Update suspend fun update(nudge: HealthNudge)
    @Delete suspend fun delete(nudge: HealthNudge)
}

@Dao
interface ExpenseDao {
    /** Newest first; [end] is exclusive. */
    @Query("SELECT * FROM expenses WHERE spentAt >= :start AND spentAt < :end ORDER BY spentAt DESC, id DESC")
    fun observeBetween(start: Long, end: Long): Flow<List<Expense>>

    /** Spending only; income never counts against a budget. */
    @Query("SELECT COALESCE(SUM(amountPaise), 0) FROM expenses WHERE spentAt >= :start AND spentAt < :end AND kind = 'EXPENSE'")
    suspend fun sumBetween(start: Long, end: Long): Long

    @Query(
        "SELECT COALESCE(SUM(amountPaise), 0) FROM expenses " +
            "WHERE spentAt >= :start AND spentAt < :end AND category = :category AND kind = 'EXPENSE'",
    )
    suspend fun sumBetween(start: Long, end: Long, category: ExpenseCategory): Long

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun get(id: Long): Expense?

    @Insert suspend fun insert(expense: Expense): Long
    @Update suspend fun update(expense: Expense)
    @Delete suspend fun delete(expense: Expense)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE `key` = :key")
    suspend fun get(key: String): Budget?

    @Upsert suspend fun upsert(budget: Budget)

    @Query("DELETE FROM budgets WHERE `key` = :key")
    suspend fun delete(key: String)
}

@Dao
interface NoteDao {
    /** Pinned first, then last edited first. */
    @Query("SELECT * FROM notes ORDER BY pinned DESC, updatedAt DESC, id DESC")
    fun observeAll(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): Note?

    @Insert suspend fun insert(note: Note): Long
    @Delete suspend fun delete(note: Note)

    @Query("UPDATE notes SET title = :title, text = :text, updatedAt = :at, pinned = :pinned, color = :color WHERE id = :id")
    suspend fun edit(id: Long, title: String, text: String, at: Long, pinned: Boolean, color: Int)

    @Query("SELECT id FROM notes WHERE createdAt = :at LIMIT 1")
    suspend fun idCreatedAt(at: Long): Long?

    /** Leaves the edit time alone: pinning isn't an edit. */
    @Query("UPDATE notes SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    /** Leaves the edit time alone, like pinning. */
    @Query("UPDATE notes SET color = :color WHERE id = :id")
    suspend fun setColor(id: Long, color: Int)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ShortcutDao {
    @Query("SELECT * FROM shortcuts ORDER BY position ASC, id ASC")
    fun observeAll(): Flow<List<AppShortcut>>

    @Query("SELECT * FROM shortcuts ORDER BY position ASC, id ASC")
    suspend fun all(): List<AppShortcut>

    @Query("SELECT MAX(position) FROM shortcuts")
    suspend fun maxPosition(): Int?

    @Insert suspend fun insert(shortcut: AppShortcut): Long
    @Delete suspend fun delete(shortcut: AppShortcut)
}
