package com.lumalink.g100d.transport.ui

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lumalink.g100d.ui.component.LumaCard
import com.lumalink.g100d.ui.component.StatusPill
import com.lumalink.g100d.ui.component.StatusTone
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrandGuideSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        BrandGuideContent(onClose = onDismiss)
    }
}

private data class BrandGuide(
    val name: String,
    val matches: List<String>,
    val steps: List<String>,
)

private val brandGuides = listOf(
    BrandGuide(
        name = "小米 / Redmi（HyperOS / MIUI）",
        matches = listOf("xiaomi", "redmi", "poco"),
        steps = listOf(
            "设置 → 应用设置 → 应用管理 → LumaLink → 省电策略 → 无限制",
            "允许“自启动”和“后台弹出界面”",
            "在最近任务中下拉锁定 LumaLink",
        ),
    ),
    BrandGuide(
        name = "荣耀（MagicOS）",
        matches = listOf("honor"),
        steps = listOf(
            "设置 → 应用 → 应用启动管理 → LumaLink → 手动管理",
            "允许自启动、关联启动和后台活动",
            "电池优化中将 LumaLink 设为“不允许优化”",
        ),
    ),
    BrandGuide(
        name = "vivo / iQOO（OriginOS）",
        matches = listOf("vivo", "iqoo"),
        steps = listOf(
            "设置 → 应用与权限 → 应用管理 → LumaLink → 权限 → 后台高耗电 → 允许",
            "i管家 → 应用管理 → 自启动 → 允许 LumaLink",
            "电池设置中关闭对 LumaLink 的智能省电限制",
        ),
    ),
    BrandGuide(
        name = "OPPO / 一加 / realme（ColorOS）",
        matches = listOf("oppo", "oneplus", "realme"),
        steps = listOf(
            "设置 → 应用 → 应用管理 → LumaLink → 耗电管理 → 允许后台活动",
            "允许自启动与关联启动",
            "在最近任务中锁定 LumaLink",
        ),
    ),
    BrandGuide(
        name = "三星（One UI）",
        matches = listOf("samsung"),
        steps = listOf(
            "设置 → 电池 → 后台使用限制 → 将 LumaLink 加入“从不休眠”",
            "设置 → 应用 → LumaLink → 电池 → 不受限制",
            "关闭“自适应电池”对 LumaLink 的限制（可选）",
        ),
    ),
)

@Composable
private fun BrandGuideContent(onClose: () -> Unit) {
    val manufacturer = remember { Build.MANUFACTURER.lowercase(Locale.ROOT) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 28.dp),
    ) {
        Text("厂商省电设置", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Android 标准 API 无法绕过厂商后台限制；以下设置需要手动完成。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(18.dp))

        brandGuides.forEach { guide ->
            val isCurrent = guide.matches.any { manufacturer.contains(it) }
            LumaCard(
                modifier = Modifier.padding(bottom = 12.dp),
                contentPadding = PaddingValues(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = guide.name,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (isCurrent) {
                        StatusPill(text = "当前设备", tone = StatusTone.Accent)
                    }
                }
                Spacer(Modifier.height(10.dp))
                guide.steps.forEachIndexed { index, step ->
                    Text(
                        text = "${index + 1}. $step",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            }
        }

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("知道了")
        }
    }
}
