package com.agam.pixelhub.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.LocalDateTime

class Converters {
    @TypeConverter fun fromLocalDateTime(value: LocalDateTime): String = value.toString()
    @TypeConverter fun toLocalDateTime(value: String): LocalDateTime = LocalDateTime.parse(value)
}

@Database(entities = [Reminder::class, Alarm::class, AppShortcut::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun alarmDao(): AlarmDao
    abstract fun shortcutDao(): ShortcutDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "pixelhub.db").build()
    }
}
