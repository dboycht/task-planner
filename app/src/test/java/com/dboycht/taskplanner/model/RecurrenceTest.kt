package com.dboycht.taskplanner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RecurrenceTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    // ---------- 编解码 ----------

    @Test
    fun `decode 空与垃圾都退回不重复`() {
        for (raw in listOf(null, "", " ", "X", "C", "C1", "Cx", "C999", "daily")) {
            val rule = RecurrenceRule.decode(raw)
            assertFalse("raw=$raw 不应算重复", rule.isRecurring)
            assertEquals(RecurKind.NONE, rule.kind)
        }
    }

    @Test
    fun `decode D W M 与 C区间合法值`() {
        assertEquals(RecurKind.DAILY, RecurrenceRule.decode("D").kind)
        assertEquals(RecurKind.WEEKLY, RecurrenceRule.decode("W").kind)
        assertEquals(RecurKind.MONTHLY, RecurrenceRule.decode("M").kind)
        val custom = RecurrenceRule.decode("C7")
        assertEquals(RecurKind.CUSTOM_DAYS, custom.kind)
        assertEquals(7, custom.intervalDays)
        assertEquals(RecurKind.CUSTOM_DAYS, RecurrenceRule.decode("C2").kind)
        assertEquals(RecurKind.CUSTOM_DAYS, RecurrenceRule.decode("C366").kind)
    }

    @Test
    fun `encode decode 往返一致`() {
        for (rule in listOf(
            RecurrenceRule.NONE,
            RecurrenceRule(RecurKind.DAILY),
            RecurrenceRule(RecurKind.WEEKLY),
            RecurrenceRule(RecurKind.MONTHLY),
            RecurrenceRule(RecurKind.CUSTOM_DAYS, 21),
        )) {
            assertEquals(rule, RecurrenceRule.decode(rule.encode()))
        }
        assertEquals("", RecurrenceRule.NONE.encode())
    }

    @Test
    fun `label 文案`() {
        assertEquals("不重复", RecurrenceRule.NONE.label())
        assertEquals("每天", RecurrenceRule(RecurKind.DAILY).label())
        assertEquals("每周", RecurrenceRule(RecurKind.WEEKLY).label())
        assertEquals("每月", RecurrenceRule(RecurKind.MONTHLY).label())
        assertEquals("每 14 天", RecurrenceRule(RecurKind.CUSTOM_DAYS, 14).label())
    }

    // ---------- 下一次发生 ----------

    @Test
    fun `不重复与非法时刻返回 null`() {
        assertNull(nextOccurrence(RecurrenceRule.NONE, at(2026, 11, 3, 20, 0), zone))
        assertNull(nextOccurrence(RecurrenceRule(RecurKind.DAILY), 0L, zone))
        assertNull(nextOccurrence(RecurrenceRule(RecurKind.DAILY), -5L, zone))
    }

    @Test
    fun `每天 加一天且保墙钟`() {
        val next = nextOccurrence(RecurrenceRule(RecurKind.DAILY), at(2026, 11, 3, 20, 30), zone)!!
        assertEquals(at(2026, 11, 4, 20, 30), next)
    }

    @Test
    fun `每周 加七天 跨月不偏移`() {
        val next = nextOccurrence(RecurrenceRule(RecurKind.WEEKLY), at(2026, 10, 27, 8, 0), zone)!!
        assertEquals(at(2026, 11, 3, 8, 0), next)
    }

    @Test
    fun `自定义 N 天`() {
        val next = nextOccurrence(RecurrenceRule(RecurKind.CUSTOM_DAYS, 14), at(2026, 11, 3, 9, 0), zone)!!
        assertEquals(at(2026, 11, 17, 9, 0), next)
    }

    @Test
    fun `每月 31号在2月收窄为28 2026非闰年`() {
        val next = nextOccurrence(RecurrenceRule(RecurKind.MONTHLY), at(2026, 1, 31, 20, 0), zone)!!
        assertEquals(at(2026, 2, 28, 20, 0), next)
    }

    @Test
    fun `每月 年内正常顺延 月末收窄后按顺延结果继续（已知漂移）`() {
        // 1月31 → 2月28 → 3月28（v1 已知限制：日号跟随上一次收窄结果，不追回 31）。
        val feb = nextOccurrence(RecurrenceRule(RecurKind.MONTHLY), at(2026, 1, 31, 20, 0), zone)!!
        assertEquals(at(2026, 2, 28, 20, 0), feb)
        val mar = nextOccurrence(RecurrenceRule(RecurKind.MONTHLY), feb, zone)!!
        assertEquals(at(2026, 3, 28, 20, 0), mar)
    }

    @Test
    fun `每月 跨年顺延`() {
        val next = nextOccurrence(RecurrenceRule(RecurKind.MONTHLY), at(2026, 12, 15, 10, 0), zone)!!
        assertEquals(at(2027, 1, 15, 10, 0), next)
    }

    // ---------- 校验联动 ----------

    @Test
    fun `重复任务必须有截止时间`() {
        assertTrue(
            TaskValidator.validate("标题", null, null, RecurrenceRule(RecurKind.WEEKLY)) != null
        )
        assertTrue(
            TaskValidator.validate("标题", null, at(2026, 11, 4, 20, 0), RecurrenceRule(RecurKind.WEEKLY)) == null
        )
        // 不重复任务无截止仍然合法（未定时段存在）。
        assertTrue(TaskValidator.validate("标题", null, null) == null)
    }
}
