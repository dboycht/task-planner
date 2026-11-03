package com.dboycht.taskplanner.calendar

import com.dboycht.taskplanner.model.ReminderAnchor
import com.dboycht.taskplanner.model.ReminderPoint
import com.dboycht.taskplanner.model.ReminderPolicy
import com.dboycht.taskplanner.model.Task
import com.dboycht.taskplanner.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarMapperTest {

    private val due = 1_799_913_600_000L // 任意固定时刻
    private val start = due - 2L * 60L * 60_000L // 截止前 2 小时开始

    private fun task(
        title: String = "写报告",
        priority: Int = 90,
        status: TaskStatus = TaskStatus.TODO,
        startTime: Long? = start,
        dueTime: Long? = due,
        policy: ReminderPolicy = ReminderPolicy(listOf(ReminderPoint(ReminderAnchor.DUE, 60))),
        id: Long = 12L,
    ) = Task(
        id = id,
        title = title,
        description = "正文",
        startTime = startTime,
        dueTime = dueTime,
        priority = priority,
        status = status,
        reminderPolicy = policy,
    )

    // ---------- 标题 ----------

    @Test
    fun `标题 = ✓前缀加优先级档位`() {
        assertEquals("[高] 写报告", CalendarMapper.eventTitle(task(status = TaskStatus.TODO)))
        assertEquals("✓ [高] 写报告", CalendarMapper.eventTitle(task(status = TaskStatus.DONE)))
        assertEquals("[中] 写报告", CalendarMapper.eventTitle(task(priority = 50)))
        assertEquals("[低] 写报告", CalendarMapper.eventTitle(task(priority = 10)))
    }

    // ---------- 起止 ----------

    @Test
    fun `有开始 用开始到截止`() {
        assertEquals(start to due, CalendarMapper.eventRange(task()))
    }

    @Test
    fun `无开始 默认截止前1小时`() {
        val range = CalendarMapper.eventRange(task(startTime = null))!!
        assertEquals(due - CalendarMapper.DEFAULT_DURATION_MS, range.first)
        assertEquals(due, range.second)
    }

    @Test
    fun `无截止 无事件`() {
        assertNull(CalendarMapper.eventRange(task(dueTime = null)))
    }

    @Test
    fun `起止倒挂 收窄为开始时刻`() {
        val range = CalendarMapper.eventRange(task(startTime = due + 60_000L))!!
        assertEquals(due + 60_000L, range.first)
        assertEquals(due + 60_000L, range.second)
    }

    // ---------- 描述 ----------

    @Test
    fun `描述带任务id标记`() {
        val desc = CalendarMapper.eventDescription(task())
        assertTrue(desc.startsWith("正文"))
        assertTrue(desc.contains("任务 ID 12"))
    }

    @Test
    fun `空描述只留标记`() {
        val desc = CalendarMapper.eventDescription(task(id = 7).copy(description = ""))
        assertEquals("由任务计划同步 · 任务 ID 7", desc)
    }

    // ---------- 日历提醒（分钟-前） ----------

    @Test
    fun `无开始任务 默认策略截止前1小时 = 事件开始到点`() {
        // 事件从截止前 1 小时开始；「截止前 1 小时」的提醒正好落在事件开始 → 0 分钟前。
        val mins = CalendarMapper.reminderMinutesBefore(task(startTime = null))
        assertEquals(listOf(0), mins)
    }

    @Test
    fun `开始锚点换算为事件开始前的分钟数`() {
        // 开始前 30 分钟提醒：事件开始 = 开始时刻 → 30 分钟前。
        val policy = ReminderPolicy(listOf(ReminderPoint(ReminderAnchor.START, 30)))
        assertEquals(listOf(30), CalendarMapper.reminderMinutesBefore(task(policy = policy)))
    }

    @Test
    fun `落在事件开始之后的提醒点被跳过`() {
        // 任务时长 2 小时，截止前 60 分钟的提醒在开始后 1 小时 → 日历表达不了，跳过。
        val mins = CalendarMapper.reminderMinutesBefore(task())
        assertTrue(mins.isEmpty())
    }

    @Test
    fun `去重排序且封顶5个`() {
        // 事件开始 = 截止前 120 分钟；DUE 锚点 x 分钟 ⇒ 开始前 (x-120) 分钟，负数被跳过。
        val policy = ReminderPolicy(
            listOf(
                ReminderPoint(ReminderAnchor.START, 60), // 60
                ReminderPoint(ReminderAnchor.START, 30), // 30
                ReminderPoint(ReminderAnchor.START, 60), // 重复点 60
                ReminderPoint(ReminderAnchor.START, 10), // 10
                ReminderPoint(ReminderAnchor.DUE, 130),  // 130-120 = 10（与开始锚点撞车）
                ReminderPoint(ReminderAnchor.DUE, 135),  // 15
                ReminderPoint(ReminderAnchor.DUE, 140),  // 20
                ReminderPoint(ReminderAnchor.START, 0),  // 0（到点）
            )
        )
        // 可表达集合（分钟前）：{60,30,10,0,15,20} → 排序去重取前 5：0,10,15,20,30。
        assertEquals(listOf(0, 10, 15, 20, 30), CalendarMapper.reminderMinutesBefore(task(policy = policy)))
    }

    @Test
    fun `无截止任务没有日历提醒`() {
        assertTrue(CalendarMapper.reminderMinutesBefore(task(dueTime = null)).isEmpty())
    }
}
