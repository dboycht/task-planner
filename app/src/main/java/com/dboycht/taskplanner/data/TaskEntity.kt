package com.dboycht.taskplanner.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dboycht.taskplanner.model.Priority
import com.dboycht.taskplanner.model.ReminderPolicy
import com.dboycht.taskplanner.model.Task
import com.dboycht.taskplanner.model.TaskStatus

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
    val calendarEventId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
)

/** 越界 ordinal 一律宽容回退默认值，避免坏行崩溃。 */
fun TaskEntity.toDomain(): Task = Task(
    id = id,
    title = title,
    description = description,
    startTime = startTime,
    dueTime = dueTime,
    priority = Priority.entries.getOrElse(priority) { Priority.MEDIUM },
    status = TaskStatus.entries.getOrElse(status) { TaskStatus.TODO },
    tags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() },
    reminderPolicy = ReminderPolicy.decode(reminderPolicy),
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
    priority = priority.ordinal,
    status = status.ordinal,
    tags = tags.joinToString(","),
    reminderPolicy = reminderPolicy.encode(),
    calendarEventId = calendarEventId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
)
