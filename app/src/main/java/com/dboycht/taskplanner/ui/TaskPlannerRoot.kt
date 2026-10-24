package com.dboycht.taskplanner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.dboycht.taskplanner.ui.anim.EASE
import com.dboycht.taskplanner.ui.anim.rememberAnimationsEnabled
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

    // 接管系统返回（手势侧滑 / 返回键）：子页面返回上一页，栈底（列表）才放行默认退出。
    // 没有这个接管，自定义栈导航下任何页面的侧滑返回都会直接退出应用。
    BackHandler(enabled = stackKeys.size > 1) { back() }

    // 页面切换转场：前进新页从右滑入、旧页左移淡出；返回反向。动画关闭时退化为直切。
    val animationsEnabled = rememberAnimationsEnabled()
    AnimatedContent(
        targetState = stackKeys,
        transitionSpec = {
            if (!animationsEnabled) {
                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            } else if (targetState.size >= initialState.size) {
                (
                    slideInHorizontally(tween(300, easing = EASE)) { it } +
                        fadeIn(tween(280))
                    ) togetherWith (
                    slideOutHorizontally(tween(300, easing = EASE)) { -it / 3 } +
                        fadeOut(tween(220))
                    )
            } else {
                (
                    slideInHorizontally(tween(300, easing = EASE)) { -it / 3 } +
                        fadeIn(tween(280))
                    ) togetherWith (
                    slideOutHorizontally(tween(300, easing = EASE)) { it } +
                        fadeOut(tween(220))
                    )
            }
        },
        label = "nav",
    ) { keys ->
        when (val current = decodeScreen(keys.last())) {
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
}
