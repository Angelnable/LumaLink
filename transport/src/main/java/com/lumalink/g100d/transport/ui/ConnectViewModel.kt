package com.lumalink.g100d.transport.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lumalink.g100d.core.data.NewTransfer
import com.lumalink.g100d.core.data.TransferRepository
import com.lumalink.g100d.core.diagnostics.Diagnostics
import com.lumalink.g100d.protocol.CameraAccessGate
import com.lumalink.g100d.protocol.CameraFile
import com.lumalink.g100d.protocol.CameraSessionStore
import com.lumalink.g100d.protocol.LumixEndpoints
import com.lumalink.g100d.protocol.LumixResult
import com.lumalink.g100d.protocol.MediaKind
import com.lumalink.g100d.transport.session.CameraConnectionState
import com.lumalink.g100d.transport.session.CameraSession
import com.lumalink.g100d.transport.session.ConnectionManager
import com.lumalink.g100d.transport.session.WifiCandidate
import com.lumalink.g100d.transport.session.WifiScanner
import com.lumalink.g100d.transport.transfer.TransferService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val MAX_PREVIEW_BYTES = 80 * 1024 * 1024
private const val MAX_THUMBNAIL_BYTES = 8 * 1024 * 1024

enum class FileViewMode {
    GRID,
    LIST,
}

enum class FileFilter(val label: String) {
    ALL("全部"),
    PHOTOS("照片"),
    VIDEOS("视频"),
    JPG("JPG"),
    RAW("RAW");

    fun accepts(file: CameraFile): Boolean = when (this) {
        ALL -> true
        PHOTOS -> file.kind == MediaKind.JPEG || file.kind == MediaKind.RAW
        VIDEOS -> file.kind == MediaKind.VIDEO
        JPG -> file.kind == MediaKind.JPEG
        RAW -> file.kind == MediaKind.RAW
    }
}

data class CameraPreviewUiState(
    val file: CameraFile? = null,
    val bitmap: Bitmap? = null,
    val capturedAtEpochSeconds: Long? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

private data class FetchedImage(
    val bitmap: Bitmap?,
    val capturedAtEpochSeconds: Long? = null,
)

private data class FetchedBytes(
    val bytes: ByteArray,
    val capturedAtEpochSeconds: Long? = null,
)

data class ConnectUiState(
    val ssid: String = "",
    val password: String = "",
    val baseUrl: String = LumixEndpoints.DEFAULT_BASE_URL,
    val candidates: List<WifiCandidate> = emptyList(),
    val scanning: Boolean = false,
    val currentDirectory: String = "/DCIM",
    val files: List<CameraFile> = emptyList(),
    val filesLoading: Boolean = false,
    val filesError: String? = null,
    val selectedPaths: Set<String> = emptySet(),
    val message: String? = null,
    val permissionError: String? = null,
    val viewMode: FileViewMode = FileViewMode.GRID,
    val fileFilter: FileFilter = FileFilter.ALL,
    val previewEnabled: Boolean = false,
    val previewQueue: List<CameraFile> = emptyList(),
    val previewIndex: Int = 0,
    val thumbnails: Map<String, Bitmap> = emptyMap(),
    val captureDates: Map<String, Long> = emptyMap(),
    val preview: CameraPreviewUiState = CameraPreviewUiState(),
)

class ConnectViewModel(
    private val connectionManager: ConnectionManager,
    private val wifiScanner: WifiScanner,
    private val transferRepository: TransferRepository,
    private val appContext: Context,
) : ViewModel() {

    val connection: StateFlow<CameraConnectionState> = connectionManager.state

    private val _uiState = MutableStateFlow(ConnectUiState())
    val uiState: StateFlow<ConnectUiState> = _uiState.asStateFlow()

    private var thumbnailJob: Job? = null
    private var previewJob: Job? = null
    private val thumbnailRequests = mutableSetOf<String>()
    private var sessionKey: Any? = null
    private var accessReady = false
    private var accessRecoveryAttempted = false
    private var accessRequestInFlight = false

    init {
        viewModelScope.launch {
            connection.collect { state ->
                // A new camera session (or disconnect) must not reuse the
                // previous camera's files, thumbnails or access state.
                val key = (state as? CameraConnectionState.Connected)?.session?.protocol
                if (key !== sessionKey) {
                    sessionKey = key
                    accessReady = false
                    accessRecoveryAttempted = false
                    thumbnailJob?.cancel()
                    previewJob?.cancel()
                    // Never carry the previous camera's X-SESSION_ID into the
                    // next body (G100D would reject its Browse with 403).
                    CameraSessionStore.clear()
                    _uiState.update {
                        it.copy(
                            currentDirectory = "/DCIM",
                            files = emptyList(),
                            filesLoading = false,
                            filesError = null,
                            fileFilter = FileFilter.ALL,
                            previewEnabled = false,
                            previewQueue = emptyList(),
                            previewIndex = 0,
                            selectedPaths = emptySet(),
                            thumbnails = emptyMap(),
                            captureDates = emptyMap(),
                            preview = CameraPreviewUiState(),
                            message = null,
                        )
                    }
                    val session = (state as? CameraConnectionState.Connected)?.session
                    if (session != null && session.model.model == "LUMIX") {
                        // S5M2 hides capability/model until access is approved.
                        // Ask once automatically so the UI can show S5M2
                        // without requiring the user to open the file sheet.
                        accessReady = false
                        viewModelScope.launch {
                            delay(700)
                            val current = connectionManager.session.value
                            if (current?.protocol === session.protocol &&
                                !accessReady && !accessRequestInFlight
                            ) {
                                authorizeInBackground(session)
                            }
                        }
                    }
                }
            }
        }
    }

    fun updateSsid(value: String) = _uiState.update { it.copy(ssid = value) }

    fun updatePassword(value: String) = _uiState.update { it.copy(password = value) }

    fun updateBaseUrl(value: String) = _uiState.update { it.copy(baseUrl = value) }

    fun scan() {
        if (_uiState.value.scanning) return
        viewModelScope.launch {
            _uiState.update { it.copy(scanning = true, permissionError = null) }
            val result = runCatching {
                withContext(Dispatchers.IO) { wifiScanner.scan() }
            }
            result.onSuccess { candidates ->
                _uiState.update { it.copy(candidates = candidates, scanning = false) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        scanning = false,
                        permissionError = if (error is SecurityException) {
                            "需要附近 Wi-Fi 或定位权限才能扫描相机热点"
                        } else {
                            "扫描失败：${error.message ?: "未知错误"}"
                        },
                    )
                }
            }
        }
    }

    fun connect() {
        val ui = _uiState.value
        if (ui.ssid.isBlank()) {
            _uiState.update { it.copy(permissionError = "请输入相机屏幕上显示的 SSID") }
            return
        }
        _uiState.update { it.copy(permissionError = null) }
        connectionManager.connect(ui.ssid, ui.password, ui.baseUrl)
    }

    fun useCurrentWifi() {
        connectionManager.useCurrentWifi(_uiState.value.baseUrl)
    }

    fun disconnect() {
        connectionManager.disconnect()
    }

    /**
     * Single recovery entry point for the file browser. It decides whether a
     * fresh access grant is needed; callers only need the refresh button.
     */
    fun refreshCameraFiles() {
        if (CameraAccessGate.transferActive) {
            _uiState.update { it.copy(message = "传输进行中，完成后再刷新") }
            return
        }
        accessRecoveryAttempted = false
        if (accessReady) {
            loadDirectory(_uiState.value.currentDirectory)
        } else {
            requestAccessAndBrowse()
        }
    }

    fun reportPermissionDenied() {
        _uiState.update {
            it.copy(permissionError = "未授予 Wi-Fi 权限，无法连接相机")
        }
    }

    fun loadDirectory(directory: String) {
        if (CameraAccessGate.transferActive) {
            _uiState.update { it.copy(message = "传输进行中，暂不刷新相机文件") }
            return
        }
        val session = connectionManager.session.value
        if (session == null) {
            _uiState.update { it.copy(filesError = "相机未连接") }
            return
        }
        viewModelScope.launch {
            if (CameraAccessGate.transferActive) return@launch
            _uiState.update { it.copy(filesLoading = true, filesError = null) }
            val result = CameraAccessGate.withGalleryCamera {
                withContext(Dispatchers.IO) { session.protocol.browse(directory) }
            } ?: run {
                _uiState.update { it.copy(filesLoading = false) }
                return@launch
            }
            when (result) {
                is LumixResult.Success -> {
                    accessReady = true
                    accessRecoveryAttempted = false
                    _uiState.update {
                        it.copy(
                            currentDirectory = directory,
                            files = result.value.sortedWith(
                                compareByDescending<CameraFile> { file -> file.isDirectory }
                                    .thenByDescending { file ->
                                        file.capturedAtEpochSeconds ?: Long.MIN_VALUE
                                    }
                                    .thenByDescending { file ->
                                        cameraFileNumber(file) ?: Long.MIN_VALUE
                                    }
                                    .thenByDescending { file -> file.name.lowercase() },
                            ),
                            filesLoading = false,
                            selectedPaths = emptySet(),
                        )
                    }
                    // Thumbnails are loaded lazily only when preview is on.
                }

                is LumixResult.Failure -> {
                    val message = result.error.message ?: "读取相机文件失败"
                    // UPnP 403/500 after a camera switch means the grant or
                    // the cached directory is stale; recover once via access.
                    if (!accessRecoveryAttempted && !CameraAccessGate.transferActive) {
                        // A stale/missing grant is the normal case after a
                        // camera switch. Recover once through the access flow.
                        accessReady = false
                        accessRecoveryAttempted = true
                        _uiState.update {
                            it.copy(
                                filesLoading = false,
                                filesError = null,
                                message = "重新获取相机访问权限…",
                            )
                        }
                        requestAccessAndBrowse()
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            filesLoading = false,
                            filesError = message,
                        )
                    }
                }
            }
        }
    }

    fun setViewMode(mode: FileViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun setFileFilter(filter: FileFilter) {
        _uiState.update {
            it.copy(
                fileFilter = filter,
                selectedPaths = emptySet(),
                preview = CameraPreviewUiState(),
                previewQueue = emptyList(),
                previewIndex = 0,
            )
        }
    }

    fun toggleSelectAll(files: List<CameraFile>) {
        val paths = files.filterNot { it.isDirectory }.map { it.path }.toSet()
        if (paths.isEmpty()) return
        _uiState.update { state ->
            val allSelected = state.selectedPaths.containsAll(paths)
            state.copy(
                selectedPaths = if (allSelected) {
                    state.selectedPaths - paths
                } else {
                    state.selectedPaths + paths
                },
            )
        }
    }

    fun setPreviewEnabled(enabled: Boolean) {
        val state = _uiState.value
        if (enabled && (state.filesLoading || state.files.isEmpty())) {
            _uiState.update { it.copy(message = "等文件全部读取结束后才能开启预览") }
            return
        }
        _uiState.update { it.copy(previewEnabled = enabled) }
        if (!enabled) closePreview()
    }

    fun openPreview(file: CameraFile) {
        if (file.isDirectory || !_uiState.value.previewEnabled || _uiState.value.filesLoading) return
        loadPreview(listOf(file), 0)
    }

    fun openPreviewAll(files: List<CameraFile>) {
        if (!_uiState.value.previewEnabled || _uiState.value.filesLoading) return
        val queue = files.filterNot { it.isDirectory }
        if (queue.isEmpty()) return
        loadPreview(queue, 0)
    }

    fun previousPreview() {
        val state = _uiState.value
        if (!state.previewEnabled || state.previewIndex <= 0) return
        loadPreview(state.previewQueue, state.previewIndex - 1)
    }

    fun nextPreview() {
        val state = _uiState.value
        if (!state.previewEnabled || state.previewIndex >= state.previewQueue.lastIndex) return
        loadPreview(state.previewQueue, state.previewIndex + 1)
    }

    private fun loadPreview(queue: List<CameraFile>, index: Int) {
        val file = queue.getOrNull(index) ?: return
        if (file.isDirectory) return
        previewJob?.cancel()
        _uiState.update {
            it.copy(
                previewQueue = queue,
                previewIndex = index,
                preview = CameraPreviewUiState(file = file, loading = true),
            )
        }
        previewJob = viewModelScope.launch {
            val session = connectionManager.session.value
            if (session == null) {
                _uiState.update {
                    it.copy(
                        preview = CameraPreviewUiState(
                            file = file,
                            loading = false,
                            error = "相机未连接",
                        ),
                    )
                }
                return@launch
            }
            val fetched = CameraAccessGate.withGalleryCamera {
                withContext(Dispatchers.IO) {
                    val access = session.protocol.prepareTransferAccess()
                    if (access is LumixResult.Failure) return@withContext null
                    fetchPreviewImage(session, file)
                }
            }
            val capturedAt = fetched?.capturedAtEpochSeconds
            _uiState.update {
                it.copy(
                    captureDates = if (capturedAt != null) {
                        it.captureDates + (file.path to capturedAt)
                    } else {
                        it.captureDates
                    },
                    preview = CameraPreviewUiState(
                        file = file,
                        bitmap = fetched?.bitmap,
                        capturedAtEpochSeconds = capturedAt,
                        loading = false,
                        error = when {
                            CameraAccessGate.transferActive -> "传输进行中，暂不预览"
                            fetched?.bitmap == null -> "无法读取预览图"
                            else -> null
                        },
                    ),
                )
            }
        }
    }

    fun closePreview() {
        previewJob?.cancel()
        _uiState.update {
            it.copy(
                preview = CameraPreviewUiState(),
                previewQueue = emptyList(),
                previewIndex = 0,
            )
        }
    }

    fun requestThumbnail(file: CameraFile) {
        if (file.isDirectory || !_uiState.value.previewEnabled) return
        if (_uiState.value.thumbnails.containsKey(file.path)) return
        if (!thumbnailRequests.add(file.path)) return
        viewModelScope.launch {
            try {
                val session = connectionManager.session.value ?: return@launch
                if (CameraAccessGate.transferActive) return@launch
                val fetched = CameraAccessGate.withGalleryCamera {
                    withContext(Dispatchers.IO) {
                        val access = session.protocol.prepareTransferAccess()
                        if (access is LumixResult.Failure) return@withContext null
                        fetchThumbnail(session, file)
                    }
                } ?: return@launch
                if (connectionManager.session.value?.protocol !== session.protocol) return@launch
                _uiState.update {
                    it.copy(
                        thumbnails = fetched.bitmap?.let { bitmap ->
                            it.thumbnails + (file.path to bitmap)
                        } ?: it.thumbnails,
                        captureDates = fetched.capturedAtEpochSeconds?.let { date ->
                            it.captureDates + (file.path to date)
                        } ?: it.captureDates,
                    )
                }
            } finally {
                thumbnailRequests.remove(file.path)
            }
        }
    }

    private suspend fun fetchPreviewImage(
        session: CameraSession,
        file: CameraFile,
    ): FetchedImage? {
        if (file.kind == MediaKind.JPEG) {
            fetchBytes(session, file, MAX_PREVIEW_BYTES)?.let { data ->
                val bitmap = decodePreview(data.bytes, maxDimension = 2048)
                if (bitmap != null) {
                    return FetchedImage(
                        bitmap = bitmap,
                        capturedAtEpochSeconds = exifDate(data.bytes)
                            ?: data.capturedAtEpochSeconds,
                    )
                }
            }
        }
        val access = session.protocol.prepareTransferAccess()
        if (access is LumixResult.Failure) return null
        return fetchThumbnail(session, file)
    }

    private suspend fun fetchThumbnail(
        session: CameraSession,
        file: CameraFile,
    ): FetchedImage? {
        val thumb = CameraFile(
            path = thumbnailPath(file.path),
            name = "preview.jpg",
            sizeBytes = 0,
        )
        val data = fetchBytes(session, thumb, MAX_THUMBNAIL_BYTES) ?: return null
        return FetchedImage(
            bitmap = decodePreview(data.bytes, maxDimension = 900),
            capturedAtEpochSeconds = exifDate(data.bytes) ?: data.capturedAtEpochSeconds,
        )
    }

    private suspend fun fetchBytes(
        session: CameraSession,
        file: CameraFile,
        maxBytes: Int,
    ): FetchedBytes? {
        val result = session.protocol.openFileConnection(file)
        if (result is LumixResult.Failure) return null
        val connection = (result as LumixResult.Success).value
        return try {
            if (connection.responseCode !in 200..299) return null
            if (connection.contentLengthLong > maxBytes) return null
            val lastModified = connection.getHeaderFieldDate("Last-Modified", 0L)
            val bytes = connection.inputStream.use { input ->
                val buffer = ByteArray(128 * 1024)
                val out = java.io.ByteArrayOutputStream()
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) return null
                    out.write(buffer, 0, read)
                }
                out.toByteArray()
            }
            FetchedBytes(
                bytes = bytes,
                capturedAtEpochSeconds = lastModified
                    .takeIf { it > 0L }
                    ?.div(1_000L),
            )
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { connection.disconnect() }
        }
    }

    private fun exifDate(bytes: ByteArray): Long? {
        return runCatching {
            val exif = ExifInterface(ByteArrayInputStream(bytes))
            val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                ?: return null
            LocalDateTime.parse(
                raw,
                DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US),
            ).atZone(ZoneId.systemDefault()).toEpochSecond()
        }.getOrNull()
    }

    private fun decodePreview(bytes: ByteArray, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.getOrNull()
    }

    private fun thumbnailPath(path: String): String {
        val name = path.substringAfterLast('/')
        val base = name.substringBeforeLast('.', name)
        val thumb = if (base.startsWith("DO")) {
            "DT" + base.substring(2) + ".JPG"
        } else {
            base + ".JPG"
        }
        return path.substringBeforeLast('/') + "/" + thumb
    }

    override fun onCleared() {
        thumbnailJob?.cancel()
        previewJob?.cancel()
        super.onCleared()
    }

    fun openCameraFiles() {
        // Reopening the browser must not rescan hundreds of camera items.
        // Refresh is the explicit full reload; the existing list is reused.
        if (accessReady && _uiState.value.files.isNotEmpty()) return
        if (accessReady) {
            loadDirectory("/DCIM")
        } else {
            requestAccessAndBrowse()
        }
    }

    private fun authorizeInBackground(session: CameraSession) {
        if (accessRequestInFlight) return
        accessRequestInFlight = true
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        message = "正在请求相机访问，请在相机屏幕上确认连接…",
                        filesError = null,
                    )
                }
                when (
                    val result = session.protocol.requestCameraAccess { status ->
                        _uiState.update { state -> state.copy(message = status) }
                    }
                ) {
                    is LumixResult.Success -> {
                        session.protocol.readModel()?.let { connectionManager.updateModel(it) }
                        accessReady = true
                        _uiState.update {
                            it.copy(message = "已获得访问权限，点击浏览文件或刷新")
                        }
                    }

                    is LumixResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                message = "自动授权失败，点击刷新重试：${result.error.message}",
                            )
                        }
                    }
                }
            } finally {
                accessRequestInFlight = false
            }
        }
    }

    /**
     * The G100D drops the Wi-Fi session if its own menu is touched, so the
     * user must trigger this fixed playmode command from the app. It is never
     * sent automatically.
     */
    fun requestAccessAndBrowse() {
        if (accessRequestInFlight) {
            _uiState.update { it.copy(message = "正在请求相机访问，请稍候…") }
            return
        }
        if (CameraAccessGate.transferActive) {
            _uiState.update { it.copy(message = "传输进行中，完成后再请求相机访问") }
            return
        }
        val session = connectionManager.session.value
        if (session == null) {
            _uiState.update { it.copy(filesError = "相机未连接") }
            return
        }
        accessRequestInFlight = true
        viewModelScope.launch {
            try {
            Diagnostics.info("用户确认请求相机访问，等待相机屏幕确认")
            _uiState.update {
                it.copy(
                    filesLoading = true,
                    filesError = null,
                    message = "正在请求访问，请在相机屏幕上确认连接…",
                )
            }
            when (
                val result = session.protocol.requestCameraAccess { status ->
                    _uiState.update { state -> state.copy(message = status) }
                }
            ) {
                is LumixResult.Success -> {
                    session.protocol.readModel()?.let { connectionManager.updateModel(it) }
                    delay(500)
                    val playback = session.protocol.enterPlaybackMode()
                    if (playback is LumixResult.Failure) {
                        // S5M2 can browse the play-mode UPnP directory even
                        // when playmode reports err_busy; try it anyway.
                        Diagnostics.info("playmode 未成功，继续尝试 Browse：${playback.error.message}")
                        _uiState.update {
                            it.copy(message = "playmode 返回 busy，正在尝试直接读取文件")
                        }
                    } else {
                        delay(1_000)
                        _uiState.update { it.copy(message = "已获得访问权限，正在读取文件") }
                    }
                    accessRecoveryAttempted = true
                    loadDirectory("/DCIM")
                }

                is LumixResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            filesLoading = false,
                            filesError = "访问请求失败：${result.error.message ?: "未知错误"}",
                        )
                    }
                }
            }
            } finally {
                accessRequestInFlight = false
            }
        }
    }

    fun enterPlaybackAndBrowse() {
        val session = connectionManager.session.value
        if (session == null) {
            _uiState.update { it.copy(filesError = "相机未连接") }
            return
        }
        viewModelScope.launch {
            Diagnostics.info("用户确认发送 playmode")
            _uiState.update {
                it.copy(filesLoading = true, filesError = null, message = "正在切换到播放模式…")
            }
            when (val result = session.protocol.enterPlaybackMode()) {
                is LumixResult.Success -> {
                    delay(1_200)
                    when (val info = session.protocol.readContentInfo()) {
                        is LumixResult.Success -> {
                            Diagnostics.info("get_content_info: ${info.value.take(2_000)}")
                            val count = Regex("<total_content_number>(\\d+)</total_content_number>")
                                .find(info.value)
                                ?.groupValues
                                ?.getOrNull(1)
                                ?.toIntOrNull()
                            _uiState.update {
                                it.copy(
                                    message = if (count != null) {
                                        "相机报告 $count 个文件，正在读取列表"
                                    } else {
                                        "已切换到播放模式，正在读取文件"
                                    },
                                )
                            }
                        }

                        is LumixResult.Failure -> {
                            Diagnostics.info("get_content_info 失败：${info.error.message}")
                            _uiState.update {
                                it.copy(message = "已切换到播放模式，正在读取文件")
                            }
                        }
                    }
                    accessRecoveryAttempted = true
                    loadDirectory("/DCIM")
                }

                is LumixResult.Failure -> {
                    Diagnostics.info("playmode 失败，继续尝试 Browse：${result.error.message}")
                    _uiState.update {
                        it.copy(
                            message = "playmode 返回 busy，正在尝试直接读取文件",
                            filesError = null,
                        )
                    }
                    accessRecoveryAttempted = true
                    loadDirectory("/DCIM")
                }
            }
        }
    }

    fun navigateUp() {
        val current = _uiState.value.currentDirectory
        if (current == "/DCIM" || current == "/") {
            loadDirectory("/DCIM")
            return
        }
        loadDirectory(current.substringBeforeLast('/', "/DCIM"))
    }

    fun toggleSelection(path: String) {
        _uiState.update { state ->
            val next = state.selectedPaths.toMutableSet()
            if (!next.add(path)) next.remove(path)
            state.copy(selectedPaths = next)
        }
    }

    fun enqueueSelected() {
        val state = _uiState.value
        val selected = state.files.filter { !it.isDirectory && it.path in state.selectedPaths }
        if (selected.isEmpty()) {
            _uiState.update { it.copy(message = "请先选择要下载的文件") }
            return
        }
        viewModelScope.launch {
            CameraAccessGate.transferActive = true
            try {
                thumbnailJob?.cancelAndJoin()
                previewJob?.cancel()
                transferRepository.enqueue(
                    selected.map { file ->
                        NewTransfer(
                            cameraPath = file.path,
                            fileName = file.name,
                            sizeBytes = file.sizeBytes,
                            mimeType = mimeTypeFor(file.kind),
                        )
                    },
                )
                TransferService.start(appContext)
            } catch (error: Throwable) {
                CameraAccessGate.transferActive = false
                _uiState.update { it.copy(message = "加入传输队列失败：${error.message ?: "未知错误"}") }
                return@launch
            }
            _uiState.update {
                it.copy(
                    selectedPaths = emptySet(),
                    message = "已加入传输队列（${selected.size} 个文件）",
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun clearFileError() {
        _uiState.update { it.copy(filesError = null) }
    }

    private fun mimeTypeFor(kind: MediaKind): String = when (kind) {
        MediaKind.JPEG -> "image/jpeg"
        MediaKind.RAW -> "application/octet-stream"
        MediaKind.VIDEO -> "video/mp4"
        MediaKind.UNKNOWN -> "application/octet-stream"
    }

    /**
     * G100D names are P1002205.JPG / 100-2205 / DO01002205.JPG. Strip
     * separators so the numeric sequence sorts the same in every form.
     */
    private fun cameraFileNumber(file: CameraFile): Long? =
        file.name.filter { it.isDigit() }
            .takeIf { it.isNotEmpty() }
            ?.toLongOrNull()

    companion object {
        fun factory(
            connectionManager: ConnectionManager,
            wifiScanner: WifiScanner,
            transferRepository: TransferRepository,
            context: Context,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ConnectViewModel(
                    connectionManager = connectionManager,
                    wifiScanner = wifiScanner,
                    transferRepository = transferRepository,
                    appContext = context.applicationContext,
                )
            }
        }
    }
}
