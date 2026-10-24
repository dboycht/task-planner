package com.dboycht.taskplanner.model

import java.time.Instant
import java.time.ZoneId

/** 任务列表分组。声明顺序即展示顺序。 */
enum class TaskGroup(val label: String) {
    OVERDUE("已逾期"),
    TODAY("今日"),
    UPCOMING("近期"),
    NO_DATE("无日期"),
    DONE("已完成项目");
}

/** 纯函数分组/排序（JVM 可单测）。 */
object TaskGrouper {

    /**
     * 分组规则（按顺序判定）：
     * 1. 已完成 → DONE；
     * 2. 截止已过（due < now，边界相等不算逾期）→ OVERDUE；
     * 3. 截止或开始落在今天，或「已开始但无截止」→ TODAY；
     * 4. 有任一时间 → UPCOMING；
     * 5. 都没有 → NO_DATE。
     */
    fun groupOf(task: Task, now: Long, zone: ZoneId): TaskGroup {
        if (task.status == TaskStatus.DONE) return TaskGroup.DONE
        val due = task.dueTime
        val start = task.startTime
        if (due != null && due < now) return TaskGroup.OVERDUE
        val dueToday = due != null && isSameDay(due, now, zone)
        val startToday = start != null && isSameDay(start, now, zone)
        val startedNoDue = start != null && due == null && start <= now
        if (dueToday || startToday || startedNoDue) return TaskGroup.TODAY
        return if (due != null || start != null) TaskGroup.UPCOMING else TaskGroup.NO_DATE
    }

    /** 分组 + 组内排序；键顺序 = [TaskGroup] 声明顺序，只含有任务的组。 */
    fun groupAndSort(tasks: List<Task>, now: Long, zone: ZoneId): Map<TaskGroup, List<Task>> {
        val grouped = tasks.groupBy { groupOf(it, now, zone) }
        val result = LinkedHashMap<TaskGroup, List<Task>>()
        for (g in TaskGroup.entries) {
            grouped[g]?.let { result[g] = sortInGroup(g, it) }
        }
        return result
    }

    private fun sortInGroup(group: TaskGroup, list: List<Task>): List<Task> = when (group) {
        // 完成的：最近完成的在前。
        TaskGroup.DONE -> list.sortedWith(
            compareByDescending<Task> { it.completedAt ?: it.updatedAt }
        )
        // 逾期的：最紧急（最早到期）的在前；同级优先级数值大者在前。
        TaskGroup.OVERDUE -> list.sortedWith(
            compareBy<Task> { it.dueTime ?: Long.MAX_VALUE }
                .thenByDescending { it.priority }
                .thenBy { it.title }
        )
        // 其余组：先到期的在前（无截止看开始），优先级数值大者在前。
        else -> list.sortedWith(
            compareBy<Task> { it.dueTime ?: it.startTime ?: Long.MAX_VALUE }
                .thenByDescending { it.priority }
                .thenBy { it.title }
        )
    }

    private fun isSameDay(a: Long, b: Long, zone: ZoneId): Boolean =
        Instant.ofEpochMilli(a).atZone(zone).toLocalDate() ==
            Instant.ofEpochMilli(b).atZone(zone).toLocalDate()
}
