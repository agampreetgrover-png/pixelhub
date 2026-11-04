package com.agam.pixelhub.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY done ASC, triggerAt ASC")
    fun observeAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Long): Reminder?

    @Query("SELECT * FROM reminders WHERE done = 0")
    suspend fun active(): List<Reminder>

    @Insert suspend fun insert(reminder: Reminder): Long
    @Update suspend fun update(reminder: Reminder)
    @Delete suspend fun delete(reminder: Reminder)
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
