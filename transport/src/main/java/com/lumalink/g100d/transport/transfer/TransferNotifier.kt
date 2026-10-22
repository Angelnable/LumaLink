package com.lumalink.g100d.transport.transfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.lumalink.g100d.core.data.TransferEntity
import com.lumalink.g100d.core.util.formatBytes
import com.lumalink.g100d.transport.R

class TransferNotifier(private val context: Context) {

    init {
        createChannel()
    }

    fun idle(): Notification = buildNotification(
        fileName = "准备传输…",
        bytes = 0L,
        total = 0L,
        speed = 0L,
        transferId = null,
    )

    fun update(
        transfer: TransferEntity,
        bytes: Long,
        total: Long,
        speed: Long,
    ) {
        notifySafely(
            buildNotification(
                fileName = transfer.fileName,
                bytes = bytes,
                total = total,
                speed = speed,
                transferId = transfer.id,
            ),
        )
    }

    fun completed(transfer: TransferEntity) {
        notifySafely(
            buildNotification(
                fileName = "已完成：${transfer.fileName}",
                bytes = transfer.sizeBytes,
                total = transfer.sizeBytes,
                speed = 0L,
                transferId = null,
            ),
        )
    }

    private fun buildNotification(
        fileName: String,
        bytes: Long,
        total: Long,
        speed: Long,
        transferId: String?,
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lumalink)
            .setContentTitle("LumaLink 传输")
            .setContentText(
                buildString {
                    append(fileName)
                    if (bytes > 0L) {
                        append(" · ")
                        append(formatBytes(bytes))
                        if (total > 0L) {
                            append(" / ")
                            append(formatBytes(total))
                        }
                    }
                    if (speed > 0L) {
                        append(" · ")
                        append(formatBytes(speed))
                        append("/s")
                    }
                },
            )
            .setOnlyAlertOnce(true)
            .setOngoing(transferId != null)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (total > 0L) {
            val percent = ((bytes.coerceAtLeast(0L) * 100L) / total).toInt().coerceIn(0, 100)
            builder.setProgress(100, percent, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        launchIntent()?.let { builder.setContentIntent(it) }

        if (transferId != null) {
            val cancelIntent = Intent(context, TransferService::class.java)
                .setAction(TransferService.ACTION_CANCEL)
                .putExtra(TransferService.EXTRA_ID, transferId)
            val cancelPendingIntent = PendingIntent.getService(
                context,
                transferId.hashCode(),
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "取消", cancelPendingIntent)
        }

        return builder.build()
    }

    private fun notifySafely(notification: Notification) {
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun launchIntent(): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "文件传输",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "显示相机文件传输进度"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "lumalink_transfers"
        const val NOTIFICATION_ID = 4101
    }
}
