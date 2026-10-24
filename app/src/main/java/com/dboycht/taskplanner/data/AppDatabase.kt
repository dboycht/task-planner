package com.dboycht.taskplanner.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TaskEntity::class, ReminderPresetEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun reminderPresetDao(): ReminderPresetDao

    companion object {
        /** v1→v2：新增 reminder_presets 表（提醒预设）。 */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reminder_presets` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`policy` TEXT NOT NULL, " +
                        "`isBuiltIn` INTEGER NOT NULL, " +
                        "`sortOrder` INTEGER NOT NULL)"
                )
            }
        }

        /**
         * v2→v3：tasks.priority 语义从三档枚举 ordinal（HIGH=0/MEDIUM=1/LOW=2）
         * 改为 0~100 连续值（越大越紧急）。列类型不变，只换算存量数据。
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE tasks SET priority = CASE priority " +
                        "WHEN 0 THEN 100 WHEN 1 THEN 50 WHEN 2 THEN 0 ELSE priority END"
                )
            }
        }
    }
}
