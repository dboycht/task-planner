package com.dboycht.taskplanner.ui.edit

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dboycht.taskplanner.TaskPlannerApp
import com.dboycht.taskplanner.model.PRIORITY_DEFAULT
import com.dboycht.taskplanner.model.ReminderPolicy
import com.dboycht.taskplanner.model.ReminderPreset
import com.dboycht.taskplanner.model.Task
import com.dboycht.taskplanner.model.TaskStatus
import com.dboycht.taskplanner.model.TaskValidator
import com.dboycht.taskplanner.model.clampPriority
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 新建/编辑草稿。每个编辑目标用独立的 ViewModel 实例（按 taskKey 区分），
 * 避免同一 Activity 复用上一个任务的草稿。
 */
class TaskEditViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TaskPlannerApp
    private val repository = app.repository

    /** 预设列表（供提醒选择 sheet 用；选择时只拷贝策略值）。 */
    val presets: StateFlow<List<ReminderPreset>> =
        app.presetRepository.observePresets()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    data class Draft(
        val id: Long = 0L,
        val title: String = "",
        val description: String = "",
        val startTime: Long? = null,
        val dueTime: Long? = null,
        val priority: Int = PRIORITY_DEFAULT,
        val status: TaskStatus = TaskStatus.TODO,
        val tags: String = "",
        val reminderPolicy: ReminderPolicy = ReminderPolicy.DEFAULT,
        val completedAt: Long? = null,
        /** false = 尚未从库加载完（新建任务立即置 true）。防止加载期间误存。 */
        val loaded: Boolean = false,
    )

    var draft by mutableStateOf(Draft())
        private set

    val error: String?
        get() = TaskValidator.validate(draft.title, draft.startTime, draft.dueTime)

    fun load(taskId: Long?) {
        if (draft.loaded) return
        if (taskId == null) {
            draft = draft.copy(loaded = true)
            return
        }
        viewModelScope.launch {
            val task = repository.getTask(taskId)
            if (task == null) {
                // 任务已不存在（如别处被删）→ 退化为新建态，而不是白屏。
                draft = draft.copy(loaded = true)
                return@launch
            }
            draft = Draft(
                id = task.id,
                title = task.title,
                description = task.description,
                startTime = task.startTime,
                dueTime = task.dueTime,
                priority = task.priority,
                status = task.status,
                tags = task.tags.joinToString(","),
                reminderPolicy = task.reminderPolicy,
                completedAt = task.completedAt,
                loaded = true,
            )
        }
    }

    fun updateTitle(v: String) { draft = draft.copy(title = v) }
    fun updateDescription(v: String) { draft = draft.copy(description = v) }
    fun updatePriority(v: Int) { draft = draft.copy(priority = clampPriority(v)) }
    fun updateStatus(s: TaskStatus) { draft = draft.copy(status = s) }
    fun updateTags(v: String) { draft = draft.copy(tags = v) }
    fun setStart(v: Long?) { draft = draft.copy(startTime = v) }
    fun setDue(v: Long?) { draft = draft.copy(dueTime = v) }

    /** 选定预设/不提醒：拷贝策略值进草稿（之后预设再改不影响本任务）。 */
    fun selectPolicy(policy: ReminderPolicy) {
        draft = draft.copy(reminderPolicy = policy)
    }

    /** 校验通过才落库（保存按钮也已按同一条件禁用）。 */
    fun save(onSaved: () -> Unit) {
        val d = draft
        if (!d.loaded || error != null) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val completedAt = when {
                d.status != TaskStatus.DONE -> null
                d.id != 0L && d.completedAt != null -> d.completedAt // 保留原完成时间
                else -> now
            }
            repository.save(
                Task(
                    id = d.id,
                    title = d.title.trim(),
                    description = d.description.trim(),
                    startTime = d.startTime,
                    dueTime = d.dueTime,
                    priority = d.priority,
                    status = d.status,
                    tags = d.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
                    reminderPolicy = d.reminderPolicy,
                    completedAt = completedAt,
                )
            )
            // 时间/策略/状态任一变化都会改变触发时刻 → 全量重排（幂等）。
            app.syncReminders()
            onSaved()
        }
    }
}
