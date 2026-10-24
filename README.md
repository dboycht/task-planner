# task-planner

Android 任务计划应用：添加任务并指定开始时间、截止日期、优先级等，保存后**自动写入系统日历日程**，并按策略**自动挂提醒**（如「截止前 1 小时」），到期通过系统通知提醒。

> 状态：开发中（v1.0.1，未发布）。Kotlin + Jetpack Compose 原生实现。

## 功能特性（v1 规划）

- **任务管理**：标题、描述、开始时间（可选）、截止时间、优先级（高 / 中 / 低）、状态（待办 / 进行中 / 完成）、标签分类
- **列表视图**：今日 / 近期 / 已逾期 / 已完成 分组，按截止时间 + 优先级排序
- **日历集成**：任务创建、修改、删除后自动同步到系统日历（默认在本机创建专属日历「Task Planner」，不污染用户主日历）
- **自动提醒**：每任务可配置提醒策略，默认「截止前 1 小时」，可选开始时间提醒、截止前 1 天 / 2 小时 / 30 分钟等；日历提醒 + 应用自有精确闹钟**双通道**，任一生效即可收到
- **状态流转**：截止时间已过且未完成 → 自动标记为已逾期

## 技术栈

- Kotlin + Jetpack Compose（Material 3），MVVM
- Room 数据库（本地单一事实源）
- Calendar Provider（日历同步）
- AlarmManager + BroadcastReceiver + 通知渠道（自有提醒通道，开机自启重排）
- minSdk 26（Android 8.0）/ 单元测试 JUnit

## 构建

```bash
# 需要 JDK 21 与 Android SDK；与 keyforge 同一套本机构建链
gradle assembleDebug   # 或在 Android Studio 中打开
```

## 版本

版本号单一来源：`app/build.gradle.kts` 的 `versionName`。当前：**1.0.1（开发中）**。

## 许可

待定版时确定（默认 MIT）。
