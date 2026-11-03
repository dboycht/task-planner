package com.dboycht.taskplanner.calendar

import android.content.Context

/**
 * 日历同步设置（SharedPreferences，非 Room：不属于任务真数据）。
 * account 名带包名：debug（.debug 后缀）与正式包各用各的专属日历，互不相通。
 */
object CalendarSettings {

    private const val PREFS = "calendar_settings"
    private const val KEY_SYNC_ENABLED = "sync_enabled"
    private const val KEY_CALENDAR_ID = "calendar_id"
    private const val KEY_BACKFILL_DONE = "backfill_done"
    private const val KEY_BACKFILL_DISMISSED = "backfill_dismissed"

    /** 专属日历账户名（运行时按包名区分 debug/正式）。 */
    fun localAccountName(context: Context): String = context.packageName + ".local"

    /** 专属日历账户类型：CalendarContract.ACCOUNT_TYPE_LOCAL（仅本机，不参与账号同步）。 */
    const val LOCAL_ACCOUNT_TYPE = "LOCAL"

    /** 专属日历显示名。 */
    const val CALENDAR_DISPLAY_NAME = "Task Planner"

    /** 同步总开关（默认开；权限未给时实际不写，只影响 UI 引导）。 */
    fun syncEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SYNC_ENABLED, true)

    fun setSyncEnabled(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_SYNC_ENABLED, value).apply()
    }

    /** 用户选定的目标日历 id；-1 = 自动（用专属日历）。 */
    fun selectedCalendarId(context: Context): Long =
        prefs(context).getLong(KEY_CALENDAR_ID, -1L)

    fun setSelectedCalendarId(context: Context, id: Long) {
        prefs(context).edit().putLong(KEY_CALENDAR_ID, id).apply()
    }

    /** 存量回填已完成（含用户主动跳过 ⇒ 不再主动弹）。 */
    fun backfillDone(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BACKFILL_DONE, false)

    fun setBackfillDone(context: Context) {
        prefs(context).edit().putBoolean(KEY_BACKFILL_DONE, true).apply()
    }

    fun backfillDismissed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BACKFILL_DISMISSED, false)

    fun setBackfillDismissed(context: Context) {
        prefs(context).edit().putBoolean(KEY_BACKFILL_DISMISSED, true).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
