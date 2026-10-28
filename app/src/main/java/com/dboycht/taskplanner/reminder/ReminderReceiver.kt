package com.dboycht.taskplanner.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dboycht.taskplanner.TaskPlannerApp
import com.dboycht.taskplanner.model.TaskStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 闹钟到点入口：读 Room 取最新任务（Room 是单一事实源，不信任 Intent 里的快照），
 * 未完成则发通知，然后重新对齐闹钟（排下一个提醒点 / 任务已被删或完成则取消）。
 *
 * 也处理通知上的「标记完成」动作。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        if (taskId <= 0L) return
        val app = context.applicationContext as? TaskPlannerApp ?: return
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                if (intent.action == ACTION_MARK_DONE) {
                    app.repository.setDone(taskId, true)
                    ReminderScheduler.cancelForTask(app, taskId)
                    NotificationHelper.cancel(app, taskId)
                } else {
                    val task = app.repository.getTask(taskId)
                    if (task != null && task.status != TaskStatus.DONE) {
                        val now = System.currentTimeMillis()
                        val reminder = task.reminderPolicy.reminderDueNow(task.startTime, task.dueTime, now)
                        if (reminder != null) {
                            NotificationHelper.show(app, task, reminder)
                        }
                    }
                }
                // 无论走哪条分支都对齐一次：顺带排下一个提醒点、清掉不该留的闹钟。
                app.syncRemindersNow()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.dboycht.taskplanner.action.REMINDER_FIRE"
        const val ACTION_MARK_DONE = "com.dboycht.taskplanner.action.MARK_DONE"
        const val EXTRA_TASK_ID = "task_id"
    }
}
