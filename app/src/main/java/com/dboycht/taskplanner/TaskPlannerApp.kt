package com.dboycht.taskplanner

import android.app.Application
import androidx.room.Room
import com.dboycht.taskplanner.data.AppDatabase
import com.dboycht.taskplanner.data.TaskRepository

class TaskPlannerApp : Application() {

    val database: AppDatabase by lazy {
        Room.databaseBuilder(this, AppDatabase::class.java, "task-planner.db").build()
    }

    val repository: TaskRepository by lazy { TaskRepository(database.taskDao()) }
}
