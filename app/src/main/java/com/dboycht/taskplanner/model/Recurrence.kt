package com.dboycht.taskplanner.model

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 重复规则。编码存 Room（tasks.recurRule，可空列）：
 * 空/无法识别 = 不重复；`D`/`W`/`M` = 每天/每周/每月；`C<N>` = 每 N 天（2~366）。
 *
 * 语义（v1，见 DEVELOPMENT.md §2.6）：
 * - 重复任务必须有截止时间（校验层保证）；下一次发生按截止时间推。
 * - 每月按「日号顺延 + 月末收窄」：31 号的下一次在 2 月是 28/29 号，之后按顺延结果继续。
 *   （月内日号会漂移，v1 已知限制。）
 * - 时区/夏令时：发生时刻按本地墙钟推算（ZonedDateTime 加减），再转 epoch 毫秒。
 */
enum class RecurKind(val label: String) {
    NONE("不重复"),
    DAILY("每天"),
    WEEKLY("每周"),
    MONTHLY("每月"),
    CUSTOM_DAYS("自定义");
}

data class RecurrenceRule(val kind: RecurKind = RecurKind.NONE, val intervalDays: Int = 1) {

    val isRecurring: Boolean get() = kind != RecurKind.NONE

    /** 展示文案（列表徽标 / 编辑页）。 */
    fun label(): String = when (kind) {
        RecurKind.NONE -> "不重复"
        RecurKind.DAILY -> "每天"
        RecurKind.WEEKLY -> "每周"
        RecurKind.MONTHLY -> "每月"
        RecurKind.CUSTOM_DAYS -> "每 $intervalDays 天"
    }

    fun encode(): String = when (kind) {
        RecurKind.NONE -> ""
        RecurKind.DAILY -> "D"
        RecurKind.WEEKLY -> "W"
        RecurKind.MONTHLY -> "M"
        RecurKind.CUSTOM_DAYS -> "C$intervalDays"
    }

    companion object {
        val NONE = RecurrenceRule()

        /** 宽容解码：认不出的串一律退回不重复（宁可少重复也不崩）。 */
        fun decode(raw: String?): RecurrenceRule = when {
            raw.isNullOrBlank() -> NONE
            raw == "D" -> RecurrenceRule(RecurKind.DAILY)
            raw == "W" -> RecurrenceRule(RecurKind.WEEKLY)
            raw == "M" -> RecurrenceRule(RecurKind.MONTHLY)
            raw.length > 1 && raw[0] == 'C' ->
                raw.drop(1).toIntOrNull()
                    ?.takeIf { it in MIN_CUSTOM_DAYS..MAX_CUSTOM_DAYS }
                    ?.let { RecurrenceRule(RecurKind.CUSTOM_DAYS, it) }
                    ?: NONE
            else -> NONE
        }

        const val MIN_CUSTOM_DAYS = 2
        const val MAX_CUSTOM_DAYS = 366
    }
}

/**
 * [currentDue]（epoch 毫秒）的下一次发生；规则不重复或 [currentDue] 非法返回 null。
 * 纯 JVM 可测：时区由调用方传入（显示与调度同用系统时区）。
 */
fun nextOccurrence(rule: RecurrenceRule, currentDue: Long, zone: ZoneId): Long? {
    if (!rule.isRecurring || currentDue <= 0L) return null
    val zdt = Instant.ofEpochMilli(currentDue).atZone(zone)
    val next: ZonedDateTime = when (rule.kind) {
        RecurKind.NONE -> return null
        RecurKind.DAILY -> zdt.plusDays(1)
        RecurKind.WEEKLY -> zdt.plusDays(7)
        RecurKind.CUSTOM_DAYS -> zdt.plusDays(rule.intervalDays.toLong())
        RecurKind.MONTHLY -> plusMonthSameTime(zdt)
    }
    return next.toInstant().toEpochMilli()
}

/** 加一个月，日号收窄到月末（1 月 31 日 → 2 月 28/29 日），墙钟时间保持不变。 */
private fun plusMonthSameTime(zdt: ZonedDateTime): ZonedDateTime {
    val nextMonth = zdt.toLocalDate().plusMonths(1)
    val day = minOf(zdt.dayOfMonth, nextMonth.lengthOfMonth())
    return nextMonth.withDayOfMonth(day).atTime(zdt.toLocalTime()).atZone(zdt.zone)
}
