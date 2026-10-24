package com.dboycht.taskplanner.model

/**
 * 提醒预设：命名的提醒策略集合。任务选择预设时**拷贝当时的策略值**存入任务，
 * 之后修改/删除预设不影响已创建的任务（策略即值，非引用）。
 */
data class ReminderPreset(
    val id: Long = 0L,
    val name: String,
    val policy: ReminderPolicy,
    val isBuiltIn: Boolean = false,
    val sortOrder: Int = 0,
)

/** 首次启动 seed 进数据库的内置预设（isBuiltIn=true，不可编辑/删除）。 */
val BUILT_IN_PRESETS: List<ReminderPreset> = listOf(
    ReminderPreset(
        name = "截止前 1 小时",
        policy = ReminderPolicy(listOf(ReminderPoint(ReminderAnchor.DUE, 60))),
        isBuiltIn = true,
        sortOrder = 0,
    ),
    ReminderPreset(
        name = "截止前 1 天 + 1 小时",
        policy = ReminderPolicy(
            listOf(ReminderPoint(ReminderAnchor.DUE, 1440), ReminderPoint(ReminderAnchor.DUE, 60))
        ),
        isBuiltIn = true,
        sortOrder = 1,
    ),
    ReminderPreset(
        name = "7 天 / 3 天 / 1 小时",
        policy = ReminderPolicy(
            listOf(
                ReminderPoint(ReminderAnchor.DUE, 10080),
                ReminderPoint(ReminderAnchor.DUE, 4320),
                ReminderPoint(ReminderAnchor.DUE, 60),
            )
        ),
        isBuiltIn = true,
        sortOrder = 2,
    ),
    ReminderPreset(
        name = "开始时 + 截止前 30 分钟",
        policy = ReminderPolicy(
            listOf(ReminderPoint(ReminderAnchor.START, 0), ReminderPoint(ReminderAnchor.DUE, 30))
        ),
        isBuiltIn = true,
        sortOrder = 3,
    ),
)

/**
 * 提前量的展示文案：能被天/小时整除的用大单位（1440→“1 天”、60→“1 小时”），
 * 其余保留分钟（90→“90 分钟”）。0 分钟由调用方特判为“到点提醒”。
 */
fun formatOffsetMinutes(minutes: Int): String = when {
    minutes % 1440 == 0 -> "${minutes / 1440} 天"
    minutes % 60 == 0 -> "${minutes / 60} 小时"
    else -> "$minutes 分钟"
}

/** 策略的人话摘要（空策略 =「不提醒」）。列表、编辑页、预设页共用。 */
fun ReminderPolicy.summary(): String {
    if (points.isEmpty()) return "不提醒"
    return points.joinToString("、") { p ->
        val offset = formatOffsetMinutes(p.offsetMinutes)
        when (p.anchor) {
            ReminderAnchor.START ->
                if (p.offsetMinutes == 0) "开始时" else "开始前 $offset"
            ReminderAnchor.DUE ->
                if (p.offsetMinutes == 0) "截止时" else "截止前 $offset"
        }
    }
}

/**
 * 预设可用性判定：策略里每个提醒点依赖的锚点时间都必须已设置
 * （含截止提醒 → 需有截止时间；含开始提醒 → 需有开始时间）。空策略恒可用。
 */
fun ReminderPolicy.isUsableFor(startTime: Long?, dueTime: Long?): Boolean =
    points.all { p ->
        when (p.anchor) {
            ReminderAnchor.START -> startTime != null
            ReminderAnchor.DUE -> dueTime != null
        }
    }

/** 不可用时的原因文案（可用于 UI 灰化提示）；可用返回 null。 */
fun ReminderPolicy.missingAnchorLabel(startTime: Long?, dueTime: Long?): String? {
    val needStart = points.any { it.anchor == ReminderAnchor.START } && startTime == null
    val needDue = points.any { it.anchor == ReminderAnchor.DUE } && dueTime == null
    return when {
        needStart && needDue -> "需先设置开始与截止时间"
        needStart -> "需先设置开始时间"
        needDue -> "需先设置截止时间"
        else -> null
    }
}
