package com.dboycht.taskplanner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RecurrenceGeneratorTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun task(
        id: Long,
        title: String = "任务",
        due: Long? = at(2026, 11, 2, 20, 0), // 周一 20:00
        start: Long? = null,
        rule: RecurrenceRule = RecurrenceRule.NONE,
        group: String? = if (rule.isRecurring) "g1" else null,
        status: TaskStatus = TaskStatus.TODO,
    ) = Task(
        id = id,
        title = title,
        startTime = start,
        dueTime = due,
        reminderPolicy = ReminderPolicy(listOf(ReminderPoint(ReminderAnchor.DUE, 60))),
        recurRule = rule,
        recurGroup = group,
        status = status,
    )

    /** now = 周三 2026-11-04 12:00。 */
    private val now = at(2026, 11, 4, 12, 0)

    @Test
    fun `未来已有未完成实例 → 保底已满足 不生成`() {
        // 种子 = 下周一 11-09（未来、未完成）→ 系列已有一条未来，不再物化 11-16。
        val seed = task(1, due = at(2026, 11, 9, 20, 0), rule = RecurrenceRule(RecurKind.WEEKLY))
        assertTrue(RecurrenceGenerator.planNextInstances(listOf(seed), now, zone).isEmpty())
    }

    @Test
    fun `种子已过期且下一次也过期 → 虚拟跳过 不补史`() {
        // 种子 = 上周一 10-26；跳过 11-02（已过），物化 11-09。
        val seed = task(1, due = at(2026, 10, 26, 20, 0), rule = RecurrenceRule(RecurKind.WEEKLY))
        val plan = RecurrenceGenerator.planNextInstances(listOf(seed), now, zone)
        assertEquals(1, plan.size)
        assertEquals(at(2026, 11, 9, 20, 0), plan[0].dueTime)
    }

    @Test
    fun `未来实例已完成 → 从它继续生成下一次`() {
        // 用户提前把 11-09 勾完：未来再无未完成 → 物化 11-16。
        val seed = task(
            1,
            due = at(2026, 11, 9, 20, 0),
            rule = RecurrenceRule(RecurKind.WEEKLY),
            status = TaskStatus.DONE,
        )
        val plan = RecurrenceGenerator.planNextInstances(listOf(seed), now, zone)
        assertEquals(1, plan.size)
        assertEquals(at(2026, 11, 16, 20, 0), plan[0].dueTime)
    }

    @Test
    fun `种已完成但无未来实例 → 仍保底生成下一次（滚动语义）`() {
        // 忘了点完成也照样有下周任务：种子唯一且已完成，下一次照样物化。
        val seed = task(
            1,
            due = at(2026, 10, 26, 20, 0),
            rule = RecurrenceRule(RecurKind.WEEKLY),
            status = TaskStatus.DONE,
        )
        val plan = RecurrenceGenerator.planNextInstances(listOf(seed), now, zone)
        assertEquals(1, plan.size)
        assertEquals(at(2026, 11, 9, 20, 0), plan[0].dueTime)
    }

    @Test
    fun `逾期未完成存在但未来已提前完成 → 仍生成未来`() {
        // 10-26 忘了完成（逾期）+ 11-09 已提前勾完 → 无未来未完成 → 物化 11-16。
        val overdue = task(1, due = at(2026, 10, 26, 20, 0), rule = RecurrenceRule(RecurKind.WEEKLY))
        val done = task(
            2,
            due = at(2026, 11, 9, 20, 0),
            rule = RecurrenceRule(RecurKind.WEEKLY),
            status = TaskStatus.DONE,
        )
        val plan = RecurrenceGenerator.planNextInstances(listOf(overdue, done), now, zone)
        assertEquals(1, plan.size)
        assertEquals(at(2026, 11, 16, 20, 0), plan[0].dueTime)
    }

    @Test
    fun `90 天封顶 → 跨度外不物化`() {
        // 唯一实例已完成（不构成未来保底），下一次超过 90 天 → 不生成。
        val farSeed = task(
            1,
            due = now + 89L * 86_400_000L,
            rule = RecurrenceRule(RecurKind.WEEKLY),
            status = TaskStatus.DONE,
        )
        assertTrue(RecurrenceGenerator.planNextInstances(listOf(farSeed), now, zone).isEmpty())
        // 下一次 = now + 87 天 ≤ 90 天 → 生成。
        val nearSeed = task(
            2,
            due = now + 80L * 86_400_000L,
            rule = RecurrenceRule(RecurKind.WEEKLY),
            status = TaskStatus.DONE,
        )
        assertEquals(1, RecurrenceGenerator.planNextInstances(listOf(nearSeed), now, zone).size)
    }

    @Test
    fun `无截止种子与不重复任务都不生成`() {
        assertTrue(
            RecurrenceGenerator.planNextInstances(
                listOf(task(1, due = null, rule = RecurrenceRule(RecurKind.DAILY))),
                now, zone,
            ).isEmpty()
        )
        assertTrue(
            RecurrenceGenerator.planNextInstances(
                listOf(task(2, rule = RecurrenceRule.NONE)),
                now, zone,
            ).isEmpty()
        )
    }

    @Test
    fun `startTime 按同一位移平移 保持时长`() {
        val due = at(2026, 10, 26, 20, 0)
        val start = at(2026, 10, 26, 18, 0)
        val seed = task(1, due = due, start = start, rule = RecurrenceRule(RecurKind.WEEKLY))
        val plan = RecurrenceGenerator.planNextInstances(listOf(seed), now, zone)
        assertEquals(1, plan.size)
        assertEquals(at(2026, 11, 9, 18, 0), plan[0].startTime)
    }

    @Test
    fun `多组各自计划 互不干扰`() {
        val a = task(1, title = "A", due = at(2026, 10, 26, 20, 0), rule = RecurrenceRule(RecurKind.WEEKLY), group = "gA")
        val b = task(
            2, title = "B", due = at(2026, 11, 3, 8, 0),
            rule = RecurrenceRule(RecurKind.DAILY), group = "gB",
        )
        val plan = RecurrenceGenerator.planNextInstances(listOf(a, b), now, zone)
        assertEquals(2, plan.size)
        assertEquals(at(2026, 11, 9, 20, 0), plan.first { it.source.title == "A" }.dueTime)
        assertEquals(at(2026, 11, 5, 8, 0), plan.first { it.source.title == "B" }.dueTime)
    }

    @Test
    fun `并列 dueTime 取 id 大者为种子`() {
        // 两个实例都已完成（无未来保底）→ 种子取 id 大者（规则取每天）→ 下一次 = 11-10。
        val old = task(
            1, title = "旧", due = at(2026, 11, 9, 20, 0),
            rule = RecurrenceRule(RecurKind.WEEKLY), status = TaskStatus.DONE,
        )
        val new = task(
            2, title = "新", due = at(2026, 11, 9, 20, 0),
            rule = RecurrenceRule(RecurKind.DAILY), status = TaskStatus.DONE,
        )
        val plan = RecurrenceGenerator.planNextInstances(listOf(old, new), now, zone)
        assertEquals(1, plan.size)
        assertEquals("新", plan[0].source.title)
        assertEquals(at(2026, 11, 10, 20, 0), plan[0].dueTime)
    }

    @Test
    fun `极端旧种子 跳步耗尽也不物化过去时刻`() {
        // 1970 年的日任务：要么正常跳到未来，要么空计划；绝不允许出现过去时刻的实例。
        val plan = RecurrenceGenerator.planNextInstances(
            listOf(task(1, due = at(1970, 1, 1, 0, 0), rule = RecurrenceRule(RecurKind.DAILY))),
            now, zone,
        )
        plan.forEach { assertTrue("不得物化过去: ${it.dueTime}", it.dueTime > now) }
    }
}
