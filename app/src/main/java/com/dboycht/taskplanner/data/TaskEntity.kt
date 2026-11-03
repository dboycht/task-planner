package com.dboycht.taskplanner.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dboycht.taskplanner.model.RecurrenceRule
import com.dboycht.taskplanner.model.ReminderPolicy
import com.dboycht.taskplanner.model.Task
import com.dboycht.taskplanner.model.TaskStatus
import com.dboycht.taskplanner.model.clampPriority

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val description: String,
    val startTime: Long?,
    val dueTime: Long?,
    val priority: Int,
    val status: Int,
    val tags: String,
    val reminderPolicy: String,
    /** 重复规则编码（空 = 不重复）；v4 新增列，可空。 */
    val recurRule: String?,
    /** 重复系列标识；同一系列实例共享，非重复任务为 null。v4 新增列，可空。 */
    val recurGroup: String?,
    val calendarEventId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
)

/** 越界 priority / status 一律宽容回退默认值，避免坏行崩溃。 */
fun TaskEntity.toDomain(): Task = Task(
    id = id,
    title = title,
    description = description,
    startTime = startTime,
    dueTime = dueTime,
    priority = clampPriority(priority),
    status = TaskStatus.entries.getOrElse(status) { TaskStatus.TODO },
    tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
    reminderPolicy = ReminderPolicy.decode(reminderPolicy),
    recurRule = RecurrenceRule.decode(recurRule),
    recurGroup = recurGroup,
    calendarEventId = calendarEventId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title,
    description = description,
    startTime = startTime,
    dueTime = dueTime,
    priority = priority,
    status = status.ordinal,
    tags = tags.joinToString(","),
    reminderPolicy = reminderPolicy.encode(),
    recurRule = recurRule.encode(),
    recurGroup = recurGroup,
    calendarEventId = calendarEventId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
)
