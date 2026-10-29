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
import androidx.compose.runtime.LaunchedEffect
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

@Composable
fun TaskPlannerRoot(
    openTaskId: Long? = null,
    onOpenTaskConsumed: () -> Unit = {},
) {
    var stackKeys by rememberSaveable { mutableStateOf(listOf(Screen.List.encode())) }

    // 每次「进入」编辑页都取一个新号；同一 targetId 的多次访问因此拿到不同的 VM key。
    // 计数器随页面栈一起进 saveable，进程重建后继续递增，不会与旧 key 撞车。
    var visitSeq by rememberSaveable { mutableStateOf(1L) }

    fun pushTask(taskId: Long?) {
        stackKeys = stackKeys + Screen.EditTask(taskId, visitSeq).encode()
        visitSeq += 1
    }

    fun pushPreset(presetId: Long?) {
        stackKeys = stackKeys + Screen.EditPreset(presetId, visitSeq).encode()
        visitSeq += 1
    }

    // 从提醒通知点进来：直接压到「列表 → 该任务编辑页」，返回即回列表。
    LaunchedEffect(openTaskId) {
        if (openTaskId != null) {
            stackKeys = listOf(Screen.List.encode())
            pushTask(openTaskId)
            onOpenTaskConsumed()
        }
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
                onEdit = { id -> pushTask(id) },
            )
            is Screen.EditTask -> EditScreen(
                taskId = current.taskId,
                visitId = current.visitId,
                onBack = ::back,
                onOpenPresets = {
                    stackKeys = stackKeys + Screen.Presets.encode()
                },
            )
            is Screen.Presets -> PresetListScreen(
                onBack = ::back,
                onEdit = { id -> pushPreset(id) },
            )
            is Screen.EditPreset -> PresetEditScreen(
                presetId = current.presetId,
                visitId = current.visitId,
                onBack = ::back,
            )
        }
    }
}
