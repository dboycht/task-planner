package com.dboycht.taskplanner.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.dboycht.taskplanner.ui.edit.EditScreen
import com.dboycht.taskplanner.ui.list.TaskListScreen

/**
 * M1 用状态导航（列表 ↔ 编辑），不引 navigation-compose；
 * M2 加入设置页时再评估是否换正式导航。
 * editKey：null = 列表；"new" = 新建；"<id>" = 编辑对应任务。
 */
@Composable
fun TaskPlannerRoot() {
    var editKey by rememberSaveable { mutableStateOf<String?>(null) }
    when (val key = editKey) {
        null -> TaskListScreen(
            onEdit = { id -> editKey = if (id == null) "new" else id.toString() },
        )
        else -> EditScreen(
            taskKey = key,
            onBack = { editKey = null },
        )
    }
}
