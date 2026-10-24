package com.dboycht.taskplanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    /** 无截止的排最后，其余按到期升序；组内二次排序在领域层做。 */
    @Query("SELECT * FROM tasks ORDER BY dueTime IS NULL, dueTime, priority, title")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Insert
    suspend fun insert(entity: TaskEntity): Long

    @Update
    suspend fun update(entity: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)
}
