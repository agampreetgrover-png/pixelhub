package com.agam.pixelhub.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.LocalDateTime

class Converters {
    @TypeConverter fun fromLocalDateTime(value: LocalDateTime): String = value.toString()
    @TypeConverter fun toLocalDateTime(value: String): LocalDateTime = LocalDateTime.parse(value)
}

@Database(
    entities = [Reminder::class, Alarm::class, AppShortcut::class, HealthNudge::class, Expense::class, Budget::class],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun reminderDao(): ReminderDao
    abstract fun alarmDao(): AlarmDao
    abstract fun shortcutDao(): ShortcutDao
    abstract fun healthNudgeDao(): HealthNudgeDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        /** v2 adds health nudges and calendar sync. Existing reminders, alarms and shortcuts are kept. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `reminders` ADD COLUMN `calendarEventId` INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `health_nudges` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`metric` TEXT NOT NULL, " +
                        "`goal` INTEGER NOT NULL, " +
                        "`hour` INTEGER NOT NULL, " +
                        "`minute` INTEGER NOT NULL, " +
                        "`daysMask` INTEGER NOT NULL, " +
                        "`enabled` INTEGER NOT NULL)",
                )
            }
        }

        /** v3 adds the expense tracker. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `expenses` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`amountPaise` INTEGER NOT NULL, " +
                        "`category` TEXT NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`spentAt` INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `budgets` (" +
                        "`key` TEXT NOT NULL, " +
                        "`limitPaise` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`key`))",
                )
            }
        }

        /** v4 lets an entry be income: a kind column, EXPENSE for every existing row. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'EXPENSE'")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "pixelhub.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
    }
}
