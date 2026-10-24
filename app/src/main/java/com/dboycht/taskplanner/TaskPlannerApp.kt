package com.dboycht.taskplanner

import android.app.Application
import androidx.room.Room
import com.dboycht.taskplanner.data.AppDatabase
import com.dboycht.taskplanner.data.PresetRepository
import com.dboycht.taskplanner.data.TaskRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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
    }
}
