package com.dboycht.taskplanner.ui.list

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dboycht.taskplanner.TaskPlannerApp
import com.dboycht.taskplanner.model.Task
import com.dboycht.taskplanner.model.TaskFilter
import com.dboycht.taskplanner.model.TaskGrouper
import com.dboycht.taskplanner.model.TaskGroup
import com.dboycht.taskplanner.model.TaskStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

class TaskListViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TaskPlannerApp
    private val repository = app.repository

    /** 搜索与筛选状态（UI 直改；作用于分组之前）。 */
    val filter = MutableStateFlow(TaskFilter())

    /**
     * 分组在数据/过滤条件变化时重算（now 取当下）。
     * 已知限制：无数据变化时跨天不会自动重新分组（M4 处理，见 DEVELOPMENT.md）。
     */
    val groups: StateFlow<Map<TaskGroup, List<Task>>> =
        combine(repository.observeTasks(), filter) { tasks, f ->
            TaskGrouper.groupAndSort(TaskFilter.apply(tasks, f), System.currentTimeMillis(), ZoneId.systemDefault())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 全库标签并集（筛选条渲染用，随数据刷新）。 */
    val allTags: StateFlow<List<String>> = repository.observeTasks()
        .map { tasks -> tasks.flatMap { it.tags }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 数据库里的任务总数（区分「空库」与「有库但筛不出」）。 */
    val totalCount: StateFlow<Int> = repository.observeTasks()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 未完成任务数（不受筛选影响）。 */
    val uncompletedCount: StateFlow<Int> = repository.observeTasks()
        .map { tasks -> tasks.count { it.status != TaskStatus.DONE } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** 还没有日历事件的未完成任务数（日历回填横幅口径：真「没同步过」的才计数）。 */
    val unsyncedCount: StateFlow<Int> = repository.observeTasks()
        .map { tasks ->
            tasks.count {
                it.status != TaskStatus.DONE && it.dueTime != null && it.calendarEventId == null
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setFilter(f: TaskFilter) { filter.value = f }

    fun toggleDone(task: Task) {
        viewModelScope.launch {
            repository.setDone(task.id, task.status != TaskStatus.DONE)
            // 重复任务勾完成会补出下一次；日历投影也要跟进 ✓ 前缀 → 走全量维护。
            app.maintenanceSync()
        }
    }

    /** 删除：重复任务可选「仅此次」或「整个系列」；删除前快照交给日历层清理事件。 */
    fun delete(task: Task, deleteSeries: Boolean = false) {
        viewModelScope.launch {
            val removed = repository.delete(task.id, deleteSeries)
            app.maintenanceSync(removed)
        }
    }
}
