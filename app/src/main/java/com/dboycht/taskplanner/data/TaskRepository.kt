package com.dboycht.taskplanner.data

import com.dboycht.taskplanner.model.Task
import com.dboycht.taskplanner.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 任务仓库：Room 是单一事实源（系统日历在 M2 作为投影接入，失败不回滚任务）。
 */
class TaskRepository(private val dao: TaskDao) {

    fun observeTasks(): Flow<List<Task>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getTask(id: Long): Task? = dao.getById(id)?.toDomain()

    /** 提醒重排用的一次性快照。 */
    suspend fun allTasks(): List<Task> = dao.getAll().map { it.toDomain() }

    /** 新建（id == 0）返回新 id；更新返回原 id。时间戳在此收口。 */
    suspend fun save(task: Task): Long {
        val now = System.currentTimeMillis()
        return if (task.id == 0L) {
            dao.insert(task.toEntity().copy(createdAt = now, updatedAt = now))
        } else {
            dao.update(task.toEntity().copy(updatedAt = now))
            task.id
        }
    }

    /** 勾选完成 / 取消完成；completedAt 随之登记或清空。 */
    suspend fun setDone(id: Long, done: Boolean) {
        val existing = dao.getById(id) ?: return
        val now = System.currentTimeMillis()
        dao.update(
            existing.copy(
                status = if (done) TaskStatus.DONE.ordinal else TaskStatus.TODO.ordinal,
                completedAt = if (done) now else null,
                updatedAt = now,
            )
        )
    }

    suspend fun delete(id: Long) = dao.deleteById(id)
}
