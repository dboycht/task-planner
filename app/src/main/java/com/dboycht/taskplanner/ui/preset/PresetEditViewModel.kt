package com.dboycht.taskplanner.ui.preset

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dboycht.taskplanner.TaskPlannerApp
import com.dboycht.taskplanner.model.ReminderPoint
import kotlinx.coroutines.launch

/** 自建预设的草稿（内置预设不提供编辑入口）。 */
class PresetEditViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = (app as TaskPlannerApp).presetRepository

    data class Draft(
        val id: Long = 0L,
        val name: String = "",
        val points: List<ReminderPoint> = emptyList(),
        val loaded: Boolean = false,
    )

    var draft by mutableStateOf(Draft())
        private set

    /** 已加载的「进入编号」（visitId，见 NavStack.kt 的 Screen）；null = 还没加载过。 */
    private var loadedVisit: Long? = null

    /** 校验：名称非空且至少一个提醒点。 */
    val error: String?
        get() = when {
            draft.name.isBlank() -> "预设名称不能为空"
            draft.points.isEmpty() -> "至少添加一个提醒点"
            else -> null
        }

    /** 与任务编辑页同理：换了一次进入编号就丢掉旧草稿，绝不把上一个预设带进新建页。 */
    fun load(presetId: Long?, visitId: Long) {
        if (loadedVisit == visitId) return
        loadedVisit = visitId
        draft = Draft(loaded = presetId == null)
        if (presetId == null) return
        viewModelScope.launch {
            val preset = repository.getPreset(presetId)
            if (preset == null || preset.isBuiltIn) {
                // 内置预设不可编辑 / 已不存在 → 按新建处理。
                draft = Draft(loaded = true)
                return@launch
            }
            draft = Draft(
                id = preset.id,
                name = preset.name,
                points = preset.policy.points,
                loaded = true,
            )
        }
    }

    fun updateName(v: String) { draft = draft.copy(name = v) }

    fun addPoint(point: ReminderPoint) {
        if (point !in draft.points) {
            draft = draft.copy(points = draft.points + point)
        }
    }

    fun removePoint(index: Int) {
        if (index in draft.points.indices) {
            draft = draft.copy(points = draft.points.filterIndexed { i, _ -> i != index })
        }
    }

    fun save(onSaved: () -> Unit) {
        val d = draft
        if (!d.loaded || error != null) return
        viewModelScope.launch {
            repository.save(
                com.dboycht.taskplanner.model.ReminderPreset(
                    id = d.id,
                    name = d.name.trim(),
                    policy = com.dboycht.taskplanner.model.ReminderPolicy(d.points),
                )
            )
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        if (draft.id == 0L) {
            onDeleted()
            return
        }
        viewModelScope.launch {
            repository.delete(draft.id)
            onDeleted()
        }
    }
}
