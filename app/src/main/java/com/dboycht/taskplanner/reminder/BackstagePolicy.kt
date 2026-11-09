package com.dboycht.taskplanner.reminder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 厂商「耗电行为控制 / 自启动管理」这一层限制的**可读信号**与引导文案。
 *
 * 背景（2026-11-03 真机实测，见 ERROR.md E5）：ColorOS 把应用设成
 * **「智能优化后台运行」** 时，系统的 [android.app.AlarmManager] 会给我们精确闹钟加上
 * `adjustment = +3d`（`dumpsys alarm` 里可见）—— 于是**当天/次日的提醒永远不响**，
 * 只有 4 天以后的才正常。这不是权限问题：`SCHEDULE_EXACT_ALARM: allow`、
 * `POST_NOTIFICATIONS: granted` 全都给了，横幅也不会出现，用户完全无从察觉。
 *
 * ⚠️ 该状态**没有公开的系统 API 可查询**（`cmd appops get` 里没有对应项，
 * `AppOpsManager` 也没有），所以本对象只负责**引导**，判定交给
 * [suspectMissedReminder]（依据「闹钟是否真的按时响过」）。
 */

/** 命中这些品牌才提示，避免在不会改写闹钟的 ROM 上打扰用户。 */
private val VENDOR_MANAGED_MARKERS = listOf("oppo", "oneplus", "realme", "oplus")

/**
 * 是否属于会做「后台行为控制」的厂商。参数化品牌/厂商名，便于纯 JVM 单测
 * （`android.os.Build` 在单测里是 null 桩，不能直接读）。
 */
fun isVendorManagedRom(brand: String?, manufacturer: String?): Boolean {
    val b = brand.orEmpty().lowercase()
    val m = manufacturer.orEmpty().lowercase()
    return VENDOR_MANAGED_MARKERS.any { b.contains(it) || m.contains(it) }
}

/** 真机环境下的便捷重载。 */
fun isVendorManagedRom(): Boolean = isVendorManagedRom(Build.BRAND, Build.MANUFACTURER)

/** 引导文案（与 UI 解耦，便于单测）。 */
fun backstageGuidanceTitle(): String = "提醒可能不会按时响"

fun backstageGuidanceBody(): String =
    "系统把本应用的后台运行设成了「智能优化」，会把当天和次日的提醒推迟好几天。" +
        "请在「耗电管理」里改成「完全允许后台行为」，并在「自启动」里允许本应用。"

/** 真机上那个入口的名字（OTA 可能微调，文案仅作指路）。 */
fun backstageSettingEntryLabel(): String = "耗电管理"

/**
 * 依次尝试：ColorOS 耗电管理页 → 应用详情页（所有 ROM 都有，作为兜底落点）。
 * 返回是否成功拉起。Intent 构造依赖 Android，故不参与单测。
 */
fun openBackstageSettings(context: Context): Boolean {
    val pkg = context.packageName
    val deepLink = Intent().apply {
        setClassName("com.oplus.battery", "com.oplus.battery.highpower.HighPowerActivity")
        putExtra("pkg_name", pkg)
        putExtra("package_name", pkg)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val appDetails = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:$pkg"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    for (intent in listOf(deepLink, appDetails)) {
        if (runCatching { context.startActivity(intent) }.isSuccess) return true
    }
    return false
}

/**
 * 提醒健康度：用来决定是否向用户出示「提醒可能不响」的引导。
 *
 * 判据刻意保守 —— **拿不到问题证据就不打扰**。三个必要条件缺一不可：
 * - **我们真的排过闹钟**（[expectedTriggerAt] 非空）—— 任务上存在过去的提醒点，
 *   不等于我们为它排过闹钟（调度层每条任务只排「下一个未到点的提醒」），
 *   否则一条早该过期、一直没勾完成的旧任务会让横幅永久亮着（误报）；
 * - 那条排过的闹钟**已经到点**且过了宽限期；
 * - 到点之后**仍没有**「已响」记录。
 *
 * 「已响」记录由 [ReminderHealth] 在真正发出通知后写入。
 */
data class ReminderHealthSnapshot(
    /** 当前所有未完成任务展开出的提醒时刻（毫秒，可乱序）。 */
    val dueTriggerTimes: List<Long>,
    /** 本应用最后一次真正发出提醒通知的时刻；null = 从未响过。 */
    val lastDeliveredAt: Long?,
    val now: Long,
    /**
     * 本应用**最后一次真正排进系统的闹钟时刻**；null = 从未排过（新装/清数据）。
     * 只有它才可能「到点没响」，任务上的历史提醒点不算数。
     */
    val expectedTriggerAt: Long? = null,
)

/** 宽限：闹钟有几十秒误差、节电模式下也会略晚，留 10 分钟。 */
const val REMINDER_GRACE_MS: Long = 10 * 60_000L

/**
 * 只在「刚过去不久」的窗口内追究漏提醒。
 *
 * 为什么需要窗口：`expectedTriggerAt` 是**上一次排程的时刻**，如果应用很久没被打开、
 * 或那条闹钟是被用户手动取消的，它会一直停在过去。没有窗口的话，一次陈年漏响会让
 * 横幅从此常亮 —— 而用户要的是「现在提醒还灵不灵」。超过窗口就当作**无从取证**，
 * 不提示（保守优先）。
 */
const val REMINDER_LOOKBACK_MS: Long = 48 * 60 * 60_000L

/**
 * 若怀疑「最近一次排过的提醒被系统压制」，返回那个提醒时刻；否则 null。
 *
 * 只看**我们确实排过的那一条**（[ReminderHealthSnapshot.expectedTriggerAt]），
 * 并且要求它落在 [lookbackMs] 窗口内：更早的排程已被后续排程取代，
 * 更谈不上「系统的锅」。
 */
fun suspectMissedReminder(
    snapshot: ReminderHealthSnapshot,
    graceMs: Long = REMINDER_GRACE_MS,
    lookbackMs: Long = REMINDER_LOOKBACK_MS,
): Long? {
    // 没排过闹钟 ⇒ 没有可追究的对象（新装、清数据、全部任务已完成）。
    val expected = snapshot.expectedTriggerAt ?: return null
    // 还没到点 → 没到该响的时候。
    if (expected > snapshot.now) return null
    // 到点但还在宽限期内 → 再等等。
    if (snapshot.now - expected < graceMs) return null
    // 太久以前的事 → 无从取证，不打扰。
    if (snapshot.now - expected > lookbackMs) return null
    // 响过，且是这次排程之后响的 ⇒ 正常，不提示。
    val delivered = snapshot.lastDeliveredAt
    if (delivered != null && delivered >= expected) return null
    return expected
}
