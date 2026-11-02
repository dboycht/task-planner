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
 * 提前量的展示文案，按大单位优先**拆分**：1440→“1 天”、60→“1 小时”、90→“1 小时 30 分钟”、
 * 1590→“1 天 2 小时 30 分钟”。整小时/整天/整分钟沿用单段写法（老文案不变）。
 * 注：早期版本对 90 输出“90 分钟”，2026-11-02 起改为拆分显示，便于核对自定义组合输入。
 */
fun formatOffsetMinutes(minutes: Int): String {
    if (minutes <= 0) return "0 分钟"
    val d = minutes / 1440
    val h = minutes % 1440 / 60
    val m = minutes % 60
    val parts = buildList {
        if (d > 0) add("$d 天")
        if (h > 0) add("$h 小时")
        if (m > 0) add("$m 分钟")
    }
    return parts.joinToString(" ")
}

/**
 * 自定义提前量输入上限。单点上限约 3 年，远超任何日历提醒的实际用途；
 * 上限存在的意义是**挡住 Int 溢出**（天×1440 + 小时×60 + 分钟 一旦溢出成负数，
 * 提醒会被调度层当成「过去的点」静默丢掉）。
 */
const val MAX_CUSTOM_OFFSET_MINUTES: Int = 1_000_000

/**
 * 自定义提前量输入（天 / 小时 / 分钟三个输入框的原始文本）。
 * 只认纯数字串：空串、`abc`、`-5`、`1.5` 一律按 0 计（UI 已过滤，纯函数仍需自防）。
 */
data class CustomOffsetInput(val days: String, val hours: String, val minutes: String) {

    /** 三段各自的整数值；非纯数字一律 0。 */
    val parts: List<Int>
        get() = listOf(days, hours, minutes).map { raw ->
            if (raw.isNotEmpty() && raw.all { it.isDigit() }) raw.toIntOrNull() ?: 0 else 0
        }

    /** 全空 ⇒ 还没填（与「填了 0」区分，用于决定是否显示错误）。 */
    val isBlank: Boolean get() = days.isEmpty() && hours.isEmpty() && minutes.isEmpty()

    /** 合计分钟数；任意一段超过 [MAX_CUSTOM_OFFSET_MINUTES]，或合计超出上限时为 null。 */
    fun toOffsetMinutes(): Int? {
        val (d, h, m) = parts
        if (parts.any { it > MAX_CUSTOM_OFFSET_MINUTES }) return null
        val total = d.toLong() * 1440L + h.toLong() * 60L + m.toLong()
        return if (total > MAX_CUSTOM_OFFSET_MINUTES) null else total.toInt()
    }
}

/**
 * 把现有策略里「某锚点下最大提前量 + 1 小时」作为新提醒点的初值，避免连续添加时
 * 每次都从默认 1 小时开始、撞上「重复提醒点」（[ReminderPoint] 去重会静默忽略）。
 * 空策略返回 60。
 */
fun suggestedOffsetMinutes(existing: List<ReminderPoint>, anchor: ReminderAnchor): Int {
    val max = existing.filter { it.anchor == anchor }.maxOfOrNull { it.offsetMinutes } ?: return 60
    return (max + 60).coerceAtMost(MAX_CUSTOM_OFFSET_MINUTES)
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
