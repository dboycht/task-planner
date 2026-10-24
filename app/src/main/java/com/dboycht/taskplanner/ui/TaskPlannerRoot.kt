package com.dboycht.taskplanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.dboycht.taskplanner.ui.edit.EditScreen
import com.dboycht.taskplanner.ui.list.TaskListScreen
import com.dboycht.taskplanner.ui.preset.PresetEditScreen
import com.dboycht.taskplanner.ui.preset.PresetListScreen

/** 页面栈（状态导航，不引 navigation-compose；M2 设置页加入时再评估）。 */
sealed interface Screen {
    data object List : Screen
    data class EditTask(val taskId: Long?) : Screen
    data object Presets : Screen
    data class EditPreset(val presetId: Long?) : Screen
}

/** 编码成 Bundle 兼容的字符串以支持 rememberSaveable（进程重建不丢栈）。 */
private fun Screen.encode(): String = when (this) {
    is Screen.List -> "list"
    is Screen.EditTask -> "task:${taskId ?: "new"}"
    is Screen.Presets -> "presets"
    is Screen.EditPreset -> "preset:${presetId ?: "new"}"
}

private fun decodeScreen(raw: String): Screen = when {
    raw == "list" -> Screen.List
    raw == "presets" -> Screen.Presets
    raw.startsWith("task:") ->
        Screen.EditTask(raw.removePrefix("task:").toLongOrNull())
    raw.startsWith("preset:") ->
        Screen.EditPreset(raw.removePrefix("preset:").toLongOrNull())
    else -> Screen.List
}

@Composable
fun TaskPlannerRoot() {
    var stackKeys by rememberSaveable { mutableStateOf(listOf(Screen.List.encode())) }

    fun push(screen: Screen) {
        stackKeys = stackKeys + screen.encode()
    }

    fun back() {
        if (stackKeys.size > 1) stackKeys = stackKeys.dropLast(1)
    }

    when (val current = decodeScreen(stackKeys.last())) {
        is Screen.List -> TaskListScreen(
            onEdit = { id -> push(Screen.EditTask(id)) },
        )
        is Screen.EditTask -> EditScreen(
            taskKey = current.taskId?.toString() ?: "new",
            onBack = ::back,
            onOpenPresets = { push(Screen.Presets) },
        )
        is Screen.Presets -> PresetListScreen(
            onBack = ::back,
            onEdit = { id -> push(Screen.EditPreset(id)) },
        )
        is Screen.EditPreset -> PresetEditScreen(
            presetKey = current.presetId?.toString() ?: "new",
            onBack = ::back,
        )
    }
}
