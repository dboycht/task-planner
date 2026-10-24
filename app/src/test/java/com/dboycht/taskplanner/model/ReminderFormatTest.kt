package com.dboycht.taskplanner.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderFormatTest {

    // ---------- formatOffsetMinutes ----------

    @Test
    fun `能被天整除用天`() {
        assertEquals("1 天", formatOffsetMinutes(1440))
        assertEquals("3 天", formatOffsetMinutes(4320))
        assertEquals("7 天", formatOffsetMinutes(10080))
    }

    @Test
    fun `能被小时整除用小时`() {
        assertEquals("1 小时", formatOffsetMinutes(60))
        assertEquals("2 小时", formatOffsetMinutes(120))
    }

    @Test
    fun `不能整除保留分钟`() {
        assertEquals("30 分钟", formatOffsetMinutes(30))
        assertEquals("90 分钟", formatOffsetMinutes(90))
        assertEquals("25 分钟", formatOffsetMinutes(25))
    }

    // ---------- summary ----------

    @Test
    fun `空策略摘要为不提醒`() {
        assertEquals("不提醒", ReminderPolicy(emptyList()).summary())
    }

    @Test
    fun `默认策略摘要`() {
        assertEquals("截止前 1 小时", ReminderPolicy.DEFAULT.summary())
    }

    @Test
    fun `用户示例预设的多点摘要`() {
        val policy = ReminderPolicy(
            listOf(
                ReminderPoint(ReminderAnchor.DUE, 10080),
                ReminderPoint(ReminderAnchor.DUE, 4320),
                ReminderPoint(ReminderAnchor.DUE, 60),
            )
        )
        assertEquals("截止前 7 天、截止前 3 天、截止前 1 小时", policy.summary())
    }

    @Test
    fun `零偏移特判为到点`() {
        assertEquals(
            "开始时、截止前 30 分钟",
            ReminderPolicy(
                listOf(
                    ReminderPoint(ReminderAnchor.START, 0),
                    ReminderPoint(ReminderAnchor.DUE, 30),
                )
            ).summary(),
        )
        assertEquals("截止时", ReminderPolicy(listOf(ReminderPoint(ReminderAnchor.DUE, 0))).summary())
    }

    // ---------- 内置预设 ----------

    @Test
    fun `内置预设名称互不重复且全部有效`() {
        assertEquals(4, BUILT_IN_PRESETS.size)
        assertEquals(4, BUILT_IN_PRESETS.map { it.name }.toSet().size)
        BUILT_IN_PRESETS.forEach { preset ->
            assertEquals(true, preset.isBuiltIn)
            assert(preset.policy.points.isNotEmpty()) { "内置预设 ${preset.name} 不能是空策略" }
        }
    }

    @Test
    fun `内置第三项正是用户示例的7天3天1小时`() {
        val policy = BUILT_IN_PRESETS[2].policy
        assertEquals(listOf(10080, 4320, 60), policy.points.map { it.offsetMinutes })
        assertEquals("截止前 7 天、截止前 3 天、截止前 1 小时", policy.summary())
    }
}
