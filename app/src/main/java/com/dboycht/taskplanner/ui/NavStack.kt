package com.dboycht.taskplanner.ui

/**
 * 页面栈（状态导航，不引 navigation-compose；M2 设置页加入时再评估）。
 *
 * [visitId] 是"这一次进入该页面"的唯一编号。它不参与 ViewModel 的 key（key 只按目标
 * 区分，避免每次进编辑页都在 Activity 的 ViewModelStore 里堆一个新实例），而是交给
 * 编辑页判断"是重新进了一次（要丢掉旧草稿）还是转屏/重组（要保留草稿）"。
 * 少了它，第二次「新建」会拿到上一次的草稿（2026-10-29 用户反馈的 bug）。
 */
sealed interface Screen {
    data object List : Screen
    data class EditTask(val taskId: Long?, val visitId: Long) : Screen
    data object Presets : Screen
    data class EditPreset(val presetId: Long?, val visitId: Long) : Screen
}

/** 编码成 Bundle 兼容的字符串以支持 rememberSaveable（进程重建不丢栈）。 */
internal fun Screen.encode(): String = when (this) {
    is Screen.List -> "list"
    is Screen.Presets -> "presets"
    is Screen.EditTask -> "task:${taskId ?: "new"}:$visitId"
    is Screen.EditPreset -> "preset:${presetId ?: "new"}:$visitId"
}

/** 目标段解码：`new` → [NEW_TARGET]，正整数 → 该 id，其余 → null（非法）。 */
private const val NEW_TARGET = Long.MIN_VALUE

private fun String.toTargetOrNull(): Long? = when (this) {
    "new" -> NEW_TARGET
    else -> toLongOrNull()?.takeIf { it > 0L }
}

/** 宽容解码：认不出的串一律退回列表页（宁可回到首页也不崩）。 */
internal fun decodeScreen(raw: String): Screen {
    if (raw == "list") return Screen.List
    if (raw == "presets") return Screen.Presets

    val parts = raw.split(':')
    // 旧版编码 "task:12" / "task:new" 没有 visitId，按 0 处理即可（老栈恢复后仍可编辑）。
    if (parts.size > 3) return Screen.List
    val target = parts.getOrNull(1)?.toTargetOrNull() ?: return Screen.List
    val visit = parts.getOrNull(2)?.toLongOrNull() ?: 0L
    val id = target.takeIf { it != NEW_TARGET }

    return when (parts[0]) {
        "task" -> Screen.EditTask(taskId = id, visitId = visit)
        "preset" -> Screen.EditPreset(presetId = id, visitId = visit)
        else -> Screen.List
    }
}
