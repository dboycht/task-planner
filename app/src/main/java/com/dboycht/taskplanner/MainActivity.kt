package com.dboycht.taskplanner

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.dboycht.taskplanner.ui.TaskPlannerRoot
import com.dboycht.taskplanner.ui.theme.TaskPlannerTheme

class MainActivity : ComponentActivity() {

    /** 通知点进来要打开的任务 id；由 Root 消费后清空（避免重组时反复跳转）。 */
    private val pendingTaskId = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 进程重建时由 rememberSaveable 恢复页面栈，不再按旧 Intent 强行跳转。
        if (savedInstanceState == null) consumeIntent(intent)
        enableEdgeToEdge()
        setContent {
            TaskPlannerTheme {
                TaskPlannerRoot(
                    openTaskId = pendingTaskId.value,
                    onOpenTaskConsumed = { pendingTaskId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(intent: Intent?) {
        val taskId = intent?.getLongExtra(EXTRA_TASK_ID, 0L) ?: 0L
        if (taskId > 0L) {
            pendingTaskId.value = taskId
            intent?.removeExtra(EXTRA_TASK_ID)
        }
    }

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
    }
}
