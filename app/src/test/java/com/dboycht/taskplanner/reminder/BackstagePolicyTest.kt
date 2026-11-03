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

    @Test
    fun `没有已到点的提醒时不提示`() {
        val now = 100 * minute
        val snapshot = ReminderHealthSnapshot(
            dueTriggerTimes = listOf(200 * minute), // 全在未来
            lastDeliveredAt = null,
            now = now,
        )
        assertNull(suspectMissedReminder(snapshot))
    }

    @Test
    fun `空任务列表与从未响过都不提示`() {
        assertNull(
            suspectMissedReminder(
                ReminderHealthSnapshot(dueTriggerTimes = emptyList(), lastDeliveredAt = null, now = 100 * minute)
            )
        )
    }

    @Test
    fun `刚过点还在宽限期内不提示`() {
        val due = 100 * minute
        val snapshot = ReminderHealthSnapshot(
            dueTriggerTimes = listOf(due),
            lastDeliveredAt = null,
            now = due + 5 * minute, // 只过了 5 分钟
        )
        assertNull(suspectMissedReminder(snapshot, graceMs = REMINDER_GRACE_MS))
    }

    @Test
    fun `过了宽限期仍没响过则提示`() {
        val due = 100 * minute
        val snapshot = ReminderHealthSnapshot(
            dueTriggerTimes = listOf(due),
            lastDeliveredAt = null,
            now = due + 11 * minute,
        )
        assertEquals(due, suspectMissedReminder(snapshot))
    }

    @Test
    fun `这次到点之后响过就不提示`() {
        val due = 100 * minute
        val snapshot = ReminderHealthSnapshot(
            dueTriggerTimes = listOf(due),
            lastDeliveredAt = due + 30_000L, // 到点后 30 秒发出
            now = due + 11 * minute,
        )
        assertNull(suspectMissedReminder(snapshot))
    }

    @Test
    fun `只响过比这次更早的提醒不算数`() {
        val oldDue = 50 * minute
        val newDue = 100 * minute
        val snapshot = ReminderHealthSnapshot(
            dueTriggerTimes = listOf(oldDue, newDue),
            lastDeliveredAt = oldDue + 30_000L, // 上一次响了，这一次没响
            now = newDue + 11 * minute,
        )
        assertEquals(newDue, suspectMissedReminder(snapshot))
    }

    @Test
    fun `只看最近一个已到点的提醒，更早的不算系统问题`() {
        val ancientDue = 10 * minute // 很久以前到点、当时可能还没装应用
        val latestDue = 100 * minute
        val snapshot = ReminderHealthSnapshot(
            dueTriggerTimes = listOf(latestDue, ancientDue), // 乱序输入也要正确
            lastDeliveredAt = latestDue + 1_000L,
            now = latestDue + 11 * minute,
        )
        assertNull(suspectMissedReminder(snapshot))
    }

    @Test
    fun `未来提醒混在列表里不影响判定`() {
        val due = 100 * minute
        val snapshot = ReminderHealthSnapshot(
            dueTriggerTimes = listOf(due, 500 * minute, 900 * minute),
            lastDeliveredAt = null,
            now = due + 11 * minute,
        )
        assertEquals(due, suspectMissedReminder(snapshot))
    }

    @Test
    fun `宽限期可注入，边界取等`() {
        val due = 100 * minute
        val snapshot = ReminderHealthSnapshot(
            dueTriggerTimes = listOf(due),
            lastDeliveredAt = null,
            now = due + 10 * minute, // 恰好等于宽限
        )
        // 恰好等于宽限期 → 已构成「过点 10 分钟没响」，提示
        assertEquals(due, suspectMissedReminder(snapshot, graceMs = 10 * minute))
        // 少 1 毫秒 → 还不算
        assertNull(
            suspectMissedReminder(
                ReminderHealthSnapshot(listOf(due), null, due + 10 * minute - 1),
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
