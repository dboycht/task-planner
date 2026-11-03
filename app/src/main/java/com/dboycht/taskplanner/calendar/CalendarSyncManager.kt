package com.dboycht.taskplanner.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.dboycht.taskplanner.model.Task
import java.util.TimeZone

/**
 * 系统日历投影（M2，§2.2）：
 * - Room 是唯一事实源；本文件只做「把任务快照对齐到日历事件」的幂等全量同步。
 * - 默认写**专属日历**（ACCOUNT_TYPE_LOCAL，账户名带包名），可在设置页改选其他可写日历。
 * - 任何失败（权限被撤 / OEM 限制 / 无日历存储）都只记日志并放弃，绝不回滚任务；
 *   calendarEventId 仍为 null 的任务会在下一轮 maintenanceSync 重试。
 * - 所有方法都是阻塞 IO，调用方必须在 Dispatchers.IO 上（maintenanceSyncNow 已保证）。
 */
object CalendarSyncManager {

    private const val TAG = "CalendarSync"

    /** 专属日历颜色 #4A90D9（ARGB Int）。 */
    private val CALENDAR_COLOR: Int = 0xFF4A90D9.toInt()

    data class CalendarInfo(
        val id: Long,
        val displayName: String,
        val accountName: String,
        val isOurs: Boolean,
    )

    data class SyncResult(val created: Int, val updated: Int, val deleted: Int, val failed: Int) {
        override fun toString() = "created=$created updated=$updated deleted=$deleted failed=$failed"
    }

    fun hasPermission(context: Context): Boolean =
        READ_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    /**
     * 目标日历解析：用户选过且仍存在 ⇒ 用之；否则回退专属日历（不存在则创建）。
     * 返回 null = 无可写日历（未授权 / 无日历存储），同步本轮跳过。
     */
    suspend fun resolveTargetCalendar(context: Context): Long? {
        if (!hasPermission(context)) return null
        val selected = CalendarSettings.selectedCalendarId(context)
        if (selected > 0 && listCalendars(context).any { it.id == selected }) return selected
        return ensureDedicatedCalendar(context)
    }

    /** 可写日历清单（设置页选择用）：access ≥ CONTRIBUTOR 的可见日历。 */
    suspend fun listCalendars(context: Context): List<CalendarInfo> {
        if (!hasPermission(context)) return emptyList()
        val ours = CalendarSettings.localAccountName(context)
        val out = mutableListOf<CalendarInfo>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                ),
                "${CalendarContract.Calendars.VISIBLE}=1 AND " +
                    "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=?",
                arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
                "${CalendarContract.Calendars._ID} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(1) ?: continue
                    val account = c.getString(2) ?: ""
                    val level = c.getInt(3)
                    if (level < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) continue
                    out += CalendarInfo(
                        id = id,
                        displayName = name,
                        accountName = account,
                        isOurs = account == ours,
                    )
                }
            }
        }.onFailure { Log.w(TAG, "listCalendars failed", it) }
        return out
    }

    /**
     * 确保专属日历存在并返回 id。账户 = `包名.local` / `LOCAL`，只在查询不到时插入一次；
     * 并发插入靠 NAME+ACCOUNT 查询兜底，最坏情况产生重复日历也不会丢数据（v1 可接受）。
     */
    suspend fun ensureDedicatedCalendar(context: Context): Long? {
        if (!hasPermission(context)) return null
        val account = CalendarSettings.localAccountName(context)
        val existing = listCalendars(context).firstOrNull { it.accountName == account }
        if (existing != null) return existing.id
        return runCatching {
            val values = ContentValues().apply {
                put(CalendarContract.Calendars.NAME, CalendarSettings.CALENDAR_DISPLAY_NAME)
                put(
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarSettings.CALENDAR_DISPLAY_NAME,
                )
                put(CalendarContract.Calendars.ACCOUNT_NAME, account)
                put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarSettings.LOCAL_ACCOUNT_TYPE)
                put(CalendarContract.Calendars.OWNER_ACCOUNT, account)
                put(CalendarContract.Calendars.CALENDAR_COLOR, CALENDAR_COLOR) // 稳定蓝 #4A90D9
                put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_OWNER)
                put(CalendarContract.Calendars.VISIBLE, 1)
                put(CalendarContract.Calendars.SYNC_EVENTS, 1)
                put(CalendarContract.Calendars.CALENDAR_TIME_ZONE, TimeZone.getDefault().id)
            }
            // 建日历必须在 URI 上拼 ACCOUNT_NAME/ACCOUNT_TYPE（provider 约定），否则报 IllegalArgumentException。
            val uri = CalendarContract.Calendars.CONTENT_URI.buildUpon()
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarSettings.LOCAL_ACCOUNT_TYPE)
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .build()
            val newUri = context.contentResolver.insert(uri, values) ?: return null
            ContentUris.parseId(newUri)
        }.onFailure { Log.w(TAG, "ensureDedicatedCalendar failed", it) }.getOrNull()
    }

    /**
     * 全量对齐：[tasks]（Room 快照）逐一同步事件；[removed] 是刚被删除的任务（删除前快照），
     * 其事件在此清理。返回计数（日志与验收用）。
     */
    suspend fun syncAll(context: Context, tasks: List<Task>, removed: List<Task> = emptyList()): SyncResult {
        if (!CalendarSettings.syncEnabled(context)) return SyncResult(0, 0, 0, 0)
        if (!hasPermission(context)) return SyncResult(0, 0, 0, 0)
        var created = 0; var updated = 0; var deleted = 0; var failed = 0

        // 先清被删任务的事件（calendarEventId 为空说明从未写入，跳过）。
        for (task in removed) {
            val eventId = task.calendarEventId ?: continue
            if (deleteEvent(context, eventId)) deleted++ else failed++
        }

        val calendarId = resolveTargetCalendar(context)
        if (calendarId == null) {
            // 有权限但解析不出日历：不算失败轮空，等下一轮重试。
            return SyncResult(0, 0, deleted, 0)
        }
        // 日历侧允许的提醒方法里没有 ALERT ⇒ 本日历不写提醒（映射层封顶 5 个不变）。
        val maxReminders = if (remindersAllowedAlert(context, calendarId)) {
            CalendarMapper.MAX_REMINDERS_PER_EVENT
        } else 0

        for (task in tasks) {
            try {
                val range = CalendarMapper.eventRange(task)
                if (range == null) {
                    // 任务没有截止时间：事件无从谈起；曾写过则删掉，保持「投影=任务」。
                    if (task.calendarEventId != null && deleteEvent(context, task.calendarEventId)) {
                        deleted++
                        setEventId(context, task.id, null)
                    }
                    continue
                }
                val eventId = task.calendarEventId
                if (eventId != null && eventExists(context, eventId)) {
                    updateEvent(context, task, eventId, calendarId, maxReminders)
                    updated++
                } else if (eventId != null) {
                    // 事件在日历侧被用户删了：重建（投影自愈）。
                    val newId = insertEvent(context, task, calendarId, maxReminders)
                    if (newId != null) { created++; setEventId(context, task.id, newId) } else failed++
                } else {
                    val newId = insertEvent(context, task, calendarId, maxReminders)
                    if (newId != null) { created++; setEventId(context, task.id, newId) } else failed++
                }
            } catch (e: Exception) {
                failed++
                Log.w(TAG, "sync task ${task.id} failed", e)
            }
        }
        Log.i(TAG, "syncAll $created/$updated/$deleted/$failed calendar=$calendarId")
        return SyncResult(created, updated, deleted, failed)
    }

    /** 存量回填：把未完成且还没事件的Task写入日历，返回成功条数（列表页横幅 / 设置页触发）。 */
    suspend fun backfillUncompleted(context: Context, tasks: List<Task>): Int {
        if (!CalendarSettings.syncEnabled(context) || !hasPermission(context)) return 0
        val calendarId = resolveTargetCalendar(context) ?: return 0
        val maxReminders = if (remindersAllowedAlert(context, calendarId)) {
            CalendarMapper.MAX_REMINDERS_PER_EVENT
        } else 0
        var n = 0
        for (task in tasks) {
            if (task.status == com.dboycht.taskplanner.model.TaskStatus.DONE) continue
            if (task.dueTime == null) continue
            if (task.calendarEventId != null) continue
            val newId = runCatching { insertEvent(context, task, calendarId, maxReminders) }
                .onFailure { Log.w(TAG, "backfill task ${task.id} failed", it) }
                .getOrNull()
            if (newId != null) { setEventId(context, task.id, newId); n++ }
        }
        Log.i(TAG, "backfill created=$n")
        return n
    }

    // ---------- 事件级操作 ----------

    private suspend fun insertEvent(
        context: Context,
        task: Task,
        calendarId: Long,
        maxReminders: Int,
    ): Long? {
        val range = CalendarMapper.eventRange(task) ?: return null
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, CalendarMapper.eventTitle(task))
            put(CalendarContract.Events.DESCRIPTION, CalendarMapper.eventDescription(task))
            put(CalendarContract.Events.DTSTART, range.first)
            put(CalendarContract.Events.DTEND, range.second)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
            put(CalendarContract.Events.CUSTOM_APP_URI, "taskplanner://task/${task.id}")
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            ?: return null
        val eventId = ContentUris.parseId(uri)
        insertReminders(context, eventId, CalendarMapper.reminderMinutesBefore(task, maxReminders))
        return eventId
    }

    private suspend fun updateEvent(
        context: Context,
        task: Task,
        eventId: Long,
        calendarId: Long,
        maxReminders: Int,
    ) {
        val range = CalendarMapper.eventRange(task) ?: return
        val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, CalendarMapper.eventTitle(task))
            put(CalendarContract.Events.DESCRIPTION, CalendarMapper.eventDescription(task))
            put(CalendarContract.Events.DTSTART, range.first)
            put(CalendarContract.Events.DTEND, range.second)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        context.contentResolver.update(eventUri, values, null, null)
        // 提醒全删全插：点数少，且幂等比增量简单可靠。
        context.contentResolver.delete(
            CalendarContract.Reminders.CONTENT_URI,
            "${CalendarContract.Reminders.EVENT_ID}=?",
            arrayOf(eventId.toString()),
        )
        insertReminders(context, eventId, CalendarMapper.reminderMinutesBefore(task, maxReminders))
    }

    private suspend fun insertReminders(context: Context, eventId: Long, minutesBefore: List<Int>) {
        for (minutes in minutesBefore) {
            val values = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, minutes)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            }
            runCatching { context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values) }
                .onSuccess { uri ->
                    if (uri == null) {
                        Log.w(TAG, "insert reminder returned null (minutes=$minutes eventId=$eventId)")
                    } else {
                        Log.i(TAG, "insert reminder ok (minutes=$minutes eventId=$eventId)")
                    }
                }
                .onFailure { Log.w(TAG, "insert reminder failed (minutes=$minutes)", it) }
        }
    }

    private suspend fun deleteEvent(context: Context, eventId: Long): Boolean = runCatching {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        context.contentResolver.delete(uri, null, null) > 0
    }.onFailure { Log.w(TAG, "delete event $eventId failed", it) }.getOrDefault(false)

    private suspend fun eventExists(context: Context, eventId: Long): Boolean = runCatching {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        context.contentResolver.query(uri, arrayOf(CalendarContract.Events._ID), null, null, null)
            ?.use { it.count > 0 } ?: false
    }.onFailure { Log.w(TAG, "query event $eventId failed", it) }.getOrDefault(false)

    private fun remindersAllowedAlert(context: Context, calendarId: Long): Boolean = runCatching {
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars.ALLOWED_REMINDERS),
            "${CalendarContract.Calendars._ID}=?",
            arrayOf(calendarId.toString()),
            null,
        )?.use { c ->
            if (c.moveToFirst()) {
                val raw = c.getString(0)
                // 该列存的是**方法数字码**（如 "0,1" = DEFAULT,ALERT），不是 "METHOD_*" 名字。
                // 空串/NULL/解析不了都按「允许」处理（AOSP 默认支持 ALERT）。
                raw.isNullOrBlank() ||
                    raw.split(',').mapNotNull { it.trim().toIntOrNull() }.let { codes ->
                        codes.isEmpty() ||
                            codes.contains(CalendarContract.Reminders.METHOD_DEFAULT) ||
                            codes.contains(CalendarContract.Reminders.METHOD_ALERT)
                    }
            } else true
        } ?: true
    }.getOrDefault(true)

    /** 回写映射（失败仅日志：下一轮会重建事件，映射不丢任务数据）。 */
    private suspend fun setEventId(context: Context, taskId: Long, eventId: Long?) {
        runCatching { CalendarSyncBridge.repository(context).setCalendarEventId(taskId, eventId) }
            .onFailure { Log.w(TAG, "setEventId task=$taskId -> $eventId failed", it) }
    }

    private val READ_PERMISSIONS = listOf(
        Manifest.permission.READ_CALENDAR,
        Manifest.permission.WRITE_CALENDAR,
    )
}

/**
 * CalendarSyncManager 需要把 calendarEventId 写回 Room；经 Application 单例桥接
 * （TaskPlannerApp 实现本接口），避免 content-provider 逻辑直接持有仓库构造依赖。
 */
interface CalendarSyncBridge {
    fun taskRepository(): com.dboycht.taskplanner.data.TaskRepository

    companion object {
        fun repository(context: Context): com.dboycht.taskplanner.data.TaskRepository =
            (context.applicationContext as CalendarSyncBridge).taskRepository()
    }
}
