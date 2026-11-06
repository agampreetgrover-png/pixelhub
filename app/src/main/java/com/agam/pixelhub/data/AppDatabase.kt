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
    entities = [
        Reminder::class, Alarm::class, AppShortcut::class, HealthNudge::class, Expense::class, Budget::class,
        Person::class, LedgerRecord::class, Note::class,
    ],
    version = 7,
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
    abstract fun peopleDao(): PeopleDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun noteDao(): NoteDao

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

        /**
         * v5 folds Groceries into Food. Entries move over; a Groceries budget is added to the
         * Food budget, or becomes it when there was none.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE `expenses` SET `category` = 'FOOD' WHERE `category` = 'GROCERIES'")
                db.execSQL(
                    "UPDATE `budgets` SET `limitPaise` = `limitPaise` + " +
                        "COALESCE((SELECT `limitPaise` FROM `budgets` WHERE `key` = 'GROCERIES'), 0) " +
                        "WHERE `key` = 'FOOD'",
                )
                db.execSQL(
                    "UPDATE `budgets` SET `key` = 'FOOD' WHERE `key` = 'GROCERIES' " +
                        "AND NOT EXISTS (SELECT 1 FROM `budgets` WHERE `key` = 'FOOD')",
                )
                db.execSQL("DELETE FROM `budgets` WHERE `key` = 'GROCERIES'")
            }
        }

        /** v6 adds the people ledger (who owes whom). Nothing existing changes. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `people` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`nameKey` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_people_nameKey` ON `people` (`nameKey`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ledger_entries` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`personId` INTEGER NOT NULL, " +
                        "`amountPaise` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`at` INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_entries_personId` ON `ledger_entries` (`personId`)")
            }
        }

        /** v7 adds quick notes. Nothing existing changes. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `notes` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`text` TEXT NOT NULL, " +
                        "`done` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "pixelhub.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .build()
    }
}
