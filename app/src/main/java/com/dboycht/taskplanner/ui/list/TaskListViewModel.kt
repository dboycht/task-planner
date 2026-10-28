package com.dboycht.taskplanner.ui.list

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dboycht.taskplanner.TaskPlannerApp
import com.dboycht.taskplanner.model.Task
import com.dboycht.taskplanner.model.TaskGrouper
import com.dboycht.taskplanner.model.TaskGroup
import com.dboycht.taskplanner.model.TaskStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

class TaskListViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TaskPlannerApp
    private val repository = app.repository

    /**
     * 分组在每次数据变化时重算（now 取当下）。
     * 已知限制：无数据变化时跨天不会自动重新分组（M4 处理，见 DEVELOPMENT.md）。
     */
    val groups: StateFlow<Map<TaskGroup, List<Task>>> = repository.observeTasks()
        .map { TaskGrouper.groupAndSort(it, System.currentTimeMillis(), ZoneId.systemDefault()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun toggleDone(task: Task) {
        viewModelScope.launch {
            repository.setDone(task.id, task.status != TaskStatus.DONE)
            app.syncReminders()
        }
    }

    fun delete(task: Task) {
        viewModelScope.launch {
            repository.delete(task.id)
            app.syncReminders()
        }
    }
}
