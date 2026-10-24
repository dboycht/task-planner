package com.dboycht.taskplanner.ui.theme

import androidx.compose.ui.graphics.Color
import com.dboycht.taskplanner.model.PRIORITY_MAX
import com.dboycht.taskplanner.model.PRIORITY_MIN
import com.dboycht.taskplanner.model.clampPriority

/**
 * 优先级 → 颜色：HSV 色相插值，最低蓝（210°）→ 最高红（0°），
 * 中途自然经过青/绿/黄/橙（温度带），深浅色主题下都够亮。
 */
fun priorityColor(priority: Int): Color {
    val t = (clampPriority(priority) - PRIORITY_MIN).toFloat() /
        (PRIORITY_MAX - PRIORITY_MIN).toFloat()
    val hue = 210f - 210f * t
    return Color.hsv(hue, 0.72f, 0.98f)
}
