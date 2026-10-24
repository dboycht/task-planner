package com.dboycht.taskplanner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TaskValidatorTest {

    @Test
    fun `空标题报错`() {
        assertNotNull(TaskValidator.validate("", null, null))
    }

    @Test
    fun `纯空白标题报错`() {
        assertNotNull(TaskValidator.validate("   \t ", null, null))
    }

    @Test
    fun `无时间只有标题合法`() {
        assertNull(TaskValidator.validate("写周报", null, null))
    }

    @Test
    fun `只有开始时间合法`() {
        assertNull(TaskValidator.validate("写周报", 100L, null))
    }

    @Test
    fun `只有截止时间合法`() {
        assertNull(TaskValidator.validate("写周报", null, 100L))
    }

    @Test
    fun `截止早于开始报错`() {
        assertNotNull(TaskValidator.validate("写周报", 200L, 100L))
    }

    @Test
    fun `截止等于开始合法`() {
        assertNull(TaskValidator.validate("写周报", 100L, 100L))
    }

    @Test
    fun `错误信息是中文可展示文本`() {
        assertEquals("标题不能为空", TaskValidator.validate("", null, null))
        assertEquals(
            "截止时间不能早于开始时间",
            TaskValidator.validate("写周报", 200L, 100L),
        )
    }
}
