package com.dboycht.taskplanner.model

/**
 * 任务列表过滤（纯函数，JVM 可单测）。作用于分组**之前**：
 * 先过滤再走 TaskGrouper，无任务的组自然消失。
 *
 * - [query]：标题 / 描述 / 标签的子串匹配（大小写不敏感），空串 = 不过滤。
 * - [status]：null = 全部状态。
 * - [priorityBucket]：0=低 / 1=中 / 2=高（按 [priorityBucketOf] 分档）；null = 全部。
 * - [tags]：命中任一标签即通过（OR）。
 */
data class TaskFilter(
    val query: String = "",
    val status: TaskStatus? = null,
    val priorityBucket: Int? = null,
    val tags: Set<String> = emptySet(),
) {

    val isActive: Boolean
        get() = query.isNotBlank() || status != null || priorityBucket != null || tags.isNotEmpty()

    fun matches(task: Task): Boolean {
        val q = query.trim()
        if (q.isNotEmpty()) {
            val hit = task.title.contains(q, ignoreCase = true) ||
                task.description.contains(q, ignoreCase = true) ||
                task.tags.any { it.contains(q, ignoreCase = true) }
            if (!hit) return false
        }
        if (status != null && task.status != status) return false
        if (priorityBucket != null && priorityBucketOf(task.priority) != priorityBucket) return false
        if (tags.isNotEmpty() && task.tags.none { it in tags }) return false
        return true
    }

    companion object {
        /** 优先级三档：与 UI 的 [priorityLabel] 同界（<34 低 / <67 中 / 其余高）。 */
        fun priorityBucketOf(priority: Int): Int = when {
            priority < 34 -> 0
            priority < 67 -> 1
            else -> 2
        }

        fun apply(tasks: List<Task>, filter: TaskFilter): List<Task> =
            if (!filter.isActive) tasks else tasks.filter { filter.matches(it) }
    }
}
