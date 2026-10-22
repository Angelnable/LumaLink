package com.lumalink.g100d.screens

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lumalink.g100d.BuildConfig
import com.lumalink.g100d.core.settings.AppSettings
import com.lumalink.g100d.core.settings.ThemeMode
import com.lumalink.g100d.core.util.formatBytes
import com.lumalink.g100d.transport.ui.BrandGuideSheet
import com.lumalink.g100d.ui.component.LumaCard
import com.lumalink.g100d.ui.component.SectionHeader
import com.lumalink.g100d.ui.component.SettingRow
import com.lumalink.g100d.ui.component.StatusPill
import com.lumalink.g100d.ui.component.StatusTone
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onThemeModeChange: (ThemeMode) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    var cacheCleared by remember { mutableStateOf(false) }
    var showBrandGuide by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    var showSafety by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        cacheBytes = withContext(Dispatchers.IO) { directorySize(context.cacheDir) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { SectionHeader("外观") }
        item {
            LumaCard {
                Text(text = "主题", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(10.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = settings.themeMode == mode,
                            onClick = { onThemeModeChange(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                            label = { Text(themeLabel(mode)) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "跟随系统时会随深色模式自动切换；状态栏与手势条由 Compose 管理。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

            }
        }

        item { SectionHeader("传输") }
        item {
            LumaCard {
                SettingRow(
                    icon = Icons.Outlined.CloudDownload,
                    title = "并发下载",
                    subtitle = "默认单文件单连接；阶段 2 接入后可按相机承受能力调整。",
                    trailing = { ValueText("1") },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingRow(
                    icon = Icons.Outlined.Description,
                    title = "完整性校验",
                    subtitle = "字节数 + 文件结构 + 本地 SHA-256；相机不提供官方哈希。",
                    trailing = { ValueText("开启") },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingRow(
                    icon = Icons.Outlined.Storage,
                    title = "传输目标",
                    subtitle = "JPEG/MP4 → 系统相册 LumaLink；RAW → Downloads/LumaLink。",
                    trailing = { ValueText("系统媒体库") },
                )
            }
        }

        item { SectionHeader("兼容性与诊断") }
        item {
            LumaCard {
                SettingRow(
                    icon = Icons.Outlined.PhoneAndroid,
                    title = "厂商省电设置",
                    subtitle = "小米 / 荣耀 / vivo / OPPO / 三星",
                    onClick = { showBrandGuide = true },
                    trailing = { Chevron() },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingRow(
                    icon = Icons.Outlined.Description,
                    title = "诊断日志",
                    subtitle = "阶段 2 启用；只读请求审计与脱敏导出",
                    onClick = { showDiagnostics = true },
                    trailing = {
                        StatusPill(text = "阶段 2", tone = StatusTone.Accent)
                    },
                )
            }
        }

        item { SectionHeader("存储") }
        item {
            LumaCard {
                SettingRow(
                    icon = Icons.Outlined.Storage,
                    title = "缩略图与临时缓存",
                    subtitle = when {
                        cacheCleared -> "已清理"
                        cacheBytes == null -> "计算中…"
                        else -> formatBytes(cacheBytes ?: 0L)
                    },
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                context.cacheDir.deleteRecursively()
                            }
                            cacheBytes = 0L
                            cacheCleared = true
                        }
                    },
                    trailing = { ValueText("清理") },
                )
            }
        }

        item { SectionHeader("关于") }
        item {
            LumaCard {
                SettingRow(
                    icon = Icons.Outlined.Info,
                    title = "关于 LumaLink",
                    subtitle = "版本 ${BuildConfig.VERSION_NAME}",
                    onClick = onOpenAbout,
                    trailing = { Chevron() },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingRow(
                    icon = Icons.Outlined.Description,
                    title = "开源许可",
                    onClick = { showLicenses = true },
                    trailing = { Chevron() },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                SettingRow(
                    icon = Icons.Outlined.Shield,
                    title = "安全承诺",
                    onClick = { showSafety = true },
                    trailing = { Chevron() },
                )
            }
        }

        item {
            Text(
                text = "LumaLink 是非官方第三方工具，与 Panasonic 无隶属关系。\n当前为阶段 1 开发预览版，尚未接入相机。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 12.dp),
            )
        }
    }

    if (showBrandGuide) {
        BrandGuideSheet(onDismiss = { showBrandGuide = false })
    }

    if (showDiagnostics) {
        DiagnosticsSheet(onDismiss = { showDiagnostics = false })
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text("开源许可") },
            text = {
                Text(
                    "LumaLink 以 Apache-2.0 许可发布。使用的 AndroidX、Kotlin、OkHttp、Coil、Media3 等组件分别遵循其各自许可；完整清单与 NOTICE 会在打包阶段生成。",
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicenses = false }) { Text("关闭") }
            },
        )
    }

    if (showSafety) {
        AlertDialog(
            onDismissRequest = { showSafety = false },
            title = { Text("安全承诺") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("• 不刷写固件、不写寄存器、不改系统分区")
                    Text("• 不绕过签名、不修改相机底层配置")
                    Text("• 不发送拍照、录像或监看命令")
                    Text("• 超时或协议不匹配立即断开，不反复重试写请求")
                    Text("• 不删除相机文件；只清理本地文件")
                    Text("• 只读访问相机，传输必须由用户明确触发")
                }
            },
            confirmButton = {
                TextButton(onClick = { showSafety = false }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun ValueText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.Outlined.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

private fun directorySize(directory: File?): Long {
    if (directory == null || !directory.exists()) return 0L
    return runCatching {
        directory.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
    }.getOrDefault(0L)
}

