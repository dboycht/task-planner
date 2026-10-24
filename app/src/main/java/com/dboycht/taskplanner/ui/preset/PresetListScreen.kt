package com.dboycht.taskplanner.ui.preset

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dboycht.taskplanner.model.summary

/** 预设管理：内置预设只读展示，自建预设点击进入编辑。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetListScreen(
    onBack: () -> Unit,
    onEdit: (Long?) -> Unit,
) {
    val vm: PresetListViewModel = viewModel()
    val groups by vm.groups.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("提醒预设") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                },
                actions = {
                    TextButton(onClick = { onEdit(null) }) { Text("＋ 新建") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (groups.builtIn.isNotEmpty()) {
                item { SectionHeader("内置预设") }
                items(groups.builtIn.size) { index ->
                    val preset = groups.builtIn[index]
                    PresetRow(
                        title = preset.name,
                        subtitle = preset.policy.summary(),
                        badge = "内置",
                        onClick = null,
                    )
                }
            }
            val customHeader = if (groups.custom.isEmpty()) "我的预设"
            else "我的预设 · ${groups.custom.size}"
            item { SectionHeader(customHeader) }
            if (groups.custom.isEmpty()) {
                item {
                    Text(
                        "还没有自定义预设，点右上角「＋ 新建」创建。\n例如：预设 A = 截止前 7 天、3 天、1 小时各提醒一次。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(groups.custom.size) { index ->
                    val preset = groups.custom[index]
                    PresetRow(
                        title = preset.name,
                        subtitle = preset.policy.summary(),
                        badge = null,
                        onClick = { onEdit(preset.id) },
                    )
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun PresetRow(
    title: String,
    subtitle: String,
    badge: String?,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val hint = if (onClick != null) "编辑 ›" else badge
        if (hint != null) {
            Text(
                hint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
