package com.dboycht.taskplanner.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dboycht.taskplanner.TaskPlannerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 系统级事件后重排全部提醒：闹钟不跨重启存活，系统清理/改时间/改时区也会丢。
 * 统一走全量对齐（见 ReminderScheduler.sync），不依赖任何簿记状态。
 *
 * ⚠️ 实测（ColorOS 16 / Android 16）：`BOOT_COMPLETED` 会被 ROM 延后
 * （`DEFER_BY_OPLUS`），重启后闹钟通常要等用户打开应用才回来 —— 因此
 * `Application.onCreate` 里的重排是必备兜底。也试过用 `USER_PRESENT`（解锁）
 * 来补，实测该隐式广播**根本不会投递**到 manifest receiver，遂不保留。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) return
        val app = context.applicationContext as? TaskPlannerApp ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                app.syncRemindersNow()
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
        )
    }
}
