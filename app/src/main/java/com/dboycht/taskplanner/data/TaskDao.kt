package com.dboycht.taskplanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    /** 无截止的排最后，其余按到期升序；priority 为 0~100 连续值，越大越紧急 → 降序。 */
    @Query("SELECT * FROM tasks ORDER BY dueTime IS NULL, dueTime, priority DESC, title")
    fun observeAll(): Flow<List<TaskEntity>>

    /** 提醒重排用的一次性全量快照（不订阅，避免后台常驻观察者）。 */
    @Query("SELECT * FROM tasks")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Insert
    suspend fun insert(entity: TaskEntity): Long

    @Update
    suspend fun update(entity: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)
}
