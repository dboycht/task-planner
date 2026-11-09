package com.dboycht.taskplanner.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「厂商耗电策略压低提醒」的判定与引导。
 *
 * 真机背景（ERROR.md E5）：ColorOS 把应用设成「智能优化后台运行」时，系统给精确闹钟加
 * `adjustment=+3d`，当天/次日的提醒永远不响，且**没有任何权限位或系统 API 能读到这个状态**。
 * 因此这里的判据只能建立在「闹钟到底有没有真的响过」之上。
 */
class BackstagePolicyTest {

    private val minute = 60_000L

    // ---------- 厂商识别 ----------

    @Test
    fun `识别 OPPO 系机型（大小写不敏感）`() {
        assertTrue(isVendorManagedRom("OPPO", "OPPO"))
        assertTrue(isVendorManagedRom("oplus", "OPPO"))
        assertTrue(isVendorManagedRom("OnePlus", "OnePlus"))
        assertTrue(isVendorManagedRom("realme", "realme"))
        // 品牌和厂商只要命中一个就够（有的机型 BRAND 被改成定制名）
        assertTrue(isVendorManagedRom("custom", "OPPO"))
    }

    @Test
    fun `其他机型与空值不提示`() {
        assertFalse(isVendorManagedRom("google", "Google"))
        assertFalse(isVendorManagedRom("Xiaomi", "Xiaomi"))
        assertFalse(isVendorManagedRom(null, null))
        assertFalse(isVendorManagedRom("", ""))
        // 不做子串误伤：不含关键词的普通品牌一律放过
        assertFalse(isVendorManagedRom("samsung", "samsung"))
    }

    // ---------- 是否怀疑提醒被压制 ----------

    /**
     * 判定只追究「我们确实排过、且已到点」的那条闹钟（snapshot.expectedTriggerAt）。
     * 任务上的历史提醒点不算数 —— 调度层每条任务只排下一个未到点的提醒。
     */

    @Test
    fun `没排过闹钟时不提示（新装或清数据）`() {
        assertNull(
            suspectMissedReminder(
                ReminderHealthSnapshot(
                    dueTriggerTimes = listOf(200 * minute),
                    lastDeliveredAt = null,
                    now = 300 * minute,
                    expectedTriggerAt = null,
                )
            )
        )
    }

    @Test
    fun `排过的闹钟还没到点时不提示`() {
        val now = 100 * minute
        assertNull(
            suspectMissedReminder(
                ReminderHealthSnapshot(
                    dueTriggerTimes = listOf(500 * minute),
                    lastDeliveredAt = null,
                    now = now,
                    expectedTriggerAt = 200 * minute, // 未来
                )
            )
        )
    }

    @Test
    fun `刚过点还在宽限期内不提示`() {
        val expected = 100 * minute
        val snapshot = ReminderHealthSnapshot(
            dueTriggerTimes = listOf(expected),
            lastDeliveredAt = null,
            now = expected + 5 * minute, // 只过了 5 分钟
            expectedTriggerAt = expected,
        )
        assertNull(suspectMissedReminder(snapshot, graceMs = REMINDER_GRACE_MS))
    }

    @Test
    fun `过了宽限期仍没响过则提示`() {
        val expected = 100 * minute
        val snapshot = ReminderHealthSnapshot(
            dueTriggerTimes = listOf(expected),
            lastDeliveredAt = null,
            now = expected + 11 * minute,
            expectedTriggerAt = expected,
        )
        assertEquals(expected, suspectMissedReminder(snapshot))
    }

    @Test
    fun `这次到点之后响过就不提示`() {
        val expected = 100 * minute
        val snapshot = ReminderHealthSnapshot(
            dueTriggerTimes = listOf(expected),
            lastDeliveredAt = expected + 30_000L, // 到点后 30 秒发出
            now = expected + 11 * minute,
            expectedTriggerAt = expected,
        )
        assertNull(suspectMissedReminder(snapshot))
    }

    @Test
    fun `只响过比这次更早的提醒不算数`() {
        val oldExpected = 50 * minute
        val newExpected = 100 * minute
        val snapshot = ReminderHealthSnapshot(
            dueTriggerTimes = listOf(oldExpected, newExpected),
            lastDeliveredAt = oldExpected + 30_000L, // 上一次响了，这一次没响
            now = newExpected + 11 * minute,
            expectedTriggerAt = newExpected,
        )
        assertEquals(newExpected, suspectMissedReminder(snapshot))
    }

    // ---------- 本轮修复的误报：陈旧未完成任务的过期提醒点 ----------

    @Test
    fun `陈旧未完成任务的过期提醒点不再触发横幅（本次修复的缺陷）`() {
        // 真机场景：一条 11-06 就过期、一直没勾完成的任务，提醒点早已过去；
        // 但调度层从未为它排过闹钟（只排下一个未到点的提醒）。
        // 旧判据把它当成「最近一次到点却没响」⇒ 横幅永久误报。
        val stalePastTrigger = 100 * minute
        val snapshot = ReminderHealthSnapshot(
            dueTriggerTimes = listOf(stalePastTrigger),
            lastDeliveredAt = null,
            now = 300 * minute,
            expectedTriggerAt = null, // 从没排过 ⇒ 不该追究
        )
        assertNull(suspectMissedReminder(snapshot))
    }

    @Test
    fun `排过的闹钟太久以前也不提示（无从取证）`() {
        val expected = 100 * minute
        val snapshot = ReminderHealthSnapshot(
            dueTriggerTimes = listOf(expected),
            lastDeliveredAt = null,
            now = expected + REMINDER_LOOKBACK_MS + 1, // 超出回看窗口
            expectedTriggerAt = expected,
        )
        assertNull(suspectMissedReminder(snapshot))
    }

    @Test
    fun `回看窗口边界内仍然提示`() {
        val expected = 100 * minute
        val snapshot = ReminderHealthSnapshot(
            dueTriggerTimes = listOf(expected),
            lastDeliveredAt = null,
            now = expected + REMINDER_LOOKBACK_MS, // 恰好等于窗口
            expectedTriggerAt = expected,
        )
        assertEquals(expected, suspectMissedReminder(snapshot))
    }

    @Test
    fun `未来提醒混在列表里不影响判定`() {
        val expected = 100 * minute
        val snapshot = ReminderHealthSnapshot(
            dueTriggerTimes = listOf(expected, 500 * minute, 900 * minute),
            lastDeliveredAt = null,
            now = expected + 11 * minute,
            expectedTriggerAt = expected,
        )
        assertEquals(expected, suspectMissedReminder(snapshot))
    }

    @Test
    fun `宽限期可注入，边界取等`() {
        val expected = 100 * minute
        // 恰好等于宽限期 → 已构成「过点 10 分钟没响」，提示
        assertEquals(
            expected,
            suspectMissedReminder(
                ReminderHealthSnapshot(listOf(expected), null, expected + 10 * minute, expected),
                graceMs = 10 * minute,
            ),
        )
        // 少 1 毫秒 → 还不算
        assertNull(
            suspectMissedReminder(
                ReminderHealthSnapshot(listOf(expected), null, expected + 10 * minute - 1, expected),
                graceMs = 10 * minute,
            )
        )
    }

    // ---------- 文案 ----------

    @Test
    fun `引导文案必须同时提到后台与自启动，否则用户改不干净`() {
        val body = backstageGuidanceBody()
        assertTrue(body.contains("后台"))
        assertTrue(body.contains("自启动"))
        assertTrue(body.contains("完全允许"))
        assertFalse(backstageGuidanceTitle().isBlank())
        assertEquals("耗电管理", backstageSettingEntryLabel())
    }
}
