package com.lumalink.g100d.transport.transfer

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.lumalink.g100d.core.data.TransferEntity
import com.lumalink.g100d.core.data.TransferRepository
import com.lumalink.g100d.core.diagnostics.Diagnostics
import com.lumalink.g100d.core.data.TransferState
import com.lumalink.g100d.protocol.CameraAccessGate
import com.lumalink.g100d.protocol.CameraFile
import com.lumalink.g100d.protocol.LumixResult
import com.lumalink.g100d.protocol.MediaKind
import com.lumalink.g100d.transport.session.CameraSession
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val PARALLEL_WORKERS = 1
private const val RANGE_STREAMS = 1
private const val RANGE_MIN_BYTES = 24L * 1024L * 1024L
private const val DOWNLOAD_BUFFER_BYTES = 4 * 1024 * 1024

class TransferDownloader(
    private val context: Context,
    private val repository: TransferRepository,
    private val sessionProvider: () -> CameraSession?,
    private val notifier: TransferNotifier,
) {

    private val activeCalls = ConcurrentHashMap.newKeySet<HttpURLConnection>()
    private val claimMutex = Mutex()

    suspend fun runQueue() = coroutineScope {
        // Transfer owns the camera while the queue is alive. Gallery browsing
        // and thumbnail prefetch must not issue another Browse/50001 request.
        CameraAccessGate.transferActive = true
        try {
            val stop = AtomicBoolean(false)
            val workers = (1..PARALLEL_WORKERS).map {
                launch {
                    while (!stop.get()) {
                        // Claim under one mutex so two workers cannot take the
                        // same Room row. The claim also makes the row RUNNING.
                        val item = claimMutex.withLock {
                            repository.nextQueued()?.also { queued ->
                                repository.updateProgress(
                                    id = queued.id,
                                    state = TransferState.RUNNING,
                                    bytes = 0,
                                    speed = 0,
                                )
                            }
                        } ?: break

                        if (!download(item)) {
                            stop.set(true)
                            cancelActive()
                        }
                    }
                }
            }
            workers.joinAll()
        } finally {
            CameraAccessGate.transferActive = false
        }
    }

    fun cancelActive() {
        activeCalls.toList().forEach { connection ->
            runCatching { connection.disconnect() }
        }
    }

    private suspend fun download(item: TransferEntity): Boolean =
        CameraAccessGate.withCamera { downloadLocked(item) }

    private suspend fun downloadLocked(item: TransferEntity): Boolean {
        var attempt = 0
        while (true) {
            if (downloadOnce(item)) return true
            val current = repository.findById(item.id)
            if (current?.state == TransferState.CANCELED) return false
            if (attempt >= 2 || sessionProvider() == null) return false
            attempt++
            Diagnostics.info("传输重试 ${item.fileName}（第 $attempt 次）")
            repository.retry(item.id)
            delay(1_500L * attempt)
        }
    }

    private suspend fun downloadOnce(item: TransferEntity): Boolean {
        val session = sessionProvider()
        if (session == null) {
            repository.markFailed(item.id, "相机未连接，无法开始传输")
            return false
        }

        Diagnostics.info("开始传输 ${item.fileName}（${item.sizeBytes} 字节）")
        val stagingDir = File(context.cacheDir, "transfers").apply { mkdirs() }
        val staging = File(stagingDir, "${item.id}.part")
        var resumeFrom = if (staging.exists()) staging.length() else 0L
        if (item.sizeBytes > 0L && resumeFrom > item.sizeBytes) {
            staging.delete()
            resumeFrom = 0L
        }

        val required = item.sizeBytes + 64L * 1024L * 1024L
        val freeSpace = context.cacheDir.usableSpace
        if (item.sizeBytes > 0L && freeSpace in 1 until required) {
            repository.markFailed(
                item.id,
                "存储空间不足：需要约 ${required / 1024L / 1024L} MB，" +
                    "缓存可用 ${freeSpace / 1024L / 1024L} MB",
            )
            return false
        }

        val cameraFile = CameraFile(
            path = item.cameraPath,
            name = item.fileName,
            sizeBytes = item.sizeBytes,
        )
        // The previous download consumed the camera's Browse grant, so every
        // queued file must re-authorize before its own single Browse.
        val accessRefresh = session.protocol.prepareTransferAccess(forceReauthorize = true)
        if (accessRefresh is LumixResult.Failure) {
            repository.markFailed(
                item.id,
                accessRefresh.error.message ?: "无法刷新相机目录访问",
            )
            return false
        }

        if (RANGE_STREAMS > 1 && resumeFrom == 0L &&
            item.sizeBytes >= RANGE_MIN_BYTES && isS5M2(session)
        ) {
            Diagnostics.info(
                "S5M2 多连接测试：${item.fileName}，" +
                    "${RANGE_STREAMS} 路 Range，${item.sizeBytes / 1024L / 1024L} MB",
            )
            if (downloadParallelRanges(item, session, staging)) return true
            val current = repository.findById(item.id)
            if (current?.state == TransferState.CANCELED) return false
            Diagnostics.info("多连接失败，回退单连接：${item.fileName}")
            staging.delete()
            // Parallel attempts can consume or invalidate the camera's
            // per-file grant. Re-authorize before the single-stream retry.
            val fallbackAccess = session.protocol.prepareTransferAccess(forceReauthorize = true)
            if (fallbackAccess is LumixResult.Failure) {
                repository.markFailed(
                    item.id,
                    fallbackAccess.error.message ?: "多连接失败后无法重新授权",
                )
                return false
            }
        }

        val connectionResult = session.protocol.openFileConnection(
            file = cameraFile,
            rangeStart = resumeFrom.takeIf { it > 0L },
        )
        if (connectionResult is LumixResult.Failure) {
            repository.markFailed(item.id, connectionResult.error.message ?: "无法打开相机文件")
            return false
        }

        val connection = (connectionResult as LumixResult.Success).value
        activeCalls += connection

        try {
            val code = connection.responseCode
            var append = resumeFrom > 0L && code == 206
            if (code == 200 && resumeFrom > 0L) {
                // Camera ignored Range; restart this file from byte zero.
                staging.delete()
                resumeFrom = 0L
                append = false
            }
            if (code !in 200..299) {
                if (code == 416) {
                    staging.delete()
                    resumeFrom = 0L
                }
                throw IOException("HTTP $code")
            }
            val contentLength = connection.contentLengthLong
            val total = if (item.sizeBytes > 0L) item.sizeBytes else {
                resumeFrom + contentLength.coerceAtLeast(0L)
            }
            val startedAt = System.currentTimeMillis()
            var written = resumeFrom
            var lastUpdate = 0L
            val digest = MessageDigest.getInstance("SHA-256")
            if (append) {
                FileInputStream(staging).use { existing ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    while (true) {
                        val read = existing.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                }
            }

            connection.inputStream.use { input ->
                FileOutputStream(staging, append).use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        written += read
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= 250L) {
                            val speed = if (now > startedAt) {
                                ((written - resumeFrom) * 1000L) / (now - startedAt)
                            } else {
                                0L
                            }
                            repository.updateProgress(
                                id = item.id,
                                state = TransferState.RUNNING,
                                bytes = written,
                                speed = speed,
                            )
                            notifier.update(item, written, total, speed)
                            lastUpdate = now
                        }
                    }
                }
            }

            if (item.sizeBytes > 0L && staging.length() != item.sizeBytes) {
                throw IOException(
                    "校验失败：期望 ${item.sizeBytes} 字节，实际 ${staging.length()} 字节",
                )
            }
            validateStructure(staging, item.fileName)
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            val localUri = publish(staging, item)
            repository.markCompleted(
                id = item.id,
                bytes = staging.length(),
                localUri = localUri,
                sha256 = sha256,
            )
            notifier.completed(item)
            Diagnostics.info("传输完成 ${item.fileName} -> $localUri")
            staging.delete()
            return true
        } catch (cancellation: CancellationException) {
            handleCancellation(item, staging)
            return false
        } catch (error: IOException) {
            val current = repository.findById(item.id)
            if (current?.state == TransferState.CANCELED) {
                return false
            }
            if (error.message?.startsWith("校验失败") == true) {
                staging.delete()
            }
            val message = if (error.message?.startsWith("校验失败") == true) {
                friendlyError(error).removePrefix("传输失败：")
            } else {
                "相机连接中断，保留进度，可重试续传"
            }
            Diagnostics.error("传输失败 ${item.fileName}: $message")
            repository.markFailed(item.id, message)
            return false
        } finally {
            activeCalls -= connection
            connection.disconnect()
        }
    }

    private fun isS5M2(session: CameraSession): Boolean {
        val model = session.model.model.uppercase()
        return model.contains("S5M2") || model.contains("S52")
    }

    /**
     * Experimental S5M2 path: request the same file as four byte ranges on
     * four connections and write each range at its offset. If the camera
     * serializes or rejects ranges, every error returns false and the caller
     * falls back to the normal single-stream download.
     */
    private suspend fun downloadParallelRanges(
        item: TransferEntity,
        session: CameraSession,
        staging: File,
    ): Boolean {
        if (item.sizeBytes <= 0L) return false
        val total = item.sizeBytes
        val cameraFile = CameraFile(
            path = item.cameraPath,
            name = item.fileName,
            sizeBytes = total,
        )
        val startedAt = System.currentTimeMillis()
        val progress = AtomicLong(0)
        val failure = AtomicReference<Throwable?>(null)
        val chunkSize = (total + RANGE_STREAMS - 1) / RANGE_STREAMS

        staging.delete()
        try {
            RandomAccessFile(staging, "rw").use { it.setLength(total) }
        } catch (error: Throwable) {
            Diagnostics.error("多连接预分配失败：${error.message}")
            return false
        }

        val results = supervisorScope {
            (0 until RANGE_STREAMS).map { index ->
                async(Dispatchers.IO) {
                    val start = index * chunkSize
                    val end = minOf(total - 1L, start + chunkSize - 1L)
                    if (start > end) return@async true
                    var connection: HttpURLConnection? = null
                    try {
                        val opened = session.protocol.openFileConnection(
                            file = cameraFile,
                            rangeStart = start,
                            rangeEnd = end,
                        )
                        if (opened is LumixResult.Failure) {
                            throw IOException(opened.error.message ?: "range open failed")
                        }
                        val activeConnection = (opened as LumixResult.Success).value
                        connection = activeConnection
                        activeCalls += activeConnection
                        val code = activeConnection.responseCode
                        if (code != 206) throw IOException("range HTTP $code")
                        RandomAccessFile(staging, "rw").use { target ->
                            target.seek(start)
                            activeConnection.inputStream.use { input ->
                                val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
                                var lastUpdate = 0L
                                while (true) {
                                    if (failure.get() != null) throw IOException("parallel aborted")
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    target.write(buffer, 0, read)
                                    val written = progress.addAndGet(read.toLong())
                                    val now = System.currentTimeMillis()
                                    if (now - lastUpdate >= 300L) {
                                        val elapsed = (now - startedAt).coerceAtLeast(1L)
                                        val speed = written * 1000L / elapsed
                                        repository.updateProgress(
                                            id = item.id,
                                            state = TransferState.RUNNING,
                                            bytes = written,
                                            speed = speed,
                                        )
                                        notifier.update(item, written, total, speed)
                                        lastUpdate = now
                                    }
                                }
                            }
                        }
                        true
                    } catch (error: Throwable) {
                        failure.compareAndSet(null, error)
                        false
                    } finally {
                        connection?.let {
                            activeCalls -= it
                            runCatching { it.disconnect() }
                        }
                    }
                }
            }.awaitAll()
        }

        val error = failure.get()
        if (error != null || results.any { !it }) {
            Diagnostics.info("多连接失败：${error?.message ?: "chunk error"}")
            return false
        }
        if (staging.length() != total) {
            Diagnostics.info("多连接长度不符：${staging.length()} / $total")
            return false
        }
        return try {
            validateStructure(staging, item.fileName)
            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
            val speed = total * 1000L / elapsed
            val sha = sha256(staging)
            val localUri = publish(staging, item)
            repository.markCompleted(
                id = item.id,
                bytes = total,
                localUri = localUri,
                sha256 = sha,
            )
            notifier.completed(item)
            Diagnostics.info(
                "多连接完成 ${item.fileName}：${speed / 1024L / 1024L} MB/s",
            )
            staging.delete()
            true
        } catch (error: Throwable) {
            Diagnostics.error("多连接校验/发布失败：${error.message}")
            false
        }
    }

    private suspend fun handleCancellation(item: TransferEntity, staging: File) {
        val current = repository.findById(item.id)
        if (current?.state == TransferState.CANCELED) {
            staging.delete()
        } else {
            repository.markCanceled(item.id)
            staging.delete()
        }
    }

    private fun friendlyError(error: Throwable): String {
        val message = error.message ?: "未知错误"
        return when {
            "timeout" in message.lowercase() -> "相机响应超时"
            "canceled" in message.lowercase() -> "传输已取消"
            "HTTP 404" in message -> "相机上找不到该文件"
            "HTTP 401" in message || "HTTP 403" in message -> "相机拒绝了请求"
            else -> "传输失败：$message"
        }
    }

    private fun validateStructure(file: File, fileName: String) {
        if (file.length() <= 0L) throw IOException("校验失败：空文件")
        val kind = MediaKind.fromFileName(fileName)
        FileInputStream(file).use { input ->
            val header = ByteArray(16)
            val count = input.read(header)
            when (kind) {
                MediaKind.JPEG -> {
                    if (count < 2 || header[0] != 0xFF.toByte() || header[1] != 0xD8.toByte()) {
                        throw IOException("校验失败：不是有效的 JPEG 文件")
                    }
                }

                MediaKind.VIDEO -> {
                    val brand = if (count >= 8) String(header, 4, 4, Charsets.US_ASCII) else ""
                    if (brand != "ftyp") {
                        throw IOException("校验失败：不是有效的 MP4/MOV 文件")
                    }
                }

                MediaKind.RAW -> {
                    val littleEndian = count >= 4 &&
                        header[0] == 'I'.code.toByte() &&
                        header[1] == 'I'.code.toByte() &&
                        header[2] == 0x2A.toByte() &&
                        header[3] == 0x00.toByte()
                    val bigEndian = count >= 4 &&
                        header[0] == 'M'.code.toByte() &&
                        header[1] == 'M'.code.toByte() &&
                        header[2] == 0x00.toByte() &&
                        header[3] == 0x2A.toByte()
                    // Panasonic RW2 uses a TIFF variant with magic IIU\0.
                    val rw2 = count >= 4 &&
                        header[0] == 'I'.code.toByte() &&
                        header[1] == 'I'.code.toByte() &&
                        header[2] == 0x55.toByte() &&
                        header[3] == 0x00.toByte()
                    if (!littleEndian && !bigEndian && !rw2) {
                        throw IOException("校验失败：RAW 文件头不是 TIFF/RW2")
                    }
                }

                MediaKind.UNKNOWN -> Unit
            }
        }

        if (kind == MediaKind.JPEG) {
            RandomAccessFile(file, "r").use { random ->
                val length = random.length()
                if (length >= 2) {
                    // Panasonic JPEGs can carry a small trailer after EOI.
                    // Accept an EOI anywhere in the final 64 KiB instead of
                    // requiring it at the literal last two bytes.
                    val scan = minOf(length, 64L * 1024L).toInt()
                    val start = length - scan
                    random.seek(start)
                    val tail = ByteArray(scan)
                    random.readFully(tail)
                    var eoi = -1
                    for (index in 0 until tail.size - 1) {
                        if (tail[index] == 0xFF.toByte() && tail[index + 1] == 0xD9.toByte()) {
                            eoi = index
                        }
                    }
                    if (eoi < 0) {
                        val hex = tail.takeLast(8).joinToString("") { "%02X".format(it) }
                        throw IOException("校验失败：JPEG 文件尾不完整（末字节 $hex）")
                    }
                    Diagnostics.info(
                        "JPEG EOI 校验通过：offsetFromEnd=${tail.size - eoi - 2} " +
                            "trailingBytes=${tail.size - eoi - 2}",
                    )
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun publish(staging: File, item: TransferEntity): String {
        return try {
            publishToMediaStore(staging, item)
        } catch (error: Throwable) {
            publishToAppStorage(staging, item)
        }
    }

    private fun publishToMediaStore(staging: File, item: TransferEntity): String {
        val kind = MediaKind.fromFileName(item.fileName)
        val collection = when (kind) {
            MediaKind.JPEG -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            MediaKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Downloads.EXTERNAL_CONTENT_URI
        }
        val relativePath = when (kind) {
            MediaKind.JPEG -> "${Environment.DIRECTORY_PICTURES}/LumaLink"
            MediaKind.VIDEO -> "${Environment.DIRECTORY_MOVIES}/LumaLink"
            else -> "${Environment.DIRECTORY_DOWNLOADS}/LumaLink/RAW"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, item.fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, item.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values)
            ?: throw IOException("无法创建媒体库条目")
        try {
            resolver.openOutputStream(uri)?.use { output ->
                staging.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("无法打开媒体库输出流")
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            return uri.toString()
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }

    private fun publishToAppStorage(staging: File, item: TransferEntity): String {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir
        val directory = File(base, "LumaLink").apply { mkdirs() }
        val target = uniqueTarget(directory, item.fileName)
        staging.copyTo(target, overwrite = false)
        return target.toURI().toString()
    }

    private fun uniqueTarget(directory: File, fileName: String): File {
        var candidate = File(directory, fileName)
        if (!candidate.exists()) return candidate
        val base = fileName.substringBeforeLast('.', fileName)
        val extension = fileName.substringAfterLast('.', "")
        var index = 1
        while (candidate.exists()) {
            val suffix = if (extension.isBlank()) "" else ".$extension"
            candidate = File(directory, "$base ($index)$suffix")
            index++
        }
        return candidate
    }
}
