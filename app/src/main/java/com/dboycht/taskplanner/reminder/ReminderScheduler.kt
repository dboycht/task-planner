package com.dboycht.taskplanner.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.dboycht.taskplanner.model.Task

/**
 * 自有提醒通道（主通道）：每个任务**最多一条**闹钟，永远指向「下一个尚未到点的提醒」。
 * 响铃后由 [ReminderReceiver] 重新对齐，自动排下一点；因此不需要按提醒点簿记多条闹钟。
 *
 * 幂等：同步时对所有应当有闹钟的任务一律 set 一遍（FLAG_UPDATE_CURRENT 覆盖），
 * 只对「已不该有」的 id 执行 cancel。这样设备重启 / 系统清理 / 改时区后都能自愈。
 */
object ReminderScheduler {

    private const val PREFS = "reminder_state"
    private const val KEY_IDS = "scheduled_task_ids"

    /**
     * 全量对齐：给 [tasks] 排闹钟，并取消 [previouslyScheduled] 中不再需要的。
     * 调用方负责在后台线程提供任务快照（Room 是单一事实源）。
     */
    fun sync(context: Context, tasks: List<Task>, now: Long = System.currentTimeMillis()) {
        val plan = planReminderSync(tasks, readScheduledIds(context), now)
        plan.toSchedule.forEach { (taskId, at) -> setAlarm(context, taskId, at) }
        plan.toCancel.forEach { taskId -> cancelAlarm(context, taskId) }
        writeScheduledIds(context, plan.toSchedule.keys)
    }

    /** 单个任务的闹钟取消（删除任务 / 标记完成 / 用户点通知里的「完成」）。 */
    fun cancelForTask(context: Context, taskId: Long) {
        cancelAlarm(context, taskId)
        writeScheduledIds(context, readScheduledIds(context) - taskId)
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    /** 跳系统「闹钟和提醒」授权页（Android 12+）。 */
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private fun setAlarm(context: Context, taskId: Long, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        if (at <= System.currentTimeMillis()) return
        val pi = alarmPendingIntent(context, taskId, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        runCatching {
            if (canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                // 未授权精确闹钟：退化为非精确（省电策略下可能延迟几分钟），不丢提醒。
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        }.onFailure {
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) }
        }
    }

    private fun cancelAlarm(context: Context, taskId: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmPendingIntent(context, taskId, PendingIntent.FLAG_NO_CREATE) ?: return
        am.cancel(pi)
        pi.cancel()
    }

    private fun alarmPendingIntent(context: Context, taskId: Long, flags: Int): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_FIRE
            putExtra(ReminderReceiver.EXTRA_TASK_ID, taskId)
        }
        val requestCode = (taskId xor (taskId ushr 32)).toInt()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun readScheduledIds(context: Context): Set<Long> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_IDS, emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    private fun writeScheduledIds(context: Context, ids: Set<Long>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_IDS, ids.map { it.toString() }.toSet())
            .apply()
    }
}
