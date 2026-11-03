package com.dboycht.taskplanner.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class WidgetModelTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val now: Long =
        LocalDateTime.of(2026, 11, 4, 12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun task(
        id: Long,
        title: String = "t",
        due: Long?,
        priority: Int = 50,
        status: TaskStatus = TaskStatus.TODO,
    ) = Task(id = id, title = title, dueTime = due, priority = priority, status = status)

    @Test
    fun `分组头与顺序 逾期今日近期 已完成与未定时不进组件`() {
        val tasks = listOf(
            task(1, "过期", due = at(2026, 11, 2, 20, 0)),
            task(2, "今天", due = at(2026, 11, 4, 20, 0)),
            task(3, "近期", due = at(2026, 11, 20, 20, 0)),
            task(4, "完成", due = at(2026, 11, 4, 21, 0), status = TaskStatus.DONE),
            task(5, "未定时", due = null),
        )
        val rows = WidgetModel.buildRows(tasks, now, zone)
        val headers = rows.filterIsInstance<WidgetModel.Row.Header>().map { it.label }
        assertEquals(listOf("已逾期 · 1", "今日 · 1", "近期 · 1"), headers)
        val ids = rows.filterIsInstance<WidgetModel.Row.Item>().map { it.taskId }
        assertEquals(listOf(1L, 2L, 3L), ids)
    }

    @Test
    fun `行数封顶 12 条 超出截断`() {
        val tasks = (1..30L).map {
            task(it, due = at(2026, 11, 4, 8, 0) + it * 60_000L)
        }
        val rows = WidgetModel.buildRows(tasks, now, zone)
        assertEquals(WidgetModel.MAX_ROWS, rows.filterIsInstance<WidgetModel.Row.Item>().size)
    }

    @Test
    fun `点色 蓝到红线性插值 端点正确`() {
        assertEquals(0xFF3B82F6.toInt(), WidgetModel.dotColor(0))
        assertEquals(0xFFEF4444.toInt(), WidgetModel.dotColor(100))
        // 中点介于两端之间（R 通道随优先级升、B 通道随优先级降）。
        val mid = WidgetModel.dotColor(50)
        val r = (mid shr 16) and 0xFF
        val b = mid and 0xFF
        assertTrue("r 应升", r > 0x3B)
        assertTrue("b 应降", b < 0xF6)
    }

    @Test
    fun `无任务返回空`() {
        assertTrue(WidgetModel.buildRows(emptyList(), now, zone).isEmpty())
    }
}
