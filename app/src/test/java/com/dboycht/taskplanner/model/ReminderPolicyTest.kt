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
}
