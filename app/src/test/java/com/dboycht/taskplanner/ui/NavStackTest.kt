package com.dboycht.taskplanner.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导航编码 / 解码。重点回归 2026-10-29 的 bug：
 * 「新建任务」若每次编码成同一个串，Activity 作用域的 viewModel(key=) 会复用上一次的草稿。
 */
class NavStackTest {

    @Test
    fun `编码解码往返_编辑已有任务`() {
        val screen = Screen.EditTask(taskId = 42L, visitId = 7L)
        assertEquals(screen, decodeScreen(screen.encode()))
    }

    @Test
    fun `编码解码往返_新建任务`() {
        val screen = Screen.EditTask(taskId = null, visitId = 3L)
        assertEquals(screen, decodeScreen(screen.encode()))
    }

    @Test
    fun `编码解码往返_预设`() {
        assertEquals(
            Screen.EditPreset(presetId = 9L, visitId = 1L),
            decodeScreen(Screen.EditPreset(presetId = 9L, visitId = 1L).encode()),
        )
        assertEquals(
            Screen.EditPreset(presetId = null, visitId = 2L),
            decodeScreen(Screen.EditPreset(presetId = null, visitId = 2L).encode()),
        )
        assertEquals(Screen.List, decodeScreen(Screen.List.encode()))
        assertEquals(Screen.Presets, decodeScreen(Screen.Presets.encode()))
    }

    @Test
    fun `同一个新建目标_两次进入必须编码不同`() {
        val first = Screen.EditTask(taskId = null, visitId = 1L).encode()
        val second = Screen.EditTask(taskId = null, visitId = 2L).encode()
        assertNotEquals(first, second)
    }

    @Test
    fun `同一个已有任务_两次进入也必须编码不同`() {
        val first = Screen.EditTask(taskId = 5L, visitId = 1L).encode()
        val second = Screen.EditTask(taskId = 5L, visitId = 2L).encode()
        assertNotEquals(first, second)
    }

    @Test
    fun `旧格式与垃圾串宽容解码`() {
        // 旧版编码 "task:12"（没有 visitId）也要能认出来
        assertEquals(Screen.EditTask(12L, 0L), decodeScreen("task:12"))
        assertEquals(Screen.EditTask(null, 0L), decodeScreen("task:new"))
        assertEquals(Screen.List, decodeScreen(""))
        assertEquals(Screen.List, decodeScreen("something-else"))
        assertEquals(Screen.List, decodeScreen("task:"))
    }

    @Test
    fun `解码出的编辑页_visitId 非空以区分实例`() {
        val decoded = decodeScreen("task:new:8")
        assertTrue(decoded is Screen.EditTask && decoded.visitId == 8L)
    }
}
