package com.dboycht.taskplanner.reminder

import com.dboycht.taskplanner.model.ReminderAnchor
import com.dboycht.taskplanner.model.ReminderPolicy
import com.dboycht.taskplanner.model.Task
import com.dboycht.taskplanner.model.TaskStatus
import com.dboycht.taskplanner.model.formatOffsetMinutes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 一个已算好的提醒触发点：时刻 + 来源（锚点/提前量）。
 * [offsetMinutes] = 0 表示「到点提醒」，通知文案据此区分。
 */
data class DueReminder(
    val triggerAt: Long,
    val anchor: ReminderAnchor,
    val offsetMinutes: Int,
)

/** 策略展开成触发点（锚点时间缺失的点跳过），按时刻升序；不过滤过去。 */
fun ReminderPolicy.dueReminders(startTime: Long?, dueTime: Long?): List<DueReminder> =
    points.mapNotNull { p ->
        val anchorAt = when (p.anchor) {
            ReminderAnchor.START -> startTime
            ReminderAnchor.DUE -> dueTime
        } ?: return@mapNotNull null
        DueReminder(
            triggerAt = anchorAt - p.offsetMinutes * 60_000L,
            anchor = p.anchor,
            offsetMinutes = p.offsetMinutes,
        )
    }.sortedBy { it.triggerAt }

/** 下一个**尚未**到点的提醒（严格晚于 now）；没有返回 null。 */
fun ReminderPolicy.nextReminder(startTime: Long?, dueTime: Long?, now: Long): DueReminder? =
    dueReminders(startTime, dueTime).firstOrNull { it.triggerAt > now }

/**
 * 闹钟响起时用的提醒点：已到点（<= now）里**最晚**的一个。
 * 设备关机/长时间待机错过多个点时不会连发，只报最近的那一个。
 */
fun ReminderPolicy.reminderDueNow(startTime: Long?, dueTime: Long?, now: Long): DueReminder? =
    dueReminders(startTime, dueTime).lastOrNull { it.triggerAt <= now }

/** 值得挂闹钟的条件：未完成，且还有未来触发点。 */
fun Task.needsReminder(now: Long): Boolean =
    status != TaskStatus.DONE && reminderPolicy.nextReminder(startTime, dueTime, now) != null

/**
 * 一次全量对齐的计划（纯函数，便于单测）：
 * - [toSchedule]：应当挂闹钟的任务 → 触发时刻（**每次同步都重挂**，靠 PendingIntent 幂等覆盖）；
 * - [toCancel]：之前挂过、现在已经不该挂的任务 id（已完成 / 已删除 / 无未来提醒点）。
 *
 * 「每次都重挂」是因为设备重启、系统清理、时区变更后闹钟可能丢失，
 * 只依赖簿记极易漏排；重挂是覆盖语义，代价可忽略。
 */
data class ReminderSyncPlan(
    val toSchedule: Map<Long, Long>,
    val toCancel: Set<Long>,
)

fun planReminderSync(
    tasks: List<Task>,
    previouslyScheduled: Set<Long>,
    now: Long,
): ReminderSyncPlan {
    val toSchedule = LinkedHashMap<Long, Long>()
    for (task in tasks) {
        if (task.id == 0L) continue
        val next = task.reminderPolicy.nextReminder(task.startTime, task.dueTime, now)
        if (task.status != TaskStatus.DONE && next != null) {
            toSchedule[task.id] = next.triggerAt
        }
    }
    return ReminderSyncPlan(
        toSchedule = toSchedule,
        toCancel = previouslyScheduled - toSchedule.keys,
    )
}

/**
 * 「等待多久后出发」的提示（列表/通知共用）：
 * 到点提醒 → null（文案用「现在开始 / 现在截止」）。
 */
fun DueReminder.waitingLabel(): String? = when {
    offsetMinutes == 0 -> null
    else -> formatOffsetMinutes(offsetMinutes)
}

private val NOTIFY_TIME_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm")

/** 通知正文：说明「什么时候到期/开始」，让用户不用打开应用也知道是什么事。 */
fun reminderNotificationText(task: Task, reminder: DueReminder, zone: ZoneId): String {
    val waiting = reminder.waitingLabel()
    val dueText = task.dueTime?.let { formatMoment(it, zone) }
    val startText = task.startTime?.let { formatMoment(it, zone) }
    return when (reminder.anchor) {
        ReminderAnchor.DUE -> when {
            reminder.offsetMinutes == 0 ->
                if (dueText != null) "现在到截止时间（$dueText）" else "现在到截止时间"
            else ->
                if (dueText != null) "${waiting}后截止（$dueText）" else "${waiting}后截止"
        }
        ReminderAnchor.START -> when {
            reminder.offsetMinutes == 0 ->
                if (startText != null) "现在开始（$startText）" else "现在开始"
            else ->
                if (startText != null) "${waiting}后开始（$startText）" else "${waiting}后开始"
        }
    }
}

fun formatMoment(epochMs: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(epochMs).atZone(zone).format(NOTIFY_TIME_FMT)
