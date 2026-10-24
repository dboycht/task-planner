package com.dboycht.taskplanner.ui.anim

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.min

/** Material 强调曲线（emphasized），全局动效统一用它。 */
val EASE = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/**
 * 动效总开关：用户在系统里把「动画时长缩放」设为 0（移除动画）时尊重之，
 * 所有自定义动效退化为直接显示。
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember {
        runCatching {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f) != 0f
    }
}

/**
 * 交错入场：淡入 + 轻微上移（240ms，强调曲线）。[index] 越大出现越晚
 * （每级 35ms，封顶 10 级），用于列表/表单的依次进场。
 */
@Composable
fun Appear(index: Int = 0, enabled: Boolean = true, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(min(index, 10) * 35L)
        shown = true
    }
    val progress by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(240, easing = EASE),
        label = "appear",
    )
    Box(
        Modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 12.dp.toPx()
        }
    ) {
        content()
    }
}
