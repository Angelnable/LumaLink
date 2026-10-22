package com.lumalink.g100d.transport.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumalink.g100d.transport.session.CameraConnectionState
import com.lumalink.g100d.ui.component.InfoBanner
import com.lumalink.g100d.ui.component.LumaCard
import com.lumalink.g100d.ui.component.NumberedStep
import com.lumalink.g100d.ui.component.SectionHeader
import com.lumalink.g100d.ui.component.StatusPill
import com.lumalink.g100d.ui.component.StatusTone

private enum class PendingAction {
    None,
    Connect,
    Scan,
    UseCurrent,
}

@Composable
fun ConnectScreen(
    viewModel: ConnectViewModel,
    onOpenTransfers: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    val ui by viewModel.uiState.collectAsStateWithLifecycle()

    var showConnectSheet by remember { mutableStateOf(false) }
    var showFilesSheet by remember { mutableStateOf(false) }
    var showBrands by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf(PendingAction.None) }
    val snackbarHostState = remember { SnackbarHostState() }

    val wifiPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }

    fun hasWifiPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val action = pendingAction
        pendingAction = PendingAction.None
        if (hasWifiPermission()) {
            when (action) {
                PendingAction.Connect -> viewModel.connect()
                PendingAction.Scan -> viewModel.scan()
                PendingAction.UseCurrent -> viewModel.useCurrentWifi()
                PendingAction.None -> Unit
            }
        } else {
            viewModel.reportPermissionDenied()
        }
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* Progress is also visible in-app if notifications are denied. */ }

    fun runWithWifiPermission(action: PendingAction, block: () -> Unit) {
        if (hasWifiPermission()) {
            block()
        } else {
            pendingAction = action
            permissionLauncher.launch(wifiPermissions)
        }
    }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(ui.message) {
        val message = ui.message ?: return@LaunchedEffect
        // Consume immediately so navigation cannot replay the same toast.
        viewModel.clearMessage()
        snackbarHostState.showSnackbar(message)
    }

    LaunchedEffect(connection) {
        if (connection is CameraConnectionState.Connected) {
            showConnectSheet = false
        }
    }

    LaunchedEffect(showFilesSheet) {
        if (showFilesSheet && ui.files.isEmpty() && !ui.filesLoading && ui.filesError == null) {
            viewModel.openCameraFiles()
        }
    }

    val connected = connection is CameraConnectionState.Connected

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ConnectionHero(
                connection = connection,
                onConnect = { showConnectSheet = true },
                onOpenFiles = {
                    if (connected) {
                        showFilesSheet = true
                    } else {
                        showConnectSheet = true
                    }
                },
                onDisconnect = viewModel::disconnect,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ConnectionTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.Wifi,
                    label = "Wi-Fi",
                    value = when (connection) {
                        is CameraConnectionState.Connected,
                        is CameraConnectionState.Handshaking,
                        -> "已绑定"

                        is CameraConnectionState.Connecting -> "连接中"
                        is CameraConnectionState.Error -> "异常"
                        CameraConnectionState.Disconnected -> "未连接"
                    },
                )
                ConnectionTile(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Outlined.CameraAlt,
                    label = "相机",
                    value = (connection as? CameraConnectionState.Connected)
                        ?.session?.model?.model
                        ?.let(::displayModelName)
                        ?.ifBlank { "相机" }
                        ?: "离线",
                )
            }

            val connectedSession = (connection as? CameraConnectionState.Connected)?.session
            if (connectedSession != null) {
                LumaCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "已连接 ${displayModelName(connectedSession.model.model)}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = buildString {
                                    append("只读会话")
                                    connectedSession.model.firmware?.let {
                                        append(" · 固件 ")
                                        append(it)
                                    }
                                    connectedSession.status?.mode?.let {
                                        append(" · 模式 ")
                                        append(it)
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        StatusPill(text = "只读", tone = StatusTone.Success)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                showConnectSheet = false
                                showFilesSheet = true
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("浏览相机文件")
                        }
                        OutlinedButton(
                            onClick = onOpenTransfers,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("传输队列")
                        }
                    }
                }
            } else {
                InfoBanner(
                    title = "连接前请关闭 LUMIX Sync",
                    body = "同一时间只让一个 App 连接相机，避免相机 HTTP 服务被占用。连接过程中系统会弹出 Wi-Fi 确认。",
                )
            }

            SectionHeader("三步连接")
            LumaCard(contentPadding = PaddingValues(18.dp)) {
                NumberedStep(
                    number = 1,
                    title = "相机进入 Wi-Fi 模式",
                    description = "MENU → Wi-Fi → Wi-Fi 功能 → 新建连接 → 遥控拍摄与查看",
                )
                NumberedStep(
                    number = 2,
                    title = "手机加入相机热点",
                    description = "点击“连接相机”，输入相机屏幕显示的 SSID；默认无密码，也可在相机中开启密码。",
                )
                NumberedStep(
                    number = 3,
                    title = "回到 App 完成连接",
                    description = "App 只读取状态与文件列表；不会修改相机设置。",
                    isLast = true,
                )
                TextButton(onClick = { showConnectSheet = true }) {
                    Text("打开连接设置")
                }
            }

            LumaCard(onClick = onOpenTransfers) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CloudDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("传输队列", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "查看进度、速度、失败原因与重试",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LumaCard(onClick = { showBrands = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.PhoneAndroid,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("小米 / HyperOS 兼容性", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "把省电策略设为“无限制”并允许自启动，避免大文件传输被后台清理。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        )

        if (showFilesSheet) {
            CameraFilesSheet(
                ui = ui,
                connected = connected,
                onOpenDirectory = viewModel::loadDirectory,
                onNavigateUp = viewModel::navigateUp,
                onRefresh = viewModel::refreshCameraFiles,
                onToggle = viewModel::toggleSelection,
                onDownload = {
                    requestNotificationPermissionIfNeeded()
                    viewModel.enqueueSelected()
                },
                onFilterChange = viewModel::setFileFilter,
                onPreviewEnabledChange = viewModel::setPreviewEnabled,
                onPreviewAll = viewModel::openPreviewAll,
                onSelectAll = viewModel::toggleSelectAll,
                onViewModeChange = viewModel::setViewMode,
                onPreview = viewModel::openPreview,
                onRequestThumbnail = viewModel::requestThumbnail,
                onPreviousPreview = viewModel::previousPreview,
                onNextPreview = viewModel::nextPreview,
                onClosePreview = viewModel::closePreview,
                onDismiss = {
                    viewModel.closePreview()
                    showFilesSheet = false
                },
            )
        }
    }

    if (showConnectSheet) {
        ConnectSetupSheet(
            ui = ui,
            connection = connection,
            onSsidChange = viewModel::updateSsid,
            onPasswordChange = viewModel::updatePassword,
            onBaseUrlChange = viewModel::updateBaseUrl,
            onScan = { runWithWifiPermission(PendingAction.Scan) { viewModel.scan() } },
            onConnect = { runWithWifiPermission(PendingAction.Connect) { viewModel.connect() } },
            onUseCurrentWifi = {
                runWithWifiPermission(PendingAction.UseCurrent) { viewModel.useCurrentWifi() }
            },
            onDismiss = { showConnectSheet = false },
        )
    }

    if (showBrands) {
        BrandGuideSheet(onDismiss = { showBrands = false })
    }

}

private fun displayModelName(raw: String): String =
    raw.removePrefix("LUMIX")
        .trim()
        .trim('"', '\'')
        .trim()

@Composable
private fun ConnectionHero(
    connection: CameraConnectionState,
    onConnect: () -> Unit,
    onOpenFiles: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val statusText = when (connection) {
        CameraConnectionState.Disconnected -> "未连接"
        is CameraConnectionState.Connecting -> "连接中"
        is CameraConnectionState.Handshaking -> "握手中"
        is CameraConnectionState.Connected -> "已连接"
        is CameraConnectionState.Error -> "连接异常"
    }
    val statusTone = when (connection) {
        is CameraConnectionState.Connected -> StatusTone.Success
        is CameraConnectionState.Connecting,
        is CameraConnectionState.Handshaking,
        -> StatusTone.Warning

        is CameraConnectionState.Error -> StatusTone.Error
        CameraConnectionState.Disconnected -> StatusTone.Neutral
    }
    val model = when (connection) {
        is CameraConnectionState.Connected -> connection.session.model.model
        else -> "LUMIX"
    }
    val displayModel = displayModelName(model).ifBlank { "相机" }
    val darkHero = scheme.background.luminance() < 0.4f
    val heroColors = if (darkHero) {
        listOf(Color(0xFF2A2B2E), Color(0xFF151619), Color(0xFF333438))
    } else {
        listOf(Color(0xFFFFFFFF), Color(0xFFECEDE9), scheme.primaryContainer)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(30.dp),
                ambientColor = Color.Black.copy(alpha = 0.16f),
                spotColor = Color.Black.copy(alpha = 0.22f),
            )
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(heroColors),
            )
            .padding(22.dp),
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color = scheme.onPrimaryContainer.copy(alpha = 0.07f),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.9f, size.height * 0.12f),
            )
            drawCircle(
                color = scheme.onPrimaryContainer.copy(alpha = 0.12f),
                radius = size.minDimension * 0.3f,
                center = Offset(size.width * 0.9f, size.height * 0.12f),
                style = Stroke(width = 1.5f),
            )
        }

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(text = statusText, tone = statusTone)
                Spacer(Modifier.weight(1f))
                Surface(
                    color = Color.White.copy(alpha = 0.18f),
                    shape = CircleShape,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(22.dp),
                        tint = scheme.onPrimaryContainer,
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
            Text(
                text = "LUMIX",
                style = MaterialTheme.typography.labelLarge,
                letterSpacing = 3.sp,
                color = scheme.onPrimaryContainer.copy(alpha = 0.65f),
            )
            Text(
                text = displayModel,
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = scheme.onPrimaryContainer,
            )
            Text(
                text = if (connection is CameraConnectionState.Connected) {
                    "Wi-Fi 直连 · 只读会话"
                } else {
                    "Wi-Fi 直连 · 仅图传"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
            )

            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (connection is CameraConnectionState.Connected) {
                    Button(
                        onClick = onOpenFiles,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = scheme.onPrimaryContainer,
                            contentColor = scheme.primaryContainer,
                        ),
                    ) {
                        Text("浏览文件")
                    }
                    OutlinedButton(
                        onClick = onDisconnect,
                        border = BorderStroke(1.dp, scheme.onPrimaryContainer.copy(alpha = 0.35f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = scheme.onPrimaryContainer,
                        ),
                    ) {
                        Text("断开")
                    }
                } else {
                    Button(
                        onClick = onConnect,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = scheme.onPrimaryContainer,
                            contentColor = scheme.primaryContainer,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Wifi,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("连接相机")
                    }

                }
            }
        }
    }
}

@Composable
private fun ConnectionTile(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerLow,
                        MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ),
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                shape = RoundedCornerShape(20.dp),
            )
            .padding(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}
