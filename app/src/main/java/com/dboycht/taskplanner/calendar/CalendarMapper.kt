package com.dboycht.taskplanner.calendar

import com.dboycht.taskplanner.model.Task
import com.dboycht.taskplanner.model.TaskStatus
import com.dboycht.taskplanner.model.priorityLabel

/**
 * 任务 → 日历事件字段的纯映射（JVM 可单测）。
 * Room 是单一事实源，日历事件是投影；描述里带任务 id 以便反查（§2.2）。
 */
object CalendarMapper {

    /** 无开始时间的任务：事件从「截止前 1 小时」起，到截止结束。 */
    const val DEFAULT_DURATION_MS: Long = 60L * 60_000L

    /** 多数日历提供方每事件提醒数上限。 */
    const val MAX_REMINDERS_PER_EVENT = 5

    /** 事件标题：已完成加 ✓ 前缀（§2.2 v1 约定，完成不删事件）；优先级以 [高]/[中]/[低] 前缀标注。 */
    fun eventTitle(task: Task): String {
        val check = if (task.status == TaskStatus.DONE) "✓ " else ""
        return "$check[${priorityLabel(task.priority)}] ${task.title}"
    }

    /**
     * 事件起止（epoch 毫秒）。无截止 ⇒ 无法成事件，返回 null（任务保持纯本地）。
     * 防御：起止倒挂（截止 < 开始）时事件收窄为开始时刻本身。
     */
    fun eventRange(task: Task): Pair<Long, Long>? {
        val due = task.dueTime ?: return null
        val start = task.startTime ?: (due - DEFAULT_DURATION_MS)
        return if (due < start) start to start else start to due
    }

    /** 描述 = 任务描述 + 同步来源标记（含任务 id，便于在日历 App 里反查）。 */
    fun eventDescription(task: Task): String {
        val marker = "由任务计划同步 · 任务 ID ${task.id}"
        return if (task.description.isBlank()) marker else "${task.description}\n—— $marker"
    }

    /**
     * 日历提醒（「事件开始前 N 分钟」）列表：由任务提醒策略换算而来。
     * 日历模型只支持「开始前」，落在事件开始之后的提醒点无法表达，跳过；
     * 去重后取最早的 [maxCount] 个。0 = 到点提醒（合法值）。
     */
    fun reminderMinutesBefore(task: Task, maxCount: Int = MAX_REMINDERS_PER_EVENT): List<Int> {
        val range = eventRange(task) ?: return emptyList()
        val eventStart = range.first
        return task.reminderPolicy.triggersFor(task.startTime, task.dueTime)
            .map { trigger -> (eventStart - trigger) / 60_000L }
            .filter { it >= 0L }
            .map { it.toInt() }
            .toSortedSet()
            .take(maxCount)
    }
}
