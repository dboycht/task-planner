package com.dboycht.taskplanner.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dboycht.taskplanner.TaskPlannerApp
import com.dboycht.taskplanner.calendar.CalendarSettings
import com.dboycht.taskplanner.calendar.CalendarSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 日历设置页状态。权限/日历清单在进页与 ON_RESUME 时刷新（refresh 自增触发重查）。
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TaskPlannerApp

    data class UiState(
        val granted: Boolean = false,
        val syncEnabled: Boolean = true,
        val calendars: List<CalendarSyncManager.CalendarInfo> = emptyList(),
        /** 当前生效的目标日历（用户选的或自动解析的专属日历）；null = 尚不可用。 */
        val resolvedCalendarId: Long? = null,
        val backfillDone: Boolean = true,
        val uncompletedWithoutEvent: Int = 0,
        val busy: Boolean = false,
        /** 回填/操作的即时反馈（snackbar 文案）。 */
        val message: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun refresh() {
        val context = getApplication<TaskPlannerApp>()
        viewModelScope.launch {
            val granted = CalendarSyncManager.hasPermission(context)
            if (!granted) {
                _state.value = _state.value.copy(
                    granted = false,
                    calendars = emptyList(),
                    resolvedCalendarId = null,
                    syncEnabled = CalendarSettings.syncEnabled(context),
                    backfillDone = CalendarSettings.backfillDone(context),
                )
                return@launch
            }
            val calendars = withContext(Dispatchers.IO) { CalendarSyncManager.listCalendars(context) }
            val resolved = withContext(Dispatchers.IO) { CalendarSyncManager.resolveTargetCalendar(context) }
            val tasks = app.repository.allTasks()
            _state.value = _state.value.copy(
                granted = true,
                syncEnabled = CalendarSettings.syncEnabled(context),
                calendars = calendars,
                resolvedCalendarId = resolved,
                backfillDone = CalendarSettings.backfillDone(context),
                uncompletedWithoutEvent = tasks.count {
                    it.status != com.dboycht.taskplanner.model.TaskStatus.DONE &&
                        it.dueTime != null && it.calendarEventId == null
                },
            )
        }
    }

    fun setSyncEnabled(value: Boolean) {
        val context = getApplication<TaskPlannerApp>()
        CalendarSettings.setSyncEnabled(context, value)
        _state.value = _state.value.copy(syncEnabled = value)
        if (value) app.maintenanceSync()
    }

    fun selectCalendar(id: Long) {
        val context = getApplication<TaskPlannerApp>()
        CalendarSettings.setSelectedCalendarId(context, id)
        _state.value = _state.value.copy(resolvedCalendarId = id, message = "已切换目标日历")
        // 换日历后旧事件留原日历（v1 不迁移）；新对齐从现在起写新日历。
        app.maintenanceSync()
    }

    fun backfill() {
        val context = getApplication<TaskPlannerApp>()
        if (_state.value.busy) return
        _state.value = _state.value.copy(busy = true)
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) {
                CalendarSyncManager.backfillUncompleted(context, app.repository.allTasks())
            }
            CalendarSettings.setBackfillDone(context)
            _state.value = _state.value.copy(busy = false, message = "已同步 $n 条到日历")
            refresh()
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
