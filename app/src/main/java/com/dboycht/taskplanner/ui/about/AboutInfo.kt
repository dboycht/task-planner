package com.dboycht.taskplanner.ui.about

/**
 * 关于页要展示的版本信息。全部字段来自**已安装的 APK**（PackageManager），
 * 源码里不留第二份版本数字 —— `versionName` 的单一来源是 `app/build.gradle.kts`。
 */
data class AboutInfo(
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val packageName: String,
    val debuggable: Boolean,
)

/** 「测试版」徽标是否显示：只有 debuggable 构建才算测试版。 */
fun AboutInfo.isTestBuild(): Boolean = debuggable

/**
 * 「复制版本信息」写进剪贴板的文本。用户反馈问题时贴这一段就够定位版本。
 * 纯函数，便于单测（不依赖 Android 运行时）。
 */
fun AboutInfo.clipboardText(): String = buildString {
    append(label)
    append('\n')
    append("版本 $versionName（versionCode $versionCode）")
    append('\n')
    append("包名 $packageName")
    append('\n')
    append(if (isTestBuild()) "类型 测试版（debug 构建）" else "类型 正式版")
    append('\n')
    append(if (isTestBuild()) {
        "分发 仅用于开发验收，数据与正式版互不相通"
    } else {
        "分发 正式版"
    })
}

/** 副标题：「versionCode N · 包名 …」。 */
fun AboutInfo.subtitle(): String = "versionCode $versionCode · 包名 $packageName"
