package com.dboycht.taskplanner.model

import java.time.ZoneId

/**
 * 重复任务「滚动预生成」计划器（纯函数，JVM 可单测）。
 *
 * 语义（用户选定）：**永远保证未来至少有一个未完成实例**（跨度 90 天封顶）——
 * 即使上一条忘了点完成，下一次任务也已经在列表和日历里。已错过的发生不批量补史，
 * 直接跳过，只物化第一个未来的实例；每个组每轮至多计划插入 1 条。
 *
 * 种子 = 组内 dueTime 最大的实例（并列取 id 大者）。种子无截止 ⇒ 规则无法推算，视为停摆。
 */
object RecurrenceGenerator {

    /** 预生成跨度：下一次发生超过 now + 90 天就不物化。 */
    const val HORIZON_DAYS = 90L

    /** 单组跳过已错过发生的最大步数（防极端数据死循环；400 步 = 日任务 13 个月）。 */
    const val MAX_SKIP_STEPS = 400

    /** 计划插入的新实例：[source] 的拷贝模板 + 新的发生时刻。 */
    data class PlannedInstance(val source: Task, val dueTime: Long, val startTime: Long?)

    fun planNextInstances(
        tasks: List<Task>,
        now: Long,
        zone: ZoneId,
    ): List<PlannedInstance> {
        val horizonEnd = now + HORIZON_DAYS * 86_400_000L
        val groups = tasks
            .filter { it.recurRule.isRecurring }
            .groupBy { it.recurGroup ?: "" }
        val plan = mutableListOf<PlannedInstance>()
        for ((_, instances) in groups) {
            if (instances.isEmpty()) continue
            planNextForGroup(instances, now, zone, horizonEnd)?.let { plan += it }
        }
        return plan
    }

    private fun planNextForGroup(
        instances: List<Task>,
        now: Long,
        zone: ZoneId,
        horizonEnd: Long,
    ): PlannedInstance? {
        // 保底已满足（存在未来的未完成实例）⇒ 不再多生成；日历里每个系列只保持一条未来事件。
        if (instances.any { it.status != TaskStatus.DONE && (it.dueTime ?: Long.MIN_VALUE) > now }) {
            return null
        }
        // 种子：dueTime 最大（null 当最小），并列取 id 大——规则/内容以最新一条为准。
        val seed = instances.maxWith(
            compareBy<Task> { it.dueTime ?: Long.MIN_VALUE }.thenBy { it.id }
        )
        val seedDue = seed.dueTime ?: return null

        // 跳过已错过的发生：推进到第一个 > now 的时刻（不落库，虚拟跳过）。
        var cursor = seedDue
        var steps = 0
        var next = nextOccurrence(seed.recurRule, cursor, zone)
        while (next != null && next <= now && steps < MAX_SKIP_STEPS) {
            cursor = next
            next = nextOccurrence(seed.recurRule, cursor, zone)
            steps++
        }
        // 步数耗尽时 next 可能仍落在过去（极端旧种子）⇒ 宁可不生成也不物化过去。
        if (next == null || next <= now || next > horizonEnd) return null
        // 防御并发双写：同刻实例已存在则跳过（正常流程 next 恒大于种子 due，不会命中）。
        if (instances.any { it.dueTime == next }) return null

        val shift = next - seedDue
        return PlannedInstance(
            source = seed,
            dueTime = next,
            startTime = seed.startTime?.plus(shift),
        )
    }
}
