package com.dboycht.taskplanner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 「自定义」提醒点的天/小时/分钟组合输入（2026-11-02 第十七轮）。 */
class CustomOffsetTest {

    // ---------- 三单位合成 ----------

    @Test
    fun `天小时分钟按 1440 60 1 合成`() {
        assertEquals(1590, CustomOffsetInput("1", "2", "30").toOffsetMinutes())
        assertEquals(90, CustomOffsetInput("", "1", "30").toOffsetMinutes())
        assertEquals(1440, CustomOffsetInput("1", "", "").toOffsetMinutes())
        assertEquals(1, CustomOffsetInput("", "", "1").toOffsetMinutes())
    }

    @Test
    fun `空框按 0 计，全空即到点`() {
        assertEquals(0, CustomOffsetInput("", "", "").toOffsetMinutes())
        assertEquals(0, CustomOffsetInput("0", "0", "0").toOffsetMinutes())
        assertEquals(60, CustomOffsetInput("", "1", "").toOffsetMinutes())
        assertTrue(CustomOffsetInput("", "", "").isBlank)
        assertFalse(CustomOffsetInput("0", "", "").isBlank)
    }

    @Test
    fun `非纯数字文本不抛异常且按 0 计`() {
        // UI 层已过滤数字，纯函数仍需自防：`-5` 不能被读成 5，`1.5` 不能被读成 15。
        assertEquals(60, CustomOffsetInput("abc", "1", "-5").toOffsetMinutes())
        assertEquals(0, CustomOffsetInput("1.5", "", "").toOffsetMinutes())
        assertEquals(1440, CustomOffsetInput("1", "x", " ").toOffsetMinutes())
    }

    // ---------- 上限与溢出 ----------

    @Test
    fun `超出上限返回 null 而不是溢出成负数`() {
        assertNull(CustomOffsetInput("999999", "", "").toOffsetMinutes()) // 1 439 998 560 分钟
        assertNull(CustomOffsetInput("694", "12", "").toOffsetMinutes()) // 999 360 + 720 > 上限
        // 上限边界：694 天 10 小时 40 分钟 = 999 360 + 600 + 40 = 1 000 000
        assertEquals(MAX_CUSTOM_OFFSET_MINUTES, CustomOffsetInput("694", "10", "40").toOffsetMinutes())
        // 差一分钟就越界
        assertNull(CustomOffsetInput("694", "10", "41").toOffsetMinutes())
    }

    @Test
    fun `上限是个能正常排版的正数`() {
        assertEquals(1_000_000, MAX_CUSTOM_OFFSET_MINUTES)
        // 1 000 000 分钟 = 694 天 10 小时 40 分钟（拆分显示）
        assertEquals("694 天 10 小时 40 分钟", formatOffsetMinutes(MAX_CUSTOM_OFFSET_MINUTES))
    }

    // ---------- 连续添加的初值建议 ----------

    @Test
    fun `空策略建议 1 小时`() {
        assertEquals(60, suggestedOffsetMinutes(emptyList(), ReminderAnchor.DUE))
    }

    @Test
    fun `建议值取同锚点最大值加 1 小时`() {
        val existing = listOf(
            ReminderPoint(ReminderAnchor.DUE, 10080),
            ReminderPoint(ReminderAnchor.DUE, 1440),
            ReminderPoint(ReminderAnchor.START, 30),
        )
        assertEquals(10080 + 60, suggestedOffsetMinutes(existing, ReminderAnchor.DUE))
        assertEquals(30 + 60, suggestedOffsetMinutes(existing, ReminderAnchor.START))
    }

    @Test
    fun `建议值不超过上限`() {
        val existing = listOf(ReminderPoint(ReminderAnchor.DUE, MAX_CUSTOM_OFFSET_MINUTES))
        assertEquals(MAX_CUSTOM_OFFSET_MINUTES, suggestedOffsetMinutes(existing, ReminderAnchor.DUE))
    }
}
