package com.dboycht.taskplanner.reminder

import com.dboycht.taskplanner.model.ReminderAnchor
import com.dboycht.taskplanner.model.ReminderPoint
import com.dboycht.taskplanner.model.ReminderPolicy
import com.dboycht.taskplanner.model.Task
import com.dboycht.taskplanner.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ReminderTimeTest {

    private val hour = 60 * 60_000L

    private fun policy(vararg points: ReminderPoint) = ReminderPolicy(points.toList())

    private fun task(
        id: Long,
        start: Long? = null,
        due: Long? = null,
        status: TaskStatus = TaskStatus.TODO,
        policy: ReminderPolicy = ReminderPolicy.DEFAULT,
    ) = Task(id = id, title = "t$id", startTime = start, dueTime = due, status = status, reminderPolicy = policy)

    // ---------- 触发点展开 ----------

    @Test
    fun `dueReminders 按时刻升序且跳过缺失锚点`() {
        val now = 0L
        val p = policy(
            ReminderPoint(ReminderAnchor.DUE, 60),
            ReminderPoint(ReminderAnchor.START, 0),
            ReminderPoint(ReminderAnchor.DUE, 1440),
        )
        val start = 10 * hour
        val due = 100 * hour
        val list = p.dueReminders(start, due)
        assertEquals(listOf(start, due - 24 * hour, due - hour), list.map { it.triggerAt })
        // 只有截止时间时，开始类提醒点被跳过
        assertEquals(listOf(due - 24 * hour, due - hour), p.dueReminders(null, due).map { it.triggerAt })
        assertEquals(emptyList<DueReminder>(), ReminderPolicy(emptyList()).dueReminders(now, now))
    }

    // ---------- 下一个提醒 ----------

    @Test
    fun `nextReminder 严格取未来最早一点`() {
        val due = 100 * hour
        val p = policy(
            ReminderPoint(ReminderAnchor.DUE, 60),
            ReminderPoint(ReminderAnchor.DUE, 1440),
        )
        assertEquals(due - 24 * hour, p.nextReminder(null, due, now = 0L)?.triggerAt)
        assertEquals(due - hour, p.nextReminder(null, due, now = due - 24 * hour)?.triggerAt)
        // 恰好等于触发时刻的点算「已到」，不再作为未来点返回
        assertEquals(null, p.nextReminder(null, due, now = due - hour))
        assertEquals(null, p.nextReminder(null, null, now = 0L))
        assertEquals(null, p.nextReminder(null, due, now = due + hour))
    }

    @Test
    fun `reminderDueNow 取已到点里最晚的一个`() {
        val due = 100 * hour
        val p = policy(
            ReminderPoint(ReminderAnchor.DUE, 60),
            ReminderPoint(ReminderAnchor.DUE, 1440),
        )
        assertEquals(null, p.reminderDueNow(null, due, now = 0L))
        assertEquals(due - 24 * hour, p.reminderDueNow(null, due, now = due - 20 * hour)?.triggerAt)
        // 错过多个点时不连发，只报最近的那个
        assertEquals(due - hour, p.reminderDueNow(null, due, now = due - 30 * 60_000L)?.triggerAt)
    }

    // ---------- 是否值得挂闹钟 ----------

    @Test
    fun `needsReminder 排除已完成与无未来点`() {
        val due = 100 * hour
        assertTrue(task(1, due = due).needsReminder(now = 0L))
        assertFalse(task(1, due = due, status = TaskStatus.DONE).needsReminder(now = 0L))
        // 已过截止且策略点全部过期 → 不挂
        assertFalse(task(1, due = due).needsReminder(now = due + hour))
        // 空策略（不提醒）→ 不挂
        assertFalse(task(1, due = due, policy = ReminderPolicy(emptyList())).needsReminder(now = 0L))
        // 无时间 → 不挂
        assertFalse(task(1).needsReminder(now = 0L))
    }

    // ---------- 全量同步计划 ----------

    @Test
    fun `plan 为活跃任务排最早点 并取消已完成与已删除`() {
        val due = 100 * hour
        val tasks = listOf(
            task(1, due = due, policy = policy(ReminderPoint(ReminderAnchor.DUE, 60), ReminderPoint(ReminderAnchor.DUE, 120))),
            task(2, due = due, status = TaskStatus.DONE),
            task(3, due = -2 * hour), // 截止与提醒点都已过去
            task(4), // 无时间
        )
        val plan = planReminderSync(tasks, previouslyScheduled = setOf(1L, 2L, 3L, 9L), now = 0L)
        assertEquals(mapOf(1L to due - 120 * 60_000L), plan.toSchedule)
        assertEquals(setOf(2L, 3L, 9L), plan.toCancel)
    }

    @Test
    fun `plan 每次都为已挂任务重新排期 便于重启后自愈`() {
        val due = 100 * hour
        val plan = planReminderSync(listOf(task(1, due = due)), previouslyScheduled = setOf(1L), now = 0L)
        assertEquals(mapOf(1L to due - hour), plan.toSchedule)
        assertTrue(plan.toCancel.isEmpty())
    }

    @Test
    fun `plan 忽略未入库的草稿任务`() {
        val plan = planReminderSync(listOf(task(0, due = 100 * hour)), emptySet(), now = 0L)
        assertTrue(plan.toSchedule.isEmpty())
    }

    // ---------- 通知文案 ----------

    @Test
    fun `通知正文区分截止与开始 并带时刻`() {
        val zone = ZoneId.of("Asia/Shanghai")
        val due = java.time.ZonedDateTime.of(2026, 10, 29, 18, 0, 0, 0, zone)
            .toInstant().toEpochMilli()
        val t = task(1, start = due - 2 * hour, due = due)

        val oneHour = ReminderPolicy.DEFAULT.nextReminder(null, due, now = 0L)!!
        assertEquals("1 小时后截止（10-29 18:00）", reminderNotificationText(t, oneHour, zone))

        val atDue = DueReminder(due, ReminderAnchor.DUE, 0)
        assertEquals("现在到截止时间（10-29 18:00）", reminderNotificationText(t, atDue, zone))

        val atStart = DueReminder(due - 2 * hour, ReminderAnchor.START, 0)
        assertEquals("现在开始（10-29 16:00）", reminderNotificationText(t, atStart, zone))

        val beforeStart = DueReminder(due - 3 * hour, ReminderAnchor.START, 60)
        assertEquals("1 小时后开始（10-29 16:00）", reminderNotificationText(t, beforeStart, zone))
    }

    @Test
    fun `通知正文在缺失锚点时刻时退化为无时刻文案`() {
        val now = 1_700_000_000_000L
        val reminder = DueReminder(now, ReminderAnchor.DUE, 30)
        assertEquals("30 分钟后截止", reminderNotificationText(task(1), reminder, ZoneId.of("UTC")))
        assertNull(DueReminder(now, ReminderAnchor.DUE, 0).waitingLabel())
    }
}
