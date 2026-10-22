package com.lumalink.g100d.transport.transfer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lumalink.g100d.core.data.TransferRepository
import com.lumalink.g100d.core.diagnostics.Diagnostics
import com.lumalink.g100d.protocol.CameraAccessGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TransferService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var queueJob: Job? = null

    private lateinit var repository: TransferRepository
    private lateinit var notifier: TransferNotifier
    private lateinit var downloader: TransferDownloader

    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        notifier = TransferNotifier(this)
        val host = application as? TransferServiceHost ?: return
        repository = host.transferRepository
        downloader = TransferDownloader(
            context = applicationContext,
            repository = repository,
            sessionProvider = host.sessionProvider,
            notifier = notifier,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!::repository.isInitialized) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        when (intent?.action) {
            ACTION_CANCEL -> {
                ensureForeground()
                val id = intent.getStringExtra(EXTRA_ID)
                if (id != null) {
                    scope.launch {
                        repository.markCanceled(id)
                        downloader.cancelActive()
                        delay(200)
                        if (queueJob?.isActive != true) {
                            stopSelfSafely()
                        }
                    }
                }
            }

            ACTION_RETRY -> {
                ensureForeground()
                val id = intent.getStringExtra(EXTRA_ID)
                if (id != null) {
                    scope.launch {
                        repository.retry(id)
                        startQueue()
                    }
                }
            }

            ACTION_STOP -> stopSelf()

            else -> startQueue()
        }
        return START_NOT_STICKY
    }

    private fun startQueue() {
        ensureForeground()
        if (queueJob?.isActive == true) return

        acquireLocks()
        Diagnostics.info("传输前台服务启动")
        queueJob = scope.launch {
            try {
                // Heal rows left RUNNING by a killed service/process. Without
                // this a new selection can sit behind a ghost transfer.
                repository.failStaleRunning("上次传输被中断，请重新选择文件")
                downloader.runQueue()
            } catch (cancellation: CancellationException) {
                // Service is being stopped.
            } finally {
                releaseLocks()
                Diagnostics.info("传输队列结束，停止前台服务")
                withContext(NonCancellable) {
                    stopSelfSafely()
                }
            }
        }
    }

    private fun ensureForeground() {
        ServiceCompat.startForeground(
            this,
            TransferNotifier.NOTIFICATION_ID,
            notifier.idle(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun stopSelfSafely() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireLocks() {
        if (wifiLock == null) {
            val wifiManager = getSystemService(WifiManager::class.java)
            wifiLock = wifiManager.createWifiLock(
                WifiManager.WIFI_MODE_FULL_LOW_LATENCY,
                "LumaLink:transfer",
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        if (wakeLock == null) {
            val powerManager = getSystemService(PowerManager::class.java)
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "LumaLink:transfer",
            ).apply {
                setReferenceCounted(false)
                acquire(2 * 60 * 60 * 1000L)
            }
        }
    }

    private fun releaseLocks() {
        wifiLock?.let { lock ->
            runCatching { if (lock.isHeld) lock.release() }
        }
        wifiLock = null
        wakeLock?.let { lock ->
            runCatching { if (lock.isHeld) lock.release() }
        }
        wakeLock = null
    }

    override fun onDestroy() {
        CameraAccessGate.transferActive = false
        scope.cancel()
        releaseLocks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.lumalink.g100d.action.START_TRANSFERS"
        const val ACTION_CANCEL = "com.lumalink.g100d.action.CANCEL_TRANSFER"
        const val ACTION_RETRY = "com.lumalink.g100d.action.RETRY_TRANSFER"
        const val ACTION_STOP = "com.lumalink.g100d.action.STOP_TRANSFERS"
        const val EXTRA_ID = "transfer_id"

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TransferService::class.java).setAction(ACTION_START),
            )
        }

        fun cancel(context: Context, transferId: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TransferService::class.java)
                    .setAction(ACTION_CANCEL)
                    .putExtra(EXTRA_ID, transferId),
            )
        }

        fun retry(context: Context, transferId: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TransferService::class.java)
                    .setAction(ACTION_RETRY)
                    .putExtra(EXTRA_ID, transferId),
            )
        }
    }
}
