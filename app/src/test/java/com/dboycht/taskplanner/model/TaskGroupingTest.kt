package com.dboycht.taskplanner.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TaskGroupingTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    /** 2026-10-24 12:00（周六，本地时区）。 */
    private val now: Long =
        ZonedDateTime.of(2026, 10, 24, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun millisOf(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private fun task(
        id: Long,
        due: Long? = null,
        start: Long? = null,
        status: TaskStatus = TaskStatus.TODO,
        priority: Int = 50,
        title: String = "T$id",
        completedAt: Long? = null,
        updatedAt: Long = 0L,
    ) = Task(
        id = id, title = title, startTime = start, dueTime = due,
        status = status, priority = priority,
        completedAt = completedAt, updatedAt = updatedAt,
    )

    // ---------- 分组 ----------

    @Test
    fun `已完成任务进 DONE 组`() {
        val t = task(1, status = TaskStatus.DONE)
        assertEquals(TaskGroup.DONE, TaskGrouper.groupOf(t, now, zone))
    }

    @Test
    fun `截止时间已过且未完成进 OVERDUE 组`() {
        val t = task(1, due = millisOf(2026, 10, 23, 18, 0))
        assertEquals(TaskGroup.OVERDUE, TaskGrouper.groupOf(t, now, zone))
    }

    @Test
    fun `截止时刻恰好等于 now 不算逾期`() {
        val t = task(1, due = now)
        assertEquals(TaskGroup.TODAY, TaskGrouper.groupOf(t, now, zone))
    }

    @Test
    fun `截止时间在今天但时刻已过进 OVERDUE 组`() {
        // 今天 09:00 截止、now 为 12:00：逾期判定（due < now）优先于「今天」判定。
        val t = task(1, due = millisOf(2026, 10, 24, 9, 0))
        assertEquals(TaskGroup.OVERDUE, TaskGrouper.groupOf(t, now, zone))
    }

    @Test
    fun `截止时间在明天进 UPCOMING 组`() {
        val t = task(1, due = millisOf(2026, 10, 25, 9, 0))
        assertEquals(TaskGroup.UPCOMING, TaskGrouper.groupOf(t, now, zone))
    }

    @Test
    fun `开始时间在今天进 TODAY 组`() {
        val t = task(1, start = millisOf(2026, 10, 24, 9, 0), due = millisOf(2026, 10, 26, 9, 0))
        assertEquals(TaskGroup.TODAY, TaskGrouper.groupOf(t, now, zone))
    }

    @Test
    fun `已开始但无截止的进 TODAY 组`() {
        val t = task(1, start = millisOf(2026, 10, 20, 9, 0), due = null)
        assertEquals(TaskGroup.TODAY, TaskGrouper.groupOf(t, now, zone))
    }

    @Test
    fun `开始时间在未来进 UPCOMING 组`() {
        val t = task(1, start = millisOf(2026, 10, 26, 9, 0))
        assertEquals(TaskGroup.UPCOMING, TaskGrouper.groupOf(t, now, zone))
    }

    @Test
    fun `开始与截止都未设置进未定时组`() {
        val t = task(1)
        assertEquals(TaskGroup.NO_DATE, TaskGrouper.groupOf(t, now, zone))
        assertEquals("未定时", TaskGroup.NO_DATE.label)
    }

    // ---------- 排序 ----------

    @Test
    fun `OVERDUE 组最紧急的在前`() {
        val earlier = task(1, due = millisOf(2026, 10, 20, 18, 0))
        val later = task(2, due = millisOf(2026, 10, 22, 9, 0))
        val grouped = TaskGrouper.groupAndSort(listOf(later, earlier), now, zone)
        assertEquals(listOf(1L, 2L), grouped.getValue(TaskGroup.OVERDUE).map { it.id })
    }

    @Test
    fun `TODAY 组同时截止时优先级数值大的在前`() {
        val low = task(1, due = millisOf(2026, 10, 24, 18, 0), priority = 20)
        val high = task(2, due = millisOf(2026, 10, 24, 18, 0), priority = 90)
        val grouped = TaskGrouper.groupAndSort(listOf(low, high), now, zone)
        assertEquals(listOf(2L, 1L), grouped.getValue(TaskGroup.TODAY).map { it.id })
    }

    @Test
    fun `TODAY 组先截止的在前`() {
        val evening = task(1, due = millisOf(2026, 10, 24, 20, 0))
        val afternoon = task(2, due = millisOf(2026, 10, 24, 15, 0))
        val grouped = TaskGrouper.groupAndSort(listOf(evening, afternoon), now, zone)
        assertEquals(listOf(2L, 1L), grouped.getValue(TaskGroup.TODAY).map { it.id })
    }

    @Test
    fun `NO_DATE 组按优先级数值降序`() {
        val low = task(1, priority = 10)
        val high = task(2, priority = 100)
        val grouped = TaskGrouper.groupAndSort(listOf(low, high), now, zone)
        assertEquals(listOf(2L, 1L), grouped.getValue(TaskGroup.NO_DATE).map { it.id })
    }

    @Test
    fun `DONE 组最近完成的在前`() {
        val older = task(1, status = TaskStatus.DONE, completedAt = 100L, updatedAt = 100L)
        val newer = task(2, status = TaskStatus.DONE, completedAt = 200L, updatedAt = 200L)
        val grouped = TaskGrouper.groupAndSort(listOf(older, newer), now, zone)
        assertEquals(listOf(2L, 1L), grouped.getValue(TaskGroup.DONE).map { it.id })
    }

    // ---------- 组装 ----------

    @Test
    fun `分组键按展示顺序排列且未定时最前已完成垫底`() {
        val tasks = listOf(
            task(1, due = millisOf(2026, 10, 23, 18, 0)), // OVERDUE
            task(2, due = millisOf(2026, 10, 24, 18, 0)), // TODAY
            task(3),                                      // NO_DATE（未定时）
            task(4, status = TaskStatus.DONE, completedAt = 1L, updatedAt = 1L), // DONE
        )
        val grouped = TaskGrouper.groupAndSort(tasks, now, zone)
        assertEquals(
            listOf(TaskGroup.NO_DATE, TaskGroup.OVERDUE, TaskGroup.TODAY, TaskGroup.DONE),
            grouped.keys.toList(),
        )
    }
}
