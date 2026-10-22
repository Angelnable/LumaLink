package com.lumalink.g100d.screens

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lumalink.g100d.BuildConfig
import com.lumalink.g100d.R
import com.lumalink.g100d.ui.component.KeyValueRow
import com.lumalink.g100d.ui.component.LumaCard
import com.lumalink.g100d.ui.component.SectionHeader

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AboutHero()

        LumaCard {
            Text(
                text = "为 LUMIX 相机设计",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "LumaLink 是一个第三方 Android 图传工具，目标是让 LUMIX 相机的照片、RAW 和视频传输更可靠，并提供清晰的传输队列。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "当前版本是纯图传版，只保留连接、相机文件选择与传输队列；相册、本地预览、遥控、监看、拍摄与 LUT 均已移除。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionHeader("支持机型")
        LumaCard {
            Text(
                text = "已真机验证",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "LUMIX G100D、LUMIX S5M2（S52）",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "LUMIX Sync 支持列表（未真机验证）",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "S1 / S1R / S1H / S5 / S5M2X / GH5M2 / GH6 / GH7 / G9M2 / S9",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Image App 支持列表（未真机验证）",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "GF6 / G7 / GH4 / GX85 / GX80 / LX100 / TZ220 / GH5 / GH5S / G9 / GX7MK3 / GX880 / LX100M2 / TZ99 / ZS99 / TZ300 / FZ1000M2 / G99 / G99D",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "支持 5GHz / Wi-Fi 5 的机型会优先使用 5GHz 网络；实际速度取决于相机热点、手机 Wi-Fi 和 SD 卡。未验证机型可能因固件或协议差异无法使用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionHeader("安全承诺")
        LumaCard {
            SafetyLine("不刷写固件、不写寄存器、不改系统分区")
            SafetyLine("不绕过签名、不修改相机底层配置")
            SafetyLine("不发送拍照、录像或监看命令")
            SafetyLine("超时或协议不匹配立即断开，不重试写请求")
            SafetyLine("不删除相机文件；只清理本地文件")
            SafetyLine("只读访问相机，传输必须由用户明确触发")
        }

        SectionHeader("技术栈")
        LumaCard {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TechChip("Kotlin 2.0")
                TechChip("Jetpack Compose")
                TechChip("Material 3")
                TechChip("Room / DataStore")
                TechChip("OkHttp")
                TechChip("Media3")
                TechChip("Coil")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "构建版本以可复现的稳定组合锁定，优先保证在无 Android Studio 的环境下也能通过 Gradle 命令行构建。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionHeader("构建信息")
        LumaCard {
            KeyValueRow("版本", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            KeyValueRow("构建类型", BuildConfig.BUILD_TYPE)
            KeyValueRow("最低系统", "Android 10 (API 29)")
            KeyValueRow("目标系统", "Android 15 (API 35)")
            KeyValueRow("当前设备", "Android ${Build.VERSION.SDK_INT}")
            KeyValueRow("应用包名", BuildConfig.APPLICATION_ID)
        }

        Text(
            text = "LumaLink 是非官方第三方工具，与 Panasonic 无隶属关系。\nPanasonic、LUMIX 是松下集团的商标，仅用于说明兼容设备。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun AboutHero() {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        scheme.primaryContainer,
                        scheme.secondaryContainer,
                        scheme.tertiaryContainer,
                    ),
                ),
            )
            .padding(22.dp),
    ) {
        Column {
            Surface(
                color = scheme.onPrimaryContainer.copy(alpha = 0.12f),
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Link,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(12.dp)
                        .size(28.dp),
                    tint = scheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "LumaLink",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onPrimaryContainer,
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = scheme.onPrimaryContainer.copy(alpha = 0.12f),
                    shape = CircleShape,
                ) {
                    Text(
                        text = "阶段 1 · 开发预览",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Composable
private fun SafetyLine(text: String) {
    Row(
        modifier = Modifier.padding(vertical = 5.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(18.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TechChip(label: String) {
    AssistChip(
        onClick = {},
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Code,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
    )
}
