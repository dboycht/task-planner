package com.dboycht.taskplanner.reminder

import android.content.Context

/**
 * 「提醒到底有没有真的响过」的本地记录。
 *
 * 存在的理由：厂商把应用的耗电策略设成「智能优化」时，闹钟会被系统静默推迟好几天
 * （见 [backstageGuidanceBody] 与 ERROR.md E5）。这个状态**没有任何系统 API 可查询**，
 * 唯一能拿到的证据是「闹钟有没有真的到点把我们叫起来」—— 于是每次真正发出通知时
 * 记一笔时间戳，列表页据此判断是否要向用户出示引导。
 *
 * 只存一个 `Long`，与 [ReminderScheduler] 一样落在 SharedPreferences，进程重建不丢。
 */
object ReminderHealth {

    private const val PREFS = "reminder_health"
    private const val KEY_LAST_DELIVERED = "last_delivered_at"

    /** 在**真正发出通知之后**调用（不要让调度成功就记，否则会把压制误判成正常）。 */
    fun markDelivered(context: Context, at: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_DELIVERED, at)
            .apply()
    }

    fun lastDeliveredAt(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_LAST_DELIVERED)) return null
        val value = prefs.getLong(KEY_LAST_DELIVERED, 0L)
        return if (value > 0L) value else null
    }
}
