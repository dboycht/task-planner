package com.dboycht.taskplanner

import android.app.Application
import androidx.room.Room
import com.dboycht.taskplanner.calendar.CalendarSyncBridge
import com.dboycht.taskplanner.calendar.CalendarSyncManager
import com.dboycht.taskplanner.data.AppDatabase
import com.dboycht.taskplanner.data.PresetRepository
import com.dboycht.taskplanner.data.TaskRepository
import com.dboycht.taskplanner.model.Task
import com.dboycht.taskplanner.reminder.NotificationHelper
import com.dboycht.taskplanner.reminder.ReminderScheduler
import com.dboycht.taskplanner.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TaskPlannerApp : Application(), CalendarSyncBridge {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "task-planner.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
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
        // 启动即对齐一次：装包后角色变更、上次被系统清掉的闹钟都在这里补上；
        // 重复任务的滚动窗口也在此保底补出下一次实例；有小组件时顺带刷新。
        maintenanceSync()
        WidgetUpdater.updateAll(this)
    }

    /** 任务增删改 / 勾选完成后触发全量重排（后台线程，不阻塞调用方）。 */
    fun syncReminders() {
        appScope.launch { syncRemindersNow() }
    }

    /**
     * 全量维护（后台线程）：重复任务滚动生成 → 日历投影对齐 → 提醒重排。
     * 顺序有意义：先生成的实例才有资格排闹钟 / 写日历。
     * [removed] 是刚被删除的任务快照（含 calendarEventId），其日历事件在此清理。
     */
    fun maintenanceSync(removed: List<Task> = emptyList()) {
        appScope.launch { maintenanceSyncNow(removed) }
    }

    /** BroadcastReceiver 走 goAsync 时用：确保在本进程还可能被杀之前完成对齐。 */
    suspend fun syncRemindersNow() {
        withContext(Dispatchers.IO) {
            ReminderScheduler.sync(this@TaskPlannerApp, repository.allTasks())
        }
    }

    /** 同上，但覆盖全部维护职责（BootReceiver / 闹钟到点后走这条）。 */
    suspend fun maintenanceSyncNow(removed: List<Task> = emptyList()) {
        // 单飞：已在跑的同步会覆盖本次意图（它结束时读的是最新库态），避免并发双写
        // ——实测并发的 ensureDedicatedCalendar 会在日历存储里建出两个「Task Planner」。
        if (!maintenanceMutex.tryLock()) return
        try {
            withContext(Dispatchers.IO) {
                repository.ensureRecurrence()
                CalendarSyncManager.syncAll(this@TaskPlannerApp, repository.allTasks(), removed)
                ReminderScheduler.sync(this@TaskPlannerApp, repository.allTasks())
                WidgetUpdater.updateAll(this@TaskPlannerApp)
            }
        } finally {
            maintenanceMutex.unlock()
        }
    }

    private val maintenanceMutex = kotlinx.coroutines.sync.Mutex()

    override fun taskRepository(): TaskRepository = repository
}
