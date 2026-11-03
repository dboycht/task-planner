package com.dboycht.taskplanner.model

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 桌面小组件数据模型（纯函数，JVM 可单测；不依赖 Compose/Android）。
 * 内容 = 已逾期 + 今日 + 近期 的分组列表（逾期最该被看见，一并放进来），
 * 每行带优先级点色（与 App 内蓝→红渐变同语义）。
 */
object WidgetModel {

    /** 最多渲染的任务行数（不含分组头），防超长列表拖慢 widget。 */
    const val MAX_ROWS = 12

    sealed interface Row {
        data class Header(val label: String) : Row
        data class Item(
            val taskId: Long,
            val title: String,
            val timeText: String?,
            val dotColor: Int,
        ) : Row
    }

    private val timeFmt = DateTimeFormatter.ofPattern("MM-dd HH:mm")

    fun buildRows(tasks: List<Task>, now: Long, zone: ZoneId): List<Row> {
        val grouped = TaskGrouper.groupAndSort(tasks, now, zone)
        val rows = mutableListOf<Row>()
        var items = 0
        for (group in listOf(TaskGroup.OVERDUE, TaskGroup.TODAY, TaskGroup.UPCOMING)) {
            val list = grouped[group].orEmpty()
            if (list.isEmpty()) continue
            rows += Row.Header("${group.label} · ${list.size}")
            for (task in list) {
                if (items >= MAX_ROWS) break
                rows += Row.Item(
                    taskId = task.id,
                    title = task.title,
                    timeText = timeTextOf(task, zone),
                    dotColor = dotColor(task.priority),
                )
                items++
            }
        }
        return rows
    }

    private fun timeTextOf(task: Task, zone: ZoneId): String? {
        val t = task.dueTime ?: task.startTime ?: return null
        return timeFmt.format(Instant.ofEpochMilli(t).atZone(zone))
    }

    /** 优先级点色：0 → 蓝 #3B82F6，100 → 红 #EF4444，中间线性插值（与 App 渐变同语义）。 */
    fun dotColor(priority: Int): Int {
        val p = clampPriority(priority)
        val from = 0x3B82F6
        val to = 0xEF4444
        fun ch(v: Int, shift: Int) = ((v shr shift) and 0xFF)
        fun lerp(shift: Int) = ch(from, shift) + (ch(to, shift) - ch(from, shift)) * p / 100
        val r = lerp(16); val g = lerp(8); val b = lerp(0)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
