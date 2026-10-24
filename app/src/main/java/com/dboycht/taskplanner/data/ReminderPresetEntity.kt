package com.dboycht.taskplanner.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.dboycht.taskplanner.model.ReminderPreset
import com.dboycht.taskplanner.model.ReminderPolicy

@Entity(tableName = "reminder_presets")
data class ReminderPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val policy: String,
    val isBuiltIn: Boolean,
    val sortOrder: Int,
)

fun ReminderPresetEntity.toDomain(): ReminderPreset = ReminderPreset(
    id = id,
    name = name,
    policy = ReminderPolicy.decode(policy),
    isBuiltIn = isBuiltIn,
    sortOrder = sortOrder,
)

fun ReminderPreset.toEntity(): ReminderPresetEntity = ReminderPresetEntity(
    id = id,
    name = name,
    policy = policy.encode(),
    isBuiltIn = isBuiltIn,
    sortOrder = sortOrder,
)
