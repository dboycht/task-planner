package com.dboycht.taskplanner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderPolicyTest {

    @Test
    fun `默认策略编码为 D60`() {
        assertEquals("D:60", ReminderPolicy.DEFAULT.encode())
    }

    @Test
    fun `空与 null 解码为空策略`() {
        assertTrue(ReminderPolicy.decode(null).points.isEmpty())
        assertTrue(ReminderPolicy.decode("").points.isEmpty())
        assertTrue(ReminderPolicy.decode("   ").points.isEmpty())
    }

    @Test
    fun `编码解码往返一致`() {
        val policy = ReminderPolicy(
            listOf(
                ReminderPoint(ReminderAnchor.START, 0),
                ReminderPoint(ReminderAnchor.DUE, 1440),
            )
        )
        assertEquals("S:0,D:1440", policy.encode())
        assertEquals(policy, ReminderPolicy.decode(policy.encode()))
    }

    @Test
    fun `宽容解码忽略非法 token`() {
        val decoded = ReminderPolicy.decode("X:5,D:abc,D:-3,S:10,,D:")
        assertEquals(listOf(ReminderPoint(ReminderAnchor.START, 10)), decoded.points)
    }

    @Test
    fun `triggersFor 按锚点减偏移计算`() {
        val policy = ReminderPolicy(
            listOf(
                ReminderPoint(ReminderAnchor.DUE, 60),
                ReminderPoint(ReminderAnchor.START, 0),
            )
        )
        val start = 1_000_000_000_000L
        val due = 1_000_003_600_000L
        assertEquals(
            listOf(due - 60 * 60_000L, start),
            policy.triggersFor(startTime = start, dueTime = due),
        )
    }

    @Test
    fun `锚点缺失时跳过对应提醒点`() {
        val policy = ReminderPolicy(
            listOf(
                ReminderPoint(ReminderAnchor.START, 0),
                ReminderPoint(ReminderAnchor.DUE, 60),
            )
        )
        assertEquals(
            emptyList<Long>(),
            policy.triggersFor(startTime = null, dueTime = null),
        )
        assertEquals(
            listOf(123L - 60 * 60_000L),
            policy.triggersFor(startTime = null, dueTime = 123L),
        )
    }

    // ---------- 可用性判定（预设灰化） ----------

    @Test
    fun `空策略恒可用`() {
        assertEquals(true, ReminderPolicy(emptyList()).isUsableFor(null, null))
    }

    @Test
    fun `含截止提醒的策略需截止时间`() {
        val dueOnly = ReminderPolicy(listOf(ReminderPoint(ReminderAnchor.DUE, 60)))
        assertEquals(false, dueOnly.isUsableFor(startTime = 123L, dueTime = null))
        assertEquals(true, dueOnly.isUsableFor(startTime = null, dueTime = 456L))
        assertEquals("需先设置截止时间", dueOnly.missingAnchorLabel(123L, null))
        assertEquals(null, dueOnly.missingAnchorLabel(null, 456L))
    }

    @Test
    fun `含开始提醒的策略需开始时间`() {
        val startOnly = ReminderPolicy(listOf(ReminderPoint(ReminderAnchor.START, 0)))
        assertEquals(false, startOnly.isUsableFor(startTime = null, dueTime = 456L))
        assertEquals("需先设置开始时间", startOnly.missingAnchorLabel(null, 456L))
    }

    @Test
    fun `双锚点策略缺任一即不可用并给出完整原因`() {
        val both = ReminderPolicy(
            listOf(
                ReminderPoint(ReminderAnchor.START, 0),
                ReminderPoint(ReminderAnchor.DUE, 30),
            )
        )
        assertEquals(false, both.isUsableFor(null, null))
        assertEquals("需先设置开始与截止时间", both.missingAnchorLabel(null, null))
        assertEquals(false, both.isUsableFor(1L, null))
        assertEquals("需先设置截止时间", both.missingAnchorLabel(1L, null))
        assertEquals(true, both.isUsableFor(1L, 2L))
        assertEquals(null, both.missingAnchorLabel(1L, 2L))
    }
}
