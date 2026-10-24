package com.dboycht.taskplanner.model

/** 优先级。ordinal 越小越紧急。 */
enum class Priority(val label: String) {
    HIGH("高"),
    MEDIUM("中"),
    LOW("低");
}

/** 任务状态。 */
enum class TaskStatus(val label: String) {
    TODO("待办"),
    DOING("进行中"),
    DONE("完成");
}

/** 提醒锚点：相对开始时间或截止时间。 */
enum class ReminderAnchor(val code: Char) {
    START('S'),
    DUE('D');
}

/**
 * 一个提醒点：锚点时刻提前 [offsetMinutes] 分钟触发。
 * [offsetMinutes] 恒为非负；0 表示到点提醒。
 */
data class ReminderPoint(val anchor: ReminderAnchor, val offsetMinutes: Int)

/**
 * 提醒策略 = 提醒点集合。默认策略：截止前 60 分钟。
 * 编码格式 "D:60,S:0"（锚点字母:提前分钟数），空策略编码为空串。
 */
data class ReminderPolicy(val points: List<ReminderPoint>) {

    fun encode(): String =
        points.joinToString(",") { "${it.anchor.code}:${it.offsetMinutes}" }

    /**
     * 按 [startTime] / [dueTime]（epoch 毫秒）算出各提醒点的实际触发时刻；
     * 锚点时间为空的点跳过。返回值不过滤过去时刻（调度层负责，见 M3）。
     */
    fun triggersFor(startTime: Long?, dueTime: Long?): List<Long> {
        val out = mutableListOf<Long>()
        for (p in points) {
            val anchor = when (p.anchor) {
                ReminderAnchor.START -> startTime
                ReminderAnchor.DUE -> dueTime
            } ?: continue
            out += anchor - p.offsetMinutes * 60_000L
        }
        return out
    }

    companion object {
        val DEFAULT = ReminderPolicy(listOf(ReminderPoint(ReminderAnchor.DUE, 60)))

        /** 宽容解码：非法 token 忽略，整体为空返回空策略。 */
        fun decode(raw: String?): ReminderPolicy {
            if (raw.isNullOrBlank()) return ReminderPolicy(emptyList())
            val points = raw.split(',').mapNotNull { token ->
                val anchor = ReminderAnchor.entries.firstOrNull { it.code == token.firstOrNull() }
                val offset = token.drop(2).toIntOrNull()
                val wellFormed = token.getOrNull(1) == ':' && anchor != null &&
                    offset != null && offset >= 0
                if (wellFormed) ReminderPoint(anchor!!, offset!!) else null
            }
            return ReminderPolicy(points)
        }
    }
}

/** 领域任务（与 Room 实体一一映射；时间一律 epoch 毫秒）。 */
data class Task(
    val id: Long = 0L,
    val title: String,
    val description: String = "",
    val startTime: Long? = null,
    val dueTime: Long? = null,
    val priority: Priority = Priority.MEDIUM,
    val status: TaskStatus = TaskStatus.TODO,
    val tags: List<String> = emptyList(),
    val reminderPolicy: ReminderPolicy = ReminderPolicy.DEFAULT,
    val calendarEventId: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val completedAt: Long? = null,
)

/** 保存前校验。返回可直接展示的中文错误信息；合法返回 null。 */
object TaskValidator {
    fun validate(title: String, startTime: Long?, dueTime: Long?): String? {
        if (title.isBlank()) return "标题不能为空"
        if (startTime != null && dueTime != null && dueTime < startTime) {
            return "截止时间不能早于开始时间"
        }
        return null
    }
}
