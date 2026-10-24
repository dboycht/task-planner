package com.dboycht.taskplanner.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PriorityTest {

    @Test
    fun `区间标签三段划分`() {
        assertEquals("低", priorityLabel(0))
        assertEquals("低", priorityLabel(33))
        assertEquals("中", priorityLabel(34))
        assertEquals("中", priorityLabel(66))
        assertEquals("高", priorityLabel(67))
        assertEquals("高", priorityLabel(100))
    }

    @Test
    fun `clamp 收进 0 到 100`() {
        assertEquals(0, clampPriority(-5))
        assertEquals(100, clampPriority(999))
        assertEquals(42, clampPriority(42))
    }

    @Test
    fun `默认值在中间档`() {
        assertEquals(50, PRIORITY_DEFAULT)
        assertEquals("中", priorityLabel(PRIORITY_DEFAULT))
    }
}
