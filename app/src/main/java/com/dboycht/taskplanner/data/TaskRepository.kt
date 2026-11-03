package com.dboycht.taskplanner.data

import com.dboycht.taskplanner.model.RecurrenceGenerator
import com.dboycht.taskplanner.model.Task
import com.dboycht.taskplanner.model.TaskStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.ZoneId
import java.util.UUID

/**
 * 任务仓库：Room 是单一事实源（系统日历是投影，由 CalendarSyncManager 在外层跟进；
 * 重复任务是普通行的集合，滚动生成见 RecurrenceGenerator）。
 */
class TaskRepository(private val dao: TaskDao) {

    fun observeTasks(): Flow<List<Task>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getTask(id: Long): Task? = dao.getById(id)?.toDomain()

    /** 提醒重排 / 日历同步用的一次性快照。 */
    suspend fun allTasks(): List<Task> = dao.getAll().map { it.toDomain() }

    suspend fun getByGroup(group: String): List<Task> =
        dao.getByGroup(group).map { it.toDomain() }

    /** 日历投影映射回写（CalendarSyncManager 专用）。 */
    suspend fun setCalendarEventId(id: Long, eventId: Long?) =
        dao.setCalendarEventId(id, eventId)

    /**
     * 新建（id == 0）返回新 id；更新返回原 id。时间戳在此收口。
     * 重复任务在此分配/保留系列标识，并即时滚动生成下一次（保底未来一个实例）。
     */
    suspend fun save(task: Task, now: Long = System.currentTimeMillis()): Long {
        val group = task.recurGroup
            ?: (if (task.recurRule.isRecurring) newGroupId() else null)
        return if (task.id == 0L) {
            val id = dao.insert(
                task.toEntity().copy(
                    recurGroup = group,
                    createdAt = now,
                    updatedAt = now,
                )
            )
            if (task.recurRule.isRecurring) ensureRecurrence(now)
            id
        } else {
            dao.update(task.toEntity().copy(recurGroup = group, updatedAt = now))
            if (task.recurRule.isRecurring) ensureRecurrence(now)
            task.id
        }
    }

    /**
     * 勾选完成 / 取消完成；completedAt 随之登记或清空。
     * 重复任务勾完成后可能补出下一次（滚动窗口保底）。
     */
    suspend fun setDone(id: Long, done: Boolean, now: Long = System.currentTimeMillis()) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                status = if (done) TaskStatus.DONE.ordinal else TaskStatus.TODO.ordinal,
                completedAt = if (done) now else null,
                updatedAt = now,
            )
        )
        if (done && existing.recurGroup != null) ensureRecurrence(now)
    }

    /**
     * 删除：返回**被删任务的删除前快照**（含 calendarEventId），调用方据此清理日历投影。
     * 重复任务删除语义（v1）：
     * - [deleteSeries] = true ⇒ 整个系列（同 recurGroup）全部删除；
     * - 否则只删这一条；若它恰是组内「最新实例」（种子），把其余行的规则清掉，
     *   防止滚动生成下一轮把它原样补回来（僵尸）。
     */
    suspend fun delete(
        id: Long,
        deleteSeries: Boolean = false,
        now: Long = System.currentTimeMillis(),
    ): List<Task> {
        val target = dao.getById(id) ?: return emptyList()
        val group = target.recurGroup
        if (deleteSeries && group != null) {
            val all = dao.getByGroup(group)
            dao.deleteByGroup(group)
            return all.map { it.toDomain() }
        }
        dao.deleteById(id)
        if (group != null) {
            val groupRows = dao.getByGroup(group)
            val seed = groupRows.maxWithOrNull(
                compareBy<TaskEntity> { it.dueTime ?: Long.MIN_VALUE }.thenBy { it.id }
            )
            if (seed != null && seed.id == id) {
                // 删的是种子：系列止于剩余最后一条。
                dao.clearRuleByGroup(group)
            }
        }
        return listOf(target.toDomain())
    }

    /**
     * 滚动生成：对每个重复组物化「第一个 > now 的下一次」（90 天封顶，每组至多 1 条）。
     * 幂等：组内已有该时刻实例则跳过。应用启动 / 保存 / 勾完成 / 系统时间事件后调用。
     */
    suspend fun ensureRecurrence(now: Long = System.currentTimeMillis()) {
        val zone = ZoneId.systemDefault()
        val tasks = dao.getAll().map { it.toDomain() }
        val plan = RecurrenceGenerator.planNextInstances(tasks, now, zone)
        for (p in plan) {
            dao.insert(
                p.source.toEntity().copy(
                    id = 0L,
                    startTime = p.startTime,
                    dueTime = p.dueTime,
                    status = TaskStatus.TODO.ordinal,
                    completedAt = null,
                    calendarEventId = null,
                    recurGroup = p.source.recurGroup,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }
    }

    private fun newGroupId(): String = "g" + UUID.randomUUID().toString().replace("-", "").take(16)
}
