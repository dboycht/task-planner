package com.dboycht.taskplanner.ui.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dboycht.taskplanner.model.Task
import com.dboycht.taskplanner.model.TaskFilter
import com.dboycht.taskplanner.model.TaskGroup
import com.dboycht.taskplanner.model.TaskStatus
import com.dboycht.taskplanner.model.priorityLabel
import com.dboycht.taskplanner.ui.about.AboutDialog
import com.dboycht.taskplanner.ui.about.appDisplayName
import com.dboycht.taskplanner.ui.anim.Appear
import com.dboycht.taskplanner.ui.anim.EASE
import com.dboycht.taskplanner.ui.anim.rememberAnimationsEnabled
import com.dboycht.taskplanner.ui.celebrate.CelebrationOverlay
import com.dboycht.taskplanner.ui.theme.priorityColor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    onEdit: (Long?) -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val vm: TaskListViewModel = viewModel()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val allTags by vm.allTags.collectAsStateWithLifecycle()
    val totalCount by vm.totalCount.collectAsStateWithLifecycle()
    val uncompletedCount by vm.uncompletedCount.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<Task?>(null) }
    var doneExpanded by rememberSaveable { mutableStateOf(false) }
    var celebrationTrigger by remember { mutableStateOf(0) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val animOn = rememberAnimationsEnabled()
    // 标题取应用自身的 label（与桌面图标同名），测试版因此也显示「任务计划·测试版」。
    val context = LocalContext.current
    val appLabel = remember(context) { appDisplayName(context) }

    Box {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (searching) {
                            SearchField(
                                query = filter.query,
                                onQuery = { vm.setFilter(filter.copy(query = it)) },
                                onClose = {
                                    searching = false
                                    vm.setFilter(filter.copy(query = ""))
                                },
                            )
                        } else {
                            Text(appLabel)
                        }
                    },
                    actions = {
                        if (!searching) {
                            IconButton(onClick = {
                                searching = true
                                showFilters = false
                            }) {
                                Icon(Icons.Filled.Search, contentDescription = "搜索")
                            }
                        }
                        TextButton(onClick = { showFilters = !showFilters }) {
                            Text(if (showFilters || filter.isActive) "筛选·" else "筛选")
                        }
                        TextButton(onClick = { showAbout = true }) { Text("关于") }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "设置")
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { onEdit(null) }) {
                    Text("＋", style = MaterialTheme.typography.titleLarge)
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                val filterVisible = showFilters || filter.isActive
                if (filterVisible) {
                    FilterBar(
                        filter = filter,
                        allTags = allTags,
                        onChange = { vm.setFilter(it) },
                        onClear = {
                            vm.setFilter(TaskFilter())
                            showFilters = false
                        },
                    )
                }
                val total = groups.entries.sumOf { it.value.size }
                val allTasks = groups.values.flatten()
                if (totalCount == 0) {
                    ReminderPermissionCard()
                    CalendarPermissionCard(uncompletedCount)
                    BackstageWarningCard(emptyList())
                    EmptyList(Modifier.fillMaxSize(), animOn)
                } else if (total == 0) {
                    // 有任务但被筛选光 → 显示「无匹配」并给出清除入口。
                    NoMatchView(
                        modifier = Modifier.fillMaxSize(),
                        animOn = animOn,
                        onClear = { vm.setFilter(TaskFilter()) },
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 96.dp),
                    ) {
                        item(key = "reminder-permission") { ReminderPermissionCard() }
                        item(key = "calendar-permission") { CalendarPermissionCard(uncompletedCount) }
                        // 权限之外的第二道坑：ROM 的「耗电行为控制」（无 API 可查，靠闹钟是否真响过反推）。
                        item(key = "backstage-warning") { BackstageWarningCard(allTasks) }
                        var appearIndex = 0
                        groups.forEach { (group, tasks) ->
                            if (group == TaskGroup.DONE) {
                                // 已完成折叠区：头部常显（可点旋转箭头），内容展开/收起动画。
                                val headerIdx = appearIndex++
                                item(key = "header-${group.name}") {
                                    Appear(headerIdx, animOn) {
                                        DoneGroupHeader(
                                            count = tasks.size,
                                            expanded = doneExpanded,
                                            onClick = { doneExpanded = !doneExpanded },
                                        )
                                    }
                                }
                                item(key = "done-content") {
                                    AnimatedVisibility(
                                        visible = doneExpanded,
                                        enter = expandVertically(tween(260, easing = EASE)) +
                                            fadeIn(tween(220)),
                                        exit = shrinkVertically(tween(220, easing = EASE)) +
                                            fadeOut(tween(160)),
                                    ) {
                                        Column {
                                            tasks.forEach { task ->
                                                TaskRow(
                                                    task = task,
                                                    onClick = { onEdit(task.id) },
                                                    onToggle = { vm.toggleDone(task) },
                                                    onLongPress = { deleteTarget = task },
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                val headerIdx = appearIndex++
                                item(key = "header-${group.name}") {
                                    Appear(headerIdx, animOn) {
                                        GroupHeader("${group.label} · ${tasks.size}")
                                    }
                                }
                                val base = appearIndex
                                appearIndex += tasks.size
                                itemsIndexed(tasks, key = { _, task -> task.id }) { index, task ->
                                    Appear(base + index, animOn) {
                                        Box(
                                            Modifier.animateItem(
                                                fadeInSpec = tween(220),
                                                fadeOutSpec = tween(180),
                                            )
                                        ) {
                                            TaskRow(
                                                task = task,
                                                onClick = { onEdit(task.id) },
                                                onToggle = {
                                                    // 未完成 → 完成才庆祝；取消完成不庆祝。
                                                    if (task.status != TaskStatus.DONE) {
                                                        celebrationTrigger++
                                                    }
                                                    vm.toggleDone(task)
                                                },
                                                onLongPress = { deleteTarget = task },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        CelebrationOverlay(trigger = celebrationTrigger)
    }

    deleteTarget?.let { target ->
        DeleteTaskDialog(
            target = target,
            onDismiss = { deleteTarget = null },
            onDelete = { series ->
                vm.delete(target, series)
                deleteTarget = null
            },
        )
    }

    if (showAbout) AboutDialog(onDismiss = { showAbout = false })
}

/** 删除确认：重复任务区分「仅此次」与「整个系列」。 */
@Composable
private fun DeleteTaskDialog(
    target: Task,
    onDismiss: () -> Unit,
    onDelete: (deleteSeries: Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除任务") },
        text = {
            if (target.recurRule.isRecurring) {
                Text(
                    "「${target.title}」是重复任务（${target.recurRule.label()}）。\n" +
                        "「仅此次」删除这一条并让系列止于此；「整个系列」删除全部重复实例。"
                )
            } else {
                Text("确定删除「${target.title}」吗？该操作不可撤销。")
            }
        },
        confirmButton = {
            if (target.recurRule.isRecurring) {
                Row {
                    TextButton(onClick = { onDelete(false) }) { Text("仅此次") }
                    TextButton(onClick = { onDelete(true) }) { Text("整个系列") }
                }
            } else {
                TextButton(onClick = { onDelete(false) }) { Text("删除") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 顶栏内嵌搜索框：无边框、占位文案、右侧清空/退出。 */
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            "搜索标题 / 描述 / 标签",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    inner()
                }
            },
        )
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "关闭搜索")
        }
    }
}

/** 筛选条：状态 / 优先级两排单选 + 标签多选；有任一激活时末尾给「清除」。 */
@Composable
private fun FilterBar(
    filter: TaskFilter,
    allTags: List<String>,
    onChange: (TaskFilter) -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                FilterChip(
                    selected = filter.status == null,
                    onClick = { onChange(filter.copy(status = null)) },
                    label = { Text("全部状态") },
                )
            }
            TaskStatus.entries.forEach { s ->
                item {
                    FilterChip(
                        selected = filter.status == s,
                        onClick = {
                            onChange(filter.copy(status = if (filter.status == s) null else s))
                        },
                        label = { Text(s.label) },
                    )
                }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                FilterChip(
                    selected = filter.priorityBucket == null,
                    onClick = { onChange(filter.copy(priorityBucket = null)) },
                    label = { Text("全部优先级") },
                )
            }
            listOf(2 to "高", 1 to "中", 0 to "低").forEach { (bucket, label) ->
                item {
                    FilterChip(
                        selected = filter.priorityBucket == bucket,
                        onClick = {
                            onChange(
                                filter.copy(
                                    priorityBucket = if (filter.priorityBucket == bucket) null else bucket
                                )
                            )
                        },
                        label = { Text(label) },
                    )
                }
            }
        }
        if (allTags.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(allTags, key = { _, tag -> tag }) { _, tag ->
                    FilterChip(
                        selected = tag in filter.tags,
                        onClick = {
                            val next = filter.tags.toMutableSet()
                            if (tag in next) next.remove(tag) else next.add(tag)
                            onChange(filter.copy(tags = next))
                        },
                        label = { Text(tag) },
                    )
                }
            }
        }
        if (filter.isActive) {
            TextButton(onClick = onClear) { Text("清除全部筛选") }
        }
    }
}

@Composable
private fun EmptyList(modifier: Modifier = Modifier, animOn: Boolean) {
    // 空态 emoji 呼吸浮动（缓入缓出往返 6dp）
    val floatDy by rememberInfiniteTransition(label = "empty-float").animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(
            tween(1600, easing = EASE),
            RepeatMode.Reverse,
        ),
        label = "dy",
    )
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "📝",
                style = MaterialTheme.typography.displaySmall,
                modifier = if (animOn) {
                    Modifier.graphicsLayer { translationY = floatDy.dp.toPx() }
                } else Modifier,
            )
            Spacer(Modifier.height(8.dp))
            Appear(1, animOn) { Text("还没有任务", style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(4.dp))
            Appear(2, animOn) {
                Text(
                    "点右下角「＋」添加第一个任务",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 有任务但被筛光：放大镜 + 清除筛选入口。 */
@Composable
private fun NoMatchView(modifier: Modifier = Modifier, animOn: Boolean, onClear: () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("🔍", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Appear(1, animOn) { Text("没有匹配的任务", style = MaterialTheme.typography.titleMedium) }
            Spacer(Modifier.height(4.dp))
            Appear(2, animOn) {
                Text(
                    "换个关键词，或清除筛选条件",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Appear(3, animOn) {
                TextButton(onClick = onClear) { Text("清除全部筛选") }
            }
        }
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** 可折叠的「已完成项目」头部：数量常显，箭头随展开状态旋转 90°。 */
@Composable
private fun DoneGroupHeader(count: Int, expanded: Boolean, onClick: () -> Unit) {
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(200, easing = EASE),
        label = "arrow",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "已完成项目 · $count",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "▸",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.rotate(arrowRotation),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskRow(
    task: Task,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
) {
    val done = task.status == TaskStatus.DONE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleCircle(done = done, onClick = onToggle)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            timeLineText(task)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PriorityChip(task.priority)
                if (task.recurRule.isRecurring) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "· ↻ ${task.recurRule.label()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (task.status == TaskStatus.DOING) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "· ${task.status.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                task.tags.forEach { tag ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "· $tag",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleCircle(done: Boolean, onClick: () -> Unit) {
    val borderColor =
        if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    // 勾选 ✓ 弹性放大进场（取消勾选时弹缩退场）
    val checkScale by animateFloatAsState(
        targetValue = if (done) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "check",
    )
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .border(1.5.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checkScale > 0.01f) {
            Text(
                "✓",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer {
                    scaleX = checkScale
                    scaleY = checkScale
                },
            )
        }
    }
}

@Composable
private fun PriorityChip(priority: Int) {
    val color = priorityColor(priority)
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.15f),
        contentColor = color,
    ) {
        Text(
            priorityLabel(priority),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private val timeFmt = DateTimeFormatter.ofPattern("MM-dd HH:mm")

/** 副标题时间行；无任何时间返回 null。已完成任务显示完成时间。 */
internal fun timeLineText(task: Task): String? {
    val zone = ZoneId.systemDefault()
    if (task.status == TaskStatus.DONE) {
        val at = task.completedAt ?: task.updatedAt
        return if (at > 0L) "完成于 ${timeFmt.format(Instant.ofEpochMilli(at).atZone(zone))}" else null
    }
    val start = task.startTime?.let { timeFmt.format(Instant.ofEpochMilli(it).atZone(zone)) }
    val due = task.dueTime?.let { timeFmt.format(Instant.ofEpochMilli(it).atZone(zone)) }
    return when {
        start != null && due != null -> "$start ~ $due"
        due != null -> "截止 $due"
        start != null -> "开始 $start"
        else -> null
    }
}
