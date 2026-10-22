package com.lumalink.g100d.transport.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumalink.g100d.core.data.TransferState
import com.lumalink.g100d.core.util.formatBytes
import com.lumalink.g100d.core.util.formatEta
import com.lumalink.g100d.core.util.formatSpeed
import com.lumalink.g100d.ui.component.EmptyState
import com.lumalink.g100d.ui.component.InfoBanner
import com.lumalink.g100d.ui.component.LumaCard
import com.lumalink.g100d.ui.component.SectionHeader
import com.lumalink.g100d.ui.component.StatTile
import com.lumalink.g100d.ui.component.StatusPill
import com.lumalink.g100d.ui.component.StatusTone

private enum class TransferTab(val label: String) {
    Active("进行中"),
    Done("已完成"),
    Failed("失败"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersScreen(
    viewModel: TransfersViewModel,
    onOpenConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(TransferTab.Active) }
    var deleteTarget by remember { mutableStateOf<TransferItem?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var batchDeleteIds by remember { mutableStateOf<List<String>?>(null) }

    val filtered = ui.items.filter { item ->
        when (tab) {
            TransferTab.Active -> item.state == TransferState.QUEUED ||
                item.state == TransferState.RUNNING

            TransferTab.Done -> item.state == TransferState.COMPLETED
            TransferTab.Failed -> item.state == TransferState.FAILED ||
                item.state == TransferState.CANCELED
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    value = ui.activeCount.toString(),
                    label = "进行中",
                    icon = Icons.Outlined.SwapVert,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = formatSpeed(ui.currentSpeed),
                    label = "当前速度",
                    icon = Icons.Outlined.Speed,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    value = ui.completedToday.toString(),
                    label = "今日完成",
                    icon = Icons.Outlined.CheckCircle,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TransferTab.entries.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = tab == item,
                        onClick = {
                            tab = item
                            selectionMode = false
                            selectedIds = emptySet()
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, TransferTab.entries.size),
                        label = { Text(item.label) },
                    )
                }
            }
        }

        if (tab != TransferTab.Active && filtered.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            selectionMode = !selectionMode
                            selectedIds = emptySet()
                        },
                    ) {
                        Text(if (selectionMode) "取消批量选择" else "批量选择")
                    }
                    Spacer(Modifier.weight(1f))
                    if (selectionMode) {
                        TextButton(
                            onClick = {
                                val all = filtered.map { it.id }.toSet()
                                selectedIds = if (selectedIds.size == all.size) emptySet() else all
                            },
                        ) {
                            Text(if (selectedIds.size == filtered.size) "取消全选" else "全选")
                        }
                        TextButton(
                            onClick = {
                                if (selectedIds.isNotEmpty()) batchDeleteIds = selectedIds.toList()
                            },
                            enabled = selectedIds.isNotEmpty(),
                        ) {
                            Text("删除选中")
                        }
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            item {
                LumaCard(contentPadding = PaddingValues(0.dp)) {
                    val empty = emptyStateFor(tab)
                    EmptyState(
                        icon = empty.icon,
                        title = empty.title,
                        description = empty.description,
                        action = {
                            Button(onClick = onOpenConnect) { Text("去连接页选择文件") }
                        },
                    )
                }
            }
        } else {
            items(filtered, key = { it.id }) { item ->
                TransferCard(
                    item = item,
                    onCancel = { viewModel.cancel(item.id) },
                    onRetry = { viewModel.retry(item.id) },
                    onDelete = { deleteTarget = item },
                    selectionMode = selectionMode,
                    selected = item.id in selectedIds,
                    onToggleSelection = {
                        selectedIds = if (item.id in selectedIds) {
                            selectedIds - item.id
                        } else {
                            selectedIds + item.id
                        }
                    },
                )
            }

            if (tab != TransferTab.Active) {
                item {
                    TextButton(onClick = viewModel::clearFinished) {
                        Text("清理已完成/已取消")
                    }
                }
            }
        }

        item {
            SectionHeader("传输策略")
            Spacer(Modifier.height(8.dp))
            LumaCard {
                StrategyRow(
                    title = "顺序传输",
                    subtitle = "单文件单连接；2 路并行实测没有提升吞吐，已移除。",
                    value = "1",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                StrategyRow(
                    title = "完整性校验",
                    subtitle = "字节数 + JPEG/MP4/RW2 结构 + 本地 SHA-256；相机没有官方哈希。",
                    value = "开启",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                StrategyRow(
                    title = "断点续传与重试",
                    subtitle = "断连保留 .part，自动重试 2 次；支持 Range 时从断点续传，相机忽略 Range 则自动从头重下。",
                    value = "开启",
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                StrategyRow(
                    title = "后台传输",
                    subtitle = "前台服务 + Wi-FiLock + Partial WakeLock，通知栏可取消。",
                    value = "前台服务",
                )
            }
        }

        item {
            InfoBanner(
                title = "保存位置",
                body = "JPEG 保存到 Pictures/LumaLink，视频保存到 Movies/LumaLink，RAW 保存到 Downloads/LumaLink/RAW。媒体库写入失败时会回退到应用专属目录并记录路径。",
            )
        }

        item { Spacer(Modifier.height(12.dp)) }
    }

    batchDeleteIds?.let { ids ->
        AlertDialog(
            onDismissRequest = { batchDeleteIds = null },
            title = { Text("删除选中的 ${ids.size} 条记录？") },
            text = { Text("可以只删除记录，或同时删除这些记录对应的本地文件。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMany(ids, deleteLocalFile = true)
                        batchDeleteIds = null
                        selectionMode = false
                        selectedIds = emptySet()
                    },
                ) {
                    Text("删除记录和本地文件")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            viewModel.deleteMany(ids, deleteLocalFile = false)
                            batchDeleteIds = null
                            selectionMode = false
                            selectedIds = emptySet()
                        },
                    ) {
                        Text("仅删除记录")
                    }
                    TextButton(onClick = { batchDeleteIds = null }) { Text("取消") }
                }
            },
        )
    }

    deleteTarget?.let { target ->
        val hasLocalFile = target.localUri != null
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这条传输记录？") },
            text = {
                Text(
                    if (hasLocalFile) {
                        "可以只删除记录，或同时删除已保存的本地文件。"
                    } else {
                        "这条记录没有本地文件，只会删除记录。"
                    },
                )
            },
            confirmButton = {
                if (hasLocalFile) {
                    TextButton(
                        onClick = {
                            viewModel.delete(target.id, deleteLocalFile = true)
                            deleteTarget = null
                        },
                    ) {
                        Text("删除记录和本地文件")
                    }
                } else {
                    TextButton(
                        onClick = {
                            viewModel.delete(target.id, deleteLocalFile = false)
                            deleteTarget = null
                        },
                    ) {
                        Text("删除记录")
                    }
                }
            },
            dismissButton = {
                if (hasLocalFile) {
                    TextButton(
                        onClick = {
                            viewModel.delete(target.id, deleteLocalFile = false)
                            deleteTarget = null
                        },
                    ) {
                        Text("仅删除记录")
                    }
                } else {
                    TextButton(onClick = { deleteTarget = null }) { Text("取消") }
                }
            },
        )
    }
}

@Composable
private fun TransferCard(
    item: TransferItem,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelection: () -> Unit,
) {
    LumaCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onToggleSelection() })
                Spacer(Modifier.width(4.dp))
            }
            Icon(
                imageVector = iconFor(item.mimeType),
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${stateLabel(item.state)} · ${formatBytes(item.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(text = stateLabel(item.state), tone = toneFor(item.state))
        }

        if (item.state == TransferState.RUNNING || item.state == TransferState.QUEUED) {
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { item.progress ?: 0f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${formatBytes(item.bytesDownloaded)} / ${formatBytes(item.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = buildString {
                        append(formatSpeed(item.speedBytesPerSecond))
                        item.etaSeconds?.let { append(" · 剩余 ${formatEta(it)}") }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (item.errorMessage != null && item.state == TransferState.FAILED) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (item.state == TransferState.COMPLETED && item.localUri != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "已保存",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (item.state == TransferState.RUNNING || item.state == TransferState.QUEUED) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel) { Text("取消") }
            }
        }

        if (item.state == TransferState.FAILED || item.state == TransferState.CANCELED) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDelete) { Text("删除记录") }
                TextButton(onClick = onRetry) { Text("重试") }
            }
        }

        if (item.state == TransferState.COMPLETED) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDelete) { Text("删除记录") }
            }
        }
    }
}

private data class EmptyCopy(
    val icon: ImageVector,
    val title: String,
    val description: String,
)

private fun emptyStateFor(tab: TransferTab): EmptyCopy = when (tab) {
    TransferTab.Active -> EmptyCopy(
        icon = Icons.Outlined.CloudDownload,
        title = "暂无传输任务",
        description = "连接相机后，在相机文件列表中选择照片、RAW 或视频，任务会出现在这里。",
    )

    TransferTab.Done -> EmptyCopy(
        icon = Icons.Outlined.CheckCircle,
        title = "还没有完成的传输",
        description = "完成的文件会保存到系统媒体库，并记录 SHA-256 与完整性校验结果。",
    )

    TransferTab.Failed -> EmptyCopy(
        icon = Icons.Outlined.ErrorOutline,
        title = "没有失败的任务",
        description = "失败任务会保留原因与重试入口，不会静默丢弃。",
    )
}

@Composable
private fun StrategyRow(
    title: String,
    subtitle: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun iconFor(mimeType: String): ImageVector = when {
    mimeType.startsWith("image/") -> Icons.Outlined.Image
    mimeType.startsWith("video/") -> Icons.Outlined.Movie
    else -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

private fun stateLabel(state: TransferState): String = when (state) {
    TransferState.QUEUED -> "等待中"
    TransferState.RUNNING -> "传输中"
    TransferState.COMPLETED -> "已完成"
    TransferState.FAILED -> "失败"
    TransferState.CANCELED -> "已取消"
}

private fun toneFor(state: TransferState): StatusTone = when (state) {
    TransferState.QUEUED -> StatusTone.Warning
    TransferState.RUNNING -> StatusTone.Accent
    TransferState.COMPLETED -> StatusTone.Success
    TransferState.FAILED -> StatusTone.Error
    TransferState.CANCELED -> StatusTone.Neutral
}
