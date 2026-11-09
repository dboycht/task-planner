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
    private const val KEY_EXPECTED_AT = "expected_trigger_at"

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

    /**
     * 记下**我们真的向系统排了一条闹钟**的时刻（调度层调用，见 [ReminderScheduler.sync]）。
     *
     * 存在的理由：判定「提醒是不是被系统压掉了」时，只该追究**我们确实排过、且已经到点**
     * 的闹钟。任务上存在一个过去的提醒点 ≠ 我们为它排过闹钟 —— 调度层每条任务只排
     * 「下一个尚未到点的提醒」（`planReminderSync`），早已过去的历史提醒点从来没排上过，
     * 却会被 `collectReminderTriggerTimes` 摊开成「应响未响」，于是横幅长期误报。
     *
     * 只记**最近一次**排程时刻，不做历史簿记：判定只需回答「上一次我排的闹钟响了没有」，
     * 更早的排程已被后续排程取代（覆盖语义，见 [ReminderScheduler] 的类型注释）。
     */
    fun markExpectedTrigger(context: Context, at: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_EXPECTED_AT, at)
            .apply()
    }

    /** 最近一次排入系统的闹钟时刻；null = 从未排过。 */
    fun expectedTriggerAt(context: Context): Long? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_EXPECTED_AT)) return null
        val value = prefs.getLong(KEY_EXPECTED_AT, 0L)
        return if (value > 0L) value else null
    }
}
