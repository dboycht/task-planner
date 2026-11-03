package com.dboycht.taskplanner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskFilterTest {

    private fun task(
        id: Long,
        title: String,
        description: String = "",
        tags: List<String> = emptyList(),
        priority: Int = 50,
        status: TaskStatus = TaskStatus.TODO,
    ) = Task(id = id, title = title, description = description, tags = tags, priority = priority, status = status)

    private val tasks = listOf(
        task(1, "写物理实验报告", description = "力学部分", tags = listOf("物理", "实验"), priority = 80),
        task(2, "买菜", tags = listOf("生活", "GROCERY"), priority = 20),
        task(3, "物理竞赛真题", tags = listOf("物理"), priority = 60, status = TaskStatus.DOING),
        task(4, "已交材料", tags = listOf("杂项"), priority = 10, status = TaskStatus.DONE),
    )

    @Test
    fun `空过滤器原样返回且不激活`() {
        val f = TaskFilter()
        assertFalse(f.isActive)
        assertEquals(tasks, TaskFilter.apply(tasks, f))
    }

    @Test
    fun `关键词命中标题描述标签 且大小写不敏感`() {
        assertTrue(TaskFilter(query = "物理").matches(tasks[0]))
        assertTrue(TaskFilter(query = "力学").matches(tasks[0])) // 描述命中
        assertTrue(TaskFilter(query = "生活").matches(tasks[1])) // 标签命中
        assertFalse(TaskFilter(query = "买菜").matches(tasks[0]))
        assertTrue(TaskFilter(query = "grocery").matches(tasks[1])) // 大小写不敏感
        assertEquals(2, TaskFilter.apply(tasks, TaskFilter(query = "物理")).size)
    }

    @Test
    fun `状态过滤`() {
        val f = TaskFilter(status = TaskStatus.DONE)
        assertEquals(listOf(4L), TaskFilter.apply(tasks, f).map { it.id })
    }

    @Test
    fun `优先级三档过滤 与 priorityLabel 同界`() {
        assertEquals(listOf(1L), TaskFilter.apply(tasks, TaskFilter(priorityBucket = 2)).map { it.id })
        assertEquals(listOf(3L), TaskFilter.apply(tasks, TaskFilter(priorityBucket = 1)).map { it.id })
        assertEquals(listOf(2L, 4L), TaskFilter.apply(tasks, TaskFilter(priorityBucket = 0)).map { it.id })
        // 边界：33 低 / 34 中 / 66 中 / 67 高。
        assertEquals(0, TaskFilter.priorityBucketOf(33))
        assertEquals(1, TaskFilter.priorityBucketOf(34))
        assertEquals(1, TaskFilter.priorityBucketOf(66))
        assertEquals(2, TaskFilter.priorityBucketOf(67))
    }

    @Test
    fun `标签 OR 命中`() {
        val f = TaskFilter(tags = setOf("生活", "杂项"))
        assertEquals(listOf(2L, 4L), TaskFilter.apply(tasks, f).map { it.id })
        // 不存在的标签 → 全过滤掉。
        assertTrue(TaskFilter.apply(tasks, TaskFilter(tags = setOf("没有"))).isEmpty())
    }

    @Test
    fun `多条件取交集`() {
        val f = TaskFilter(query = "物理", status = TaskStatus.DOING)
        assertEquals(listOf(3L), TaskFilter.apply(tasks, f).map { it.id })
    }

    @Test
    fun `isActive 各维度单独激活`() {
        assertTrue(TaskFilter(query = " ").isActive.not()) // 纯空白不算
        assertTrue(TaskFilter(query = "x").isActive)
        assertTrue(TaskFilter(status = TaskStatus.TODO).isActive)
        assertTrue(TaskFilter(priorityBucket = 0).isActive)
        assertTrue(TaskFilter(tags = setOf("t")).isActive)
    }
}
