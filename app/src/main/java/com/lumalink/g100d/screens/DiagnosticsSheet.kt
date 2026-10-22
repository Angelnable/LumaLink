package com.lumalink.g100d.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lumalink.g100d.core.diagnostics.DiagnosticEntry
import com.lumalink.g100d.core.diagnostics.DiagnosticLevel
import com.lumalink.g100d.core.diagnostics.Diagnostics
import com.lumalink.g100d.ui.component.EmptyState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsSheet(onDismiss: () -> Unit) {
    val entries by Diagnostics.entries.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("诊断日志", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "仅保存在当前进程内存；不会上传。复现问题后点右上角复制。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(Diagnostics.asText()))
                    },
                    enabled = entries.isNotEmpty(),
                ) {
                    Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = "复制")
                }
                IconButton(onClick = Diagnostics::clear, enabled = entries.isNotEmpty()) {
                    Icon(imageVector = Icons.Outlined.Delete, contentDescription = "清空")
                }
            }
            Spacer(Modifier.size(8.dp))

            if (entries.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Description,
                    title = "还没有日志",
                    description = "连接、握手、下载失败等事件会记录在这里。",
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(entries.asReversed(), key = { "${it.timestamp}-${it.message.hashCode()}" }) { entry ->
                        DiagnosticRow(entry)
                    }
                }
            }

            Spacer(Modifier.size(12.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("关闭")
            }
        }
    }
}

@Composable
private fun DiagnosticRow(entry: DiagnosticEntry) {
    val color = when (entry.level) {
        DiagnosticLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
        DiagnosticLevel.WARN -> MaterialTheme.colorScheme.tertiary
        DiagnosticLevel.ERROR -> MaterialTheme.colorScheme.error
    }
    val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(entry.timestamp))
    Text(
        text = "$time ${entry.level.name} ${entry.message}",
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = color,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}
