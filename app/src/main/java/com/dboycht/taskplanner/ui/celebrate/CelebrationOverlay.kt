package com.dboycht.taskplanner.ui.celebrate

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 单粒彩纸：位置/速度用屏幕比例坐标，避免依赖具体分辨率。 */
private class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rotation: Float,
    var vr: Float,
    val color: Color,
    val sizeDp: Float,
    val delayMs: Int,
    val lifeMs: Int,
)

/**
 * 任务完成庆祝浮层：中心绿色对勾 + 一圈彩纸喷发，约 1.8 秒后自行消失。
 * [trigger] 每次自增播放一轮；不拦截任何点击（纯展示层）。
 */
@Composable
fun CelebrationOverlay(trigger: Int, modifier: Modifier = Modifier) {
    var active by remember { mutableStateOf(false) }
    var particles by remember { mutableStateOf(listOf<Particle>()) }
    var elapsedMs by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect
        val rnd = Random(trigger * 31 + 7)
        val palette = listOf(
            Color(0xFFFFC107), Color(0xFF4DD0E1), Color(0xFFFF7043),
            Color(0xFF66BB6A), Color(0xFFAB47BC), Color(0xFFFFEE58),
        )
        particles = List(56) {
            val angle = rnd.nextFloat() * (2.0 * Math.PI).toFloat()
            val speed = 0.35f + rnd.nextFloat() * 0.55f
            Particle(
                x = 0.5f,
                y = 0.42f,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - 0.25f,
                rotation = rnd.nextFloat() * 360f,
                vr = (rnd.nextFloat() - 0.5f) * 720f,
                color = palette[rnd.nextInt(palette.size)],
                sizeDp = 4f + rnd.nextFloat() * 5f,
                delayMs = rnd.nextInt(160),
                lifeMs = 1100 + rnd.nextInt(700),
            )
        }
        active = true
        val start = withFrameNanos { it }
        var last = start
        while (true) {
            val now = withFrameNanos { it }
            elapsedMs = (now - start) / 1_000_000f
            if (elapsedMs > 1800f) break
            val dt = (now - last) / 1_000_000_000f
            last = now
            for (p in particles) {
                if (elapsedMs - p.delayMs < 0f) continue
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.vy += 1.1f * dt // 重力（比例/s²）
                p.rotation += p.vr * dt
            }
        }
        active = false
    }

    if (active) {
        Canvas(modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val fade = if (elapsedMs < 1200f) 1f
            else (1f - (elapsedMs - 1200f) / 600f).coerceIn(0f, 1f)

            // 彩纸
            for (p in particles) {
                val t = elapsedMs - p.delayMs
                if (t < 0f || t > p.lifeMs) continue
                val pw = p.sizeDp.dp.toPx()
                val alpha = fade * (1f - t / p.lifeMs * 0.3f)
                rotate(p.rotation, pivot = Offset(p.x * w, p.y * h)) {
                    drawRoundRect(
                        color = p.color.copy(alpha = alpha),
                        topLeft = Offset(p.x * w - pw / 2f, p.y * h - pw * 0.3f),
                        size = Size(pw, pw * 0.6f),
                        cornerRadius = CornerRadius(2.dp.toPx()),
                    )
                }
            }

            // 中心对勾（两段线依次画出）
            val appear = (elapsedMs / 260f).coerceIn(0f, 1f)
            if (appear > 0f) {
                val cx = 0.5f * w
                val cy = 0.42f * h
                val r = 44.dp.toPx()
                drawCircle(
                    color = Color(0xFF66BB6A).copy(alpha = fade),
                    radius = r,
                    center = Offset(cx, cy),
                    style = Stroke(width = 5.dp.toPx()),
                )
                val seg1 = (appear * 1.7f).coerceAtMost(1f)
                val seg2 = ((appear - 0.58f) * 2.4f).coerceIn(0f, 1f)
                val stroke = 7.dp.toPx()
                val green = Color(0xFF66BB6A).copy(alpha = fade)
                if (seg1 > 0f) {
                    val ax = cx - 20.dp.toPx()
                    val ay = cy + 2.dp.toPx()
                    val bx = cx - 6.dp.toPx()
                    val by = cy + 16.dp.toPx()
                    drawLine(
                        color = green,
                        start = Offset(ax, ay),
                        end = Offset(ax + (bx - ax) * seg1, ay + (by - ay) * seg1),
                        strokeWidth = stroke,
                    )
                }
                if (seg2 > 0f) {
                    val bx = cx - 6.dp.toPx()
                    val by = cy + 16.dp.toPx()
                    val ex = cx + 24.dp.toPx()
                    val ey = cy - 16.dp.toPx()
                    drawLine(
                        color = green,
                        start = Offset(bx, by),
                        end = Offset(bx + (ex - bx) * seg2, by + (ey - by) * seg2),
                        strokeWidth = stroke,
                    )
                }
            }
        }
    }
}
