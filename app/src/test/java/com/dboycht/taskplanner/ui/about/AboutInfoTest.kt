package com.dboycht.taskplanner.ui.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 关于页的纯逻辑部分（版本信息不来自 Android 运行时，可 JVM 单测）。
 * 重点：正式版与测试版的文案必须能区分，且复制出来的信息包含定位所需的三项。
 */
class AboutInfoTest {

    private fun info(debuggable: Boolean) = AboutInfo(
        label = if (debuggable) "任务计划·测试版" else "任务计划",
        versionName = "1.0.3",
        versionCode = 2L,
        packageName = if (debuggable) "com.dboycht.taskplanner.debug" else "com.dboycht.taskplanner",
        debuggable = debuggable,
    )

    @Test
    fun `debug 构建判为测试版_正式构建不是`() {
        assertTrue(info(debuggable = true).isTestBuild())
        assertFalse(info(debuggable = false).isTestBuild())
    }

    @Test
    fun `复制文本含版本号_versionCode_包名`() {
        val text = info(debuggable = false).clipboardText()
        assertTrue(text.contains("1.0.3"))
        assertTrue(text.contains("2"))
        assertTrue(text.contains("com.dboycht.taskplanner"))
    }

    @Test
    fun `正式版复制文本标注正式版_且不出现测试版字样`() {
        val text = info(debuggable = false).clipboardText()
        assertTrue(text.contains("正式版"))
        assertFalse(text.contains("测试版"))
    }

    @Test
    fun `测试版复制文本标注测试版与数据隔离`() {
        val text = info(debuggable = true).clipboardText()
        assertTrue(text.contains("测试版"))
        assertTrue(text.contains("数据与正式版互不相通"))
    }

    @Test
    fun `副标题给出 versionCode 与包名`() {
        assertEquals(
            "versionCode 2 · 包名 com.dboycht.taskplanner.debug",
            info(debuggable = true).subtitle(),
        )
    }

    @Test
    fun `复制文本首行是应用名`() {
        assertEquals("任务计划·测试版", info(debuggable = true).clipboardText().lineSequence().first())
    }
}
