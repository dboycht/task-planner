package com.dboycht.taskplanner.ui.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.delay

/**
 * 关于页。版本信息全部从已安装的 APK 读（见 [readAboutInfo]），源码里不抄第二份版本号。
 * 测试版徽标由 `FLAG_DEBUGGABLE` 判定，与桌面名称（debug 变体覆盖 `app_name`）一致。
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val info = remember(context) { readAboutInfo(context) }

    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info.label, fontWeight = FontWeight.SemiBold)
                if (info.isTestBuild()) {
                    Surface(
                        modifier = Modifier.padding(start = 8.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ) {
                        Text(
                            "测试版",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("版本 ${info.versionName}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    info.subtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider()

                Text(
                    "提醒由本应用自己的通知通道发出：按任务的提醒策略挂精确闹钟，到点弹通知，" +
                        "通知上可直接「标记完成」。任务只存在本机数据库，不联网、不上传。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "提示：手机重启后若提醒没恢复，打开一次本应用即可重新排上" +
                        "（部分系统会延后第三方应用的开机广播）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    // 仓库里还没有 LICENSE 文件（README 记为「定版时确定」），这里不声明具体许可。
                    "作者 dboycht · 个人项目",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(
                        ClipData.newPlainText("版本信息", info.clipboardText())
                    )
                    copied = true
                },
            ) { Text(if (copied) "已复制" else "复制版本信息") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

/** 读已安装 APK 的版本信息；任何一项读不到都用占位文案，绝不因为读失败而崩。 */
fun readAboutInfo(context: Context): AboutInfo {
    val pm = context.packageManager
    val packageInfo = runCatching { pm.getPackageInfo(context.packageName, 0) }.getOrNull()
    return AboutInfo(
        label = appDisplayName(context),
        versionName = packageInfo?.versionName ?: "未知",
        versionCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L,
        packageName = context.packageName,
        debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
    )
}

/** 应用自身的显示名（桌面图标上的那个）；给顶栏等处复用，读不到时回落固定文案。 */
fun appDisplayName(context: Context): String = runCatching {
    context.applicationInfo.loadLabel(context.packageManager).toString()
}.getOrDefault("任务计划")
