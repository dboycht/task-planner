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

    /** 重复系列的全部实例（删除系列 / 防僵尸生成用）。 */
    @Query("SELECT * FROM tasks WHERE recurGroup = :group")
    suspend fun getByGroup(group: String): List<TaskEntity>

    @Insert
    suspend fun insert(entity: TaskEntity): Long

    @Update
    suspend fun update(entity: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM tasks WHERE recurGroup = :group")
    suspend fun deleteByGroup(group: String)

    /** 系列止步：清掉组内所有行的重复规则（种子实例被删后防僵尸再生成）。 */
    @Query("UPDATE tasks SET recurRule = '' WHERE recurGroup = :group")
    suspend fun clearRuleByGroup(group: String)

    /** 日历投影映射回写（null = 无事件/已清理）。 */
    @Query("UPDATE tasks SET calendarEventId = :eventId WHERE id = :id")
    suspend fun setCalendarEventId(id: Long, eventId: Long?)
}
