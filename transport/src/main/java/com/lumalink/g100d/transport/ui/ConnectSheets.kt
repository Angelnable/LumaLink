package com.lumalink.g100d.transport.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.ui.unit.dp
import com.lumalink.g100d.core.util.formatBytes
import com.lumalink.g100d.protocol.CameraFile
import com.lumalink.g100d.protocol.MediaKind
import com.lumalink.g100d.transport.session.CameraConnectionState
import com.lumalink.g100d.transport.session.WifiCandidate
import com.lumalink.g100d.ui.component.EmptyState
import com.lumalink.g100d.ui.component.InfoBanner
import com.lumalink.g100d.ui.component.StatusPill
import com.lumalink.g100d.ui.component.StatusTone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectSetupSheet(
    ui: ConnectUiState,
    connection: CameraConnectionState,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onBaseUrlChange: (String) -> Unit,
    onScan: () -> Unit,
    onConnect: () -> Unit,
    onUseCurrentWifi: () -> Unit,
    onDismiss: () -> Unit,
) {
    val busy = connection is CameraConnectionState.Connecting ||
        connection is CameraConnectionState.Handshaking
    val error = when (connection) {
        is CameraConnectionState.Error -> connection.message
        else -> ui.permissionError
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("连接相机", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "相机需处于 Wi-Fi 待连接状态。连接时系统会弹出 Wi-Fi 确认，请选择允许。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(18.dp))

            OutlinedTextField(
                value = ui.ssid,
                onValueChange = onSsidChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("相机 SSID") },
                placeholder = { Text("相机屏幕显示的 LUMIX-…") },
                singleLine = true,
                enabled = !busy,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onScan, enabled = !busy) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("扫描附近")
                }
                if (ui.scanning) {
                    Spacer(Modifier.width(14.dp))
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }

            if (ui.candidates.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "附近网络",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                ui.candidates.take(6).forEach { candidate ->
                    WifiCandidateRow(candidate = candidate, onSelect = { onSsidChange(candidate.ssid) })
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = ui.password,
                onValueChange = onPasswordChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Wi-Fi 密码（无密码留空）") },
                singleLine = true,
                enabled = !busy,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = ui.baseUrl,
                onValueChange = onBaseUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("相机地址") },
                placeholder = { Text("192.168.54.1") },
                singleLine = true,
                enabled = !busy,
                supportingText = { Text("直连默认 http://192.168.54.1；网络模式填相机局域网 IP。") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )

            Spacer(Modifier.height(14.dp))
            InfoBanner(
                title = "只读握手",
                body = "连接后只读取状态和文件列表，不修改相机设置，也不发送拍照/录像/监看命令。",
            )

            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (busy) {
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when (connection) {
                        is CameraConnectionState.Connecting -> "正在请求系统加入相机热点…"
                        is CameraConnectionState.Handshaking -> "已加入热点，正在读取相机状态…"
                        else -> "连接中…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onConnect,
                enabled = ui.ssid.isNotBlank() && !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(if (busy) "连接中…" else "连接相机")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onUseCurrentWifi,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Wifi,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("我已手动加入相机 Wi-Fi")
            }
            Text(
                text = "适用：系统 Wi-Fi 弹窗没有出现或连接失败时，先在系统设置加入相机热点，再点此按钮。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            androidx.compose.material3.TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("取消")
            }
        }
    }
}

@Composable
private fun WifiCandidateRow(candidate: WifiCandidate, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onSelect)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Wifi,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.ssid,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(if (candidate.secured) "加密网络" else "开放网络")
                    append(if (candidate.is5Ghz) " · 5GHz" else " · 2.4GHz")
                    if (candidate.isWifi5) append(" · Wi-Fi 5")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (candidate.isLikelyCamera) {
            StatusPill(text = "相机", tone = StatusTone.Accent)
            Spacer(Modifier.width(6.dp))
        }
        Icon(
            imageVector = if (candidate.secured) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraFilesSheet(
    ui: ConnectUiState,
    connected: Boolean,
    onOpenDirectory: (String) -> Unit,
    onNavigateUp: () -> Unit,
    onRefresh: () -> Unit,
    onToggle: (String) -> Unit,
    onDownload: () -> Unit,
    onFilterChange: (FileFilter) -> Unit,
    onPreviewEnabledChange: (Boolean) -> Unit,
    onPreviewAll: (List<CameraFile>) -> Unit,
    onSelectAll: (List<CameraFile>) -> Unit,
    onViewModeChange: (FileViewMode) -> Unit,
    onPreview: (CameraFile) -> Unit,
    onRequestThumbnail: (CameraFile) -> Unit,
    onPreviousPreview: () -> Unit,
    onNextPreview: () -> Unit,
    onClosePreview: () -> Unit,
    onDismiss: () -> Unit,
) {
    // In-content full page, not a Dialog: the app's bottom navigation must
    // remain visible and tappable while the file list is open. A Dialog would
    // cover the nav bar and was the cause of "bottom navigation blocked".
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        val fileCount = ui.files.count { !it.isDirectory }
        val visibleFiles = ui.files.filter { file ->
            file.isDirectory || ui.fileFilter.accepts(file)
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ui.currentDirectory != "/DCIM") {
                        IconButton(onClick = onNavigateUp) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = "上一级",
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("相机文件", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = buildString {
                                append(ui.currentDirectory)
                                append(" · 已读取 ")
                                append(ui.files.size)
                                append(" 项")
                                if (fileCount != ui.files.size) {
                                    append("（")
                                    append(fileCount)
                                    append(" 个文件）")
                                }
                                if (ui.fileFilter != FileFilter.ALL) {
                                    append(" · 当前显示 ")
                                    append(visibleFiles.size)
                                    append(" 项")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(
                        onClick = {
                            onViewModeChange(
                                if (ui.viewMode == FileViewMode.GRID) {
                                    FileViewMode.LIST
                                } else {
                                    FileViewMode.GRID
                                },
                            )
                        },
                    ) {
                        Icon(
                            imageVector = if (ui.viewMode == FileViewMode.GRID) {
                                Icons.AutoMirrored.Outlined.ViewList
                            } else {
                                Icons.Outlined.GridView
                            },
                            contentDescription = if (ui.viewMode == FileViewMode.GRID) {
                                "切换到列表"
                            } else {
                                "切换到网格"
                            },
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(imageVector = Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Outlined.Close, contentDescription = "关闭")
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FileFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = ui.fileFilter == filter,
                            onClick = { onFilterChange(filter) },
                            label = { Text(filter.label) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "预览",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = ui.previewEnabled,
                        onCheckedChange = onPreviewEnabledChange,
                        enabled = !ui.filesLoading && ui.files.isNotEmpty(),
                    )
                    Spacer(Modifier.weight(1f))
                    if (ui.previewEnabled) {
                        TextButton(
                            onClick = { onPreviewAll(visibleFiles) },
                            enabled = visibleFiles.any { !it.isDirectory },
                        ) {
                            Text("全部预览")
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))

            if (ui.filesLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
            }

            if (!connected) {
                InfoBanner(
                    title = "相机未连接",
                    body = "请先连接相机，再浏览相机文件。",
                    tone = StatusTone.Warning,
                )
            } else if (ui.filesError != null) {
                InfoBanner(
                    title = "读取失败",
                    body = ui.filesError,
                    tone = StatusTone.Error,
                )
            }

            if (connected && ui.files.isEmpty() && !ui.filesLoading) {
                InfoBanner(
                    title = "需要重新读取相机",
                    body = "如果列表为空或读取失败，点击右上角刷新按钮。App 会自动重新请求访问、切换播放模式并读取。",
                    tone = StatusTone.Warning,
                )
                Spacer(Modifier.height(8.dp))
            }

            if (visibleFiles.isEmpty() && !ui.filesLoading) {
                EmptyState(
                    icon = Icons.Outlined.Folder,
                    title = if (ui.fileFilter == FileFilter.ALL) {
                        "这个目录没有文件"
                    } else {
                        "没有符合筛选的文件"
                    },
                    description = if (ui.fileFilter == FileFilter.ALL) {
                        "点击右上角刷新；仍为空时请确认相机处于播放模式并已插入 SD 卡。"
                    } else {
                        "当前只显示${ui.fileFilter.label}。点“全部”查看所有文件。"
                    },
                )
            } else if (ui.viewMode == FileViewMode.GRID) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(112.dp),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    gridItems(visibleFiles, key = { it.path }) { file ->
                        CameraFileGridItem(
                            file = file,
                            selected = file.path in ui.selectedPaths,
                            thumbnail = if (ui.previewEnabled) ui.thumbnails[file.path] else null,
                            capturedAt = ui.captureDates[file.path] ?: file.capturedAtEpochSeconds,
                            previewEnabled = ui.previewEnabled,
                            onOpenDirectory = onOpenDirectory,
                            onToggle = onToggle,
                            onPreview = onPreview,
                            onRequestThumbnail = onRequestThumbnail,
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(visibleFiles, key = { it.path }) { file ->
                        CameraFileRow(
                            file = file,
                            selected = file.path in ui.selectedPaths,
                            thumbnail = if (ui.previewEnabled) ui.thumbnails[file.path] else null,
                            capturedAt = ui.captureDates[file.path] ?: file.capturedAtEpochSeconds,
                            previewEnabled = ui.previewEnabled,
                            onOpenDirectory = onOpenDirectory,
                            onToggle = onToggle,
                            onPreview = onPreview,
                            onRequestThumbnail = onRequestThumbnail,
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${ui.selectedPaths.size} 个已选",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = { onSelectAll(visibleFiles) },
                    enabled = visibleFiles.any { !it.isDirectory },
                ) {
                    val selectable = visibleFiles.filterNot { it.isDirectory }.map { it.path }.toSet()
                    Text(
                        if (selectable.isNotEmpty() && ui.selectedPaths.containsAll(selectable)) {
                            "取消全选"
                        } else {
                            "全选"
                        },
                    )
                }
                Button(
                    onClick = onDownload,
                    enabled = ui.selectedPaths.isNotEmpty(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("下载选中")
                }
            }
        }
    }

    if (ui.preview.file != null) {
        CameraPreviewDialog(
            preview = ui.preview,
            selected = ui.preview.file?.path in ui.selectedPaths,
            hasPrevious = ui.previewIndex > 0,
            hasNext = ui.previewIndex < ui.previewQueue.lastIndex,
            onPrevious = onPreviousPreview,
            onNext = onNextPreview,
            onToggle = { ui.preview.file?.let { onToggle(it.path) } },
            onClose = onClosePreview,
        )
    }
}

@Composable
private fun CameraFileRow(
    file: CameraFile,
    selected: Boolean,
    thumbnail: Bitmap?,
    capturedAt: Long?,
    previewEnabled: Boolean,
    onOpenDirectory: (String) -> Unit,
    onToggle: (String) -> Unit,
    onPreview: (CameraFile) -> Unit,
    onRequestThumbnail: (CameraFile) -> Unit,
) {
    LaunchedEffect(file.path, previewEnabled, thumbnail) {
        if (previewEnabled && !file.isDirectory && thumbnail == null) {
            onRequestThumbnail(file)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                when {
                    file.isDirectory -> onOpenDirectory(file.path)
                    previewEnabled -> onPreview(file)
                    else -> onToggle(file.path)
                }
            }
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CameraFileThumbnail(
            file = file,
            thumbnail = thumbnail,
            modifier = Modifier.size(46.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (file.isDirectory) {
                    "文件夹"
                } else {
                    "${captureDateLabel(capturedAt)} · ${kindLabel(file.kind)} · ${formatBytes(file.sizeBytes)}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (file.isDirectory) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle(file.path) },
            )
        }
    }
}

@Composable
private fun CameraFileThumbnail(
    file: CameraFile,
    thumbnail: Bitmap?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = file.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Icon(
                imageVector = if (file.isDirectory) {
                    Icons.Outlined.Folder
                } else {
                    iconForKind(file.kind)
                },
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CameraFileGridItem(
    file: CameraFile,
    selected: Boolean,
    thumbnail: Bitmap?,
    capturedAt: Long?,
    previewEnabled: Boolean,
    onOpenDirectory: (String) -> Unit,
    onToggle: (String) -> Unit,
    onPreview: (CameraFile) -> Unit,
    onRequestThumbnail: (CameraFile) -> Unit,
) {
    LaunchedEffect(file.path, previewEnabled, thumbnail) {
        if (previewEnabled && !file.isDirectory && thumbnail == null) {
            onRequestThumbnail(file)
        }
    }
    val shape = RoundedCornerShape(16.dp)
    val scheme = MaterialTheme.colorScheme
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(shape)
                .background(scheme.surfaceContainerHigh)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) scheme.primary else scheme.outlineVariant.copy(alpha = 0.7f),
                    shape = shape,
                )
                .clickable {
                    when {
                        file.isDirectory -> onOpenDirectory(file.path)
                        previewEnabled -> onPreview(file)
                        else -> onToggle(file.path)
                    }
                },
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail.asImageBitmap(),
                    contentDescription = file.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = if (file.isDirectory) {
                        Icons.Outlined.Folder
                    } else {
                        iconForKind(file.kind)
                    },
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(38.dp),
                    tint = scheme.primary,
                )
            }
            if (!file.isDirectory) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggle(file.path) },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(2.dp),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = file.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (file.isDirectory) "文件夹" else captureDateLabel(capturedAt),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
        if (!file.isDirectory) {
            Text(
                text = "${kindLabel(file.kind)} · ${formatBytes(file.sizeBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CameraPreviewDialog(
    preview: CameraPreviewUiState,
    selected: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggle: () -> Unit,
    onClose: () -> Unit,
) {
    val file = preview.file ?: return
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${captureDateLabel(preview.capturedAtEpochSeconds)} · " +
                                "${kindLabel(file.kind)} · ${formatBytes(file.sizeBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Outlined.Close, contentDescription = "关闭预览")
                    }
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        preview.loading -> CircularProgressIndicator()
                        preview.bitmap != null -> Image(
                            bitmap = preview.bitmap.asImageBitmap(),
                            contentDescription = file.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                        else -> Text(
                            text = preview.error ?: "无法读取预览图",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onPrevious, enabled = hasPrevious) {
                        Text("上一张")
                    }
                    TextButton(onClick = onNext, enabled = hasNext) {
                        Text("下一张")
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = onToggle) {
                        Text(if (selected) "取消选择" else "选择此图")
                    }
                }
                Text(
                    text = if (file.kind == MediaKind.JPEG) "原图预览" else "相机缩略图预览",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun iconForKind(kind: MediaKind) = when (kind) {
    MediaKind.JPEG -> Icons.Outlined.Image
    MediaKind.VIDEO -> Icons.Outlined.Movie
    MediaKind.RAW -> Icons.AutoMirrored.Outlined.InsertDriveFile
    MediaKind.UNKNOWN -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

private fun kindLabel(kind: MediaKind): String = when (kind) {
    MediaKind.JPEG -> "JPEG"
    MediaKind.RAW -> "RAW"
    MediaKind.VIDEO -> "视频"
    MediaKind.UNKNOWN -> "文件"
}

private val captureDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.CHINA)

private fun captureDateLabel(epochSeconds: Long?): String {
    if (epochSeconds == null) return "日期未知"
    return runCatching {
        Instant.ofEpochSecond(epochSeconds)
            .atZone(ZoneId.systemDefault())
            .format(captureDateFormatter)
    }.getOrDefault("日期未知")
}
