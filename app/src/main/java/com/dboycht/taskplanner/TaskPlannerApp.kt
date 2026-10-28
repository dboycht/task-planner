package com.dboycht.taskplanner

import android.app.Application
import androidx.room.Room
import com.dboycht.taskplanner.data.AppDatabase
import com.dboycht.taskplanner.data.PresetRepository
import com.dboycht.taskplanner.data.TaskRepository
import com.dboycht.taskplanner.reminder.NotificationHelper
import com.dboycht.taskplanner.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TaskPlannerApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "task-planner.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()
    }

    val repository: TaskRepository by lazy { TaskRepository(database.taskDao()) }

    val presetRepository: PresetRepository by lazy {
        PresetRepository(database.reminderPresetDao(), appScope)
    }

    override fun onCreate() {
        super.onCreate()
        presetRepository.ensureSeeded()
        NotificationHelper.ensureChannel(this)
        // 启动即对齐一次：装包后角色变更、上次被系统清掉的闹钟都在这里补上。
        syncReminders()
    }

    /** 任务增删改 / 勾选完成后触发全量重排（后台线程，不阻塞调用方）。 */
    fun syncReminders() {
        appScope.launch { syncRemindersNow() }
    }

    /** BroadcastReceiver 走 goAsync 时用：确保在本进程还可能被杀之前完成对齐。 */
    suspend fun syncRemindersNow() {
        withContext(Dispatchers.IO) {
            ReminderScheduler.sync(this@TaskPlannerApp, repository.allTasks())
        }
    }
}
