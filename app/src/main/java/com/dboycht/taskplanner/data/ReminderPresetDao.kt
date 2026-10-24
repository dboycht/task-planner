package com.dboycht.taskplanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderPresetDao {

    /** 内置在前（isBuiltIn DESC），再按 sortOrder、id。 */
    @Query("SELECT * FROM reminder_presets ORDER BY isBuiltIn DESC, sortOrder, id")
    fun observeAll(): Flow<List<ReminderPresetEntity>>

    @Query("SELECT * FROM reminder_presets WHERE id = :id")
    suspend fun getById(id: Long): ReminderPresetEntity?

    @Query("SELECT COUNT(*) FROM reminder_presets")
    suspend fun count(): Int

    @Insert
    suspend fun insertAll(items: List<ReminderPresetEntity>)

    @Insert
    suspend fun insert(entity: ReminderPresetEntity): Long

    @Update
    suspend fun update(entity: ReminderPresetEntity)

    @Query("DELETE FROM reminder_presets WHERE id = :id")
    suspend fun deleteById(id: Long)
}
