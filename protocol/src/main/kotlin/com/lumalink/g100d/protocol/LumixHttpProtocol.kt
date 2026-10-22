package com.lumalink.g100d.protocol

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

/**
 * HTTP CGI implementation of [LumixProtocol]. Capabilities are intentionally
 * conservative: only metadata/browse/download are exposed in phase 2. Live
 * view, capture, and recording stay behind a later capability probe.
 */
class LumixHttpProtocol(
    override val baseUrl: String,
    private val logger: LumixLogger? = null,
    private val http: LumixHttpClient = LumixHttpClient(baseUrl, logger = logger),
    private val upnp: UpnpContentDirectoryClient = UpnpContentDirectoryClient(
        baseUrl = baseUrl,
        logger = logger,
    ),
    private val control: LumixControlClient = LumixControlClient(
        baseUrl = baseUrl,
        logger = logger,
    ),
    private val contentNumberBrowse: Boolean = true,
) : LumixProtocol {

    private val transferAccessMutex = Mutex()

    private val knownCapabilities = setOf(
        LumixCapability.READ_STATE,
        LumixCapability.BROWSE,
        LumixCapability.DOWNLOAD,
    )
    private var loggedStatusBody = false

    override suspend fun handshake(): LumixResult<CameraModel> {
        val infoUrl = LumixEndpoints.info(baseUrl)
        val info = http.getText(infoUrl)
        if (info is LumixResult.Success) {
            http.logBody(infoUrl, info.value.text)
            if (!info.value.text.contains("<result>err_", ignoreCase = true)) {
                return LumixResult.Success(LumixInfoParser.parse(info.value.text))
            }
        }

        // S5M2 and newer LUMIX Sync bodies return err_critical for plain
        // getinfo, but capability contains productinfo/modelname.
        val capabilityUrl = LumixEndpoints.infoCapability(baseUrl)
        val capability = http.getText(capabilityUrl)
        if (capability is LumixResult.Success) {
            http.logBody(capabilityUrl, capability.value.text)
            if (!capability.value.text.contains("<result>err_", ignoreCase = true)) {
                return LumixResult.Success(LumixInfoParser.parse(capability.value.text))
            }
        }

        val error = (info as? LumixResult.Failure)?.error
        if (error is LumixError.Offline || error is LumixError.Timeout) {
            return LumixResult.Failure(error)
        }

        // Some firmware does not implement getinfo; a successful getstate is
        // still enough to prove the camera is reachable and read-only.
        val stateUrl = LumixEndpoints.state(baseUrl)
        return when (val state = http.getText(stateUrl)) {
            is LumixResult.Success -> {
                http.logBody(stateUrl, state.value.text)
                // S5M2 returns err_critical for getinfo/getstate until the
                // camera-screen access is approved. HTTP 200 still proves the
                // camera is reachable, so connect provisionally and let the
                // access flow upgrade the model via capability.
                LumixResult.Success(CameraModel(model = "LUMIX"))
            }

            is LumixResult.Failure -> LumixResult.Failure(state.error)
        }
    }

    /** Refresh the model after access approval; S5M2 only answers capability then. */
    suspend fun readModel(): CameraModel? {
        val url = LumixEndpoints.infoCapability(baseUrl)
        val response = http.getText(url) as? LumixResult.Success ?: return null
        http.logBody(url, response.value.text)
        if (response.value.text.contains("<result>err_", ignoreCase = true)) return null
        return LumixInfoParser.parse(response.value.text)
    }

    override suspend fun readStatus(): LumixResult<CameraStatus> {
        val url = LumixEndpoints.state(baseUrl)
        return when (val response = http.getText(url)) {
            is LumixResult.Success -> {
                if (!loggedStatusBody) {
                    loggedStatusBody = true
                    http.logBody(url, response.value.text)
                }
                LumixResult.Success(LumixStatusParser.parse(response.value.text))
            }

            is LumixResult.Failure -> LumixResult.Failure(response.error)
        }
    }

    override suspend fun browse(directory: String): LumixResult<List<CameraFile>> {
        // UPnP is the only path that returns the real P100xxxx names and item
        // IDs. Try it first (including the /Lumix/Server0/CDS_control variant);
        // content-number/cam.cgi remain read-only fallbacks.
        val upnpResult = upnp.browseAll(
            onAuthRefresh = {
                requestCameraAccess() is LumixResult.Success
            },
        )
        if (upnpResult is LumixResult.Success) {
            // S5M2 can report a small TotalMatches while get_content_info
            // knows there are more files. If so, ignore the UPnP total and
            // continue paging until an empty/repeated page.
            val reportedTotal = readContentTotal()
            if (reportedTotal > upnpResult.value.size) {
                val extended = upnp.browseAll(expectedMinimum = reportedTotal)
                if (extended is LumixResult.Success &&
                    extended.value.size > upnpResult.value.size
                ) {
                    return extended
                }
            }
            // A successful Browse response is authoritative, including an
            // empty album. Never fall back to guessed 50001 filenames: that
            // hangs the camera for 30 seconds per candidate.
            return upnpResult
        }

        // Without a valid UPnP Browse result, a session cannot help; do not
        // keep hammering the camera with guessed filenames.
        if (CameraSessionStore.sessionId != null) {
            return LumixResult.Failure((upnpResult as LumixResult.Failure).error)
        }

        if (contentNumberBrowse) {
            val contentResult = browseByContentNumber()
            if (contentResult is LumixResult.Success && contentResult.value.isNotEmpty()) {
                return contentResult
            }
            if (contentResult is LumixResult.Success && contentResult.value.isEmpty()) {
                // The camera explicitly reported zero contents; do not spam 403s.
                val info = readContentInfo()
                if (info is LumixResult.Success &&
                    Regex("<total_content_number>(\\d+)</total_content_number>")
                        .find(info.value)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toIntOrNull() == 0
                ) {
                    return contentResult
                }
            }
        }

        var lastError: LumixError? = (upnpResult as? LumixResult.Failure)?.error
        var sawSuccess = upnpResult is LumixResult.Success

        for (candidate in browseCandidates(directory)) {
            val url = LumixEndpoints.browse(baseUrl, candidate)
            when (val response = http.getText(url)) {
                is LumixResult.Success -> {
                    val text = response.value.text
                    http.logBody(url, text)
                    if (text.contains("err_")) {
                        lastError = LumixError.ProtocolMismatch(
                            "cam.cgi browse returned an error: ${text.take(200)}",
                        )
                        continue
                    }
                    sawSuccess = true
                    val files = LumixBrowseParser.parse(text, directory)
                    if (files.isNotEmpty()) {
                        return LumixResult.Success(files)
                    }
                }

                is LumixResult.Failure -> lastError = response.error
            }
        }

        probePtpPort()
        return if (sawSuccess) {
            LumixResult.Success(emptyList())
        } else {
            LumixResult.Failure(lastError ?: LumixError.Offline())
        }
    }

    /**
     * Diagnostic only: establish and immediately close a TCP connection to the
     * standard PTP/IP port. No PTP packet is sent, so this cannot change camera
     * state. It tells us whether LUMIX Sync's PTP/IP path is plausible.
     */
    private suspend fun probePtpPort() {
        val host = runCatching { baseUrl.toHttpUrl().host }.getOrNull() ?: return
        if (!HostGuard.isLocalHost(host)) return
        for (port in listOf(15740, 50001)) {
            var lastError: String? = null
            var success: String? = null
            for (includeVersion in listOf(true, false)) {
                when (val result = PtpIpProbe.probe(host, port, includeVersion)) {
                    is LumixResult.Success -> {
                        success =
                            "$host:$port ${result.value} version=${if (includeVersion) "yes" else "no"}"
                        break
                    }

                    is LumixResult.Failure -> {
                        lastError = result.error.message
                    }
                }
            }
            logger?.logBody(
                "ptp-probe",
                success ?: "$host:$port unavailable: ${lastError ?: "unknown"}",
            )
        }
    }

    /**
     * G100D content URLs use a 1-based content number, e.g. /DL10068.jpg on
     * :50001. We probe HEAD (read-only) and only surface entries that exist.
     */
    private suspend fun browseByContentNumber(): LumixResult<List<CameraFile>> {
        val info = readContentInfo()
        if (info is LumixResult.Failure) return info
        val text = (info as LumixResult.Success).value
        val total = Regex("<total_content_number>(\\d+)</total_content_number>")
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: return LumixResult.Success(emptyList())
        if (total <= 0) return LumixResult.Success(emptyList())

        val host = baseUrl.toHttpUrl().host
        val base = "http://$host:50001"
        val patterns = listOf(
            // Public Lumix Tools uses :50001/DO<itemId>.<ext>, where itemId is
            // 8 digits (folder 0100 + 4-digit sequence).
            ContentUrlPattern("DO-50001") { "$base/DO0100${pad4(it)}.jpg" },
            ContentUrlPattern("DL-50001") { "$base/DL100$it.jpg" },
            ContentUrlPattern("P-50001") { "$base/P100${pad4(it)}.JPG" },
            ContentUrlPattern("DCIM-50001") {
                "$base/DCIM/100_PANA/P100${pad4(it)}.JPG"
            },
            ContentUrlPattern("DCIM-80") {
                "http://$host/DCIM/100_PANA/P100${pad4(it)}.JPG"
            },
        )
        val discoveryIndices = listOf(total, maxOf(1, total - 1), 1, 2, total + 1)
            .distinct()

        var chosen: ContentUrlPattern? = null
        var chosenIndex: Int? = null
        for (pattern in patterns) {
            for (index in discoveryIndices) {
                val file = probeUrl(
                    pattern.urlFor(index),
                    logFailures = index == total,
                )
                if (file != null) {
                    chosen = pattern
                    chosenIndex = index
                    logger?.logBody(
                        "content-probe",
                        "pattern=${pattern.label} firstIndex=$index url=${file.path}",
                    )
                    break
                }
            }
            if (chosen != null) break
        }

        val selected = chosen
        if (selected == null) {
            logger?.logBody(
                "content-number",
                "total=$total no filename pattern matched",
            )
            return LumixResult.Success(emptyList())
        }

        val files = (1..minOf(total, 500)).mapNotNull { index ->
            probeUrl(selected.urlFor(index))
        }
        logger?.logBody(
            "content-number",
            "total=$total pattern=${selected.label} firstIndex=$chosenIndex found=${files.size}",
        )
        return LumixResult.Success(files)
    }

    private suspend fun probeUrl(
        url: String,
        logFailures: Boolean = false,
    ): CameraFile? {
        val variants = buildList {
            add(url)
            when {
                url.endsWith(".JPG") -> add(url.dropLast(4) + ".jpg")
                url.endsWith(".jpg") -> add(url.dropLast(4) + ".JPG")
            }
        }
        for (candidate in variants) {
            val byHead = http.head(candidate, logRequest = false)
            when (byHead) {
                is LumixResult.Success -> {
                    val size = byHead.value ?: 0L
                    if (size > 0L) return materialize(candidate, size)
                }

                is LumixResult.Failure -> {
                    if (logFailures) {
                        logger?.logBody(
                            "content-probe",
                            "$candidate HEAD -> ${byHead.error.message}",
                        )
                    }
                }
            }

            // Some firmware returns 404/405 for HEAD but serves GET normally.
            when (val byGet = http.probeFile(candidate)) {
                is LumixResult.Success -> return materialize(candidate, byGet.value ?: 0L)
                is LumixResult.Failure -> {
                    if (logFailures) {
                        logger?.logBody(
                            "content-probe",
                            "$candidate GET -> ${byGet.error.message}",
                        )
                    }
                }
            }
        }
        return null
    }

    private fun pad4(value: Int): String = value.toString().padStart(4, '0')

    private data class ContentUrlPattern(
        val label: String,
        val urlFor: (Int) -> String,
    )

    private fun materialize(url: String, size: Long): CameraFile {
        val name = url.substringAfterLast('/')
        return CameraFile(
            path = url,
            name = name,
            sizeBytes = size,
            kind = MediaKind.fromFileName(name),
        )
    }

    /**
     * Panasonic firmware varies between `dir=/DCIM`, `dir=DCIM`, and a bare
     * folder name. These are all read-only probes; the first non-empty result
     * wins.
     */
    private fun browseCandidates(directory: String): List<String> {
        val normalized = directory.trim()
        val candidates = linkedSetOf<String>()
        candidates += normalized
        val withoutLeadingSlash = normalized.trimStart('/')
        if (withoutLeadingSlash.isNotEmpty()) {
            candidates += withoutLeadingSlash
        }
        val leaf = normalized.trim('/').substringAfterLast('/')
        if (leaf.isNotEmpty()) {
            candidates += leaf
        }
        return candidates.toList()
    }

    override suspend fun capabilities(): Set<LumixCapability> = knownCapabilities

    /**
     * Opens the camera file for streaming. The caller owns the response and
     * must close it. Downloads are still guarded by [ReadOnlyGuard].
     */
    suspend fun openFile(file: CameraFile): LumixResult<Response> {
        return http.open(LumixEndpoints.file(baseUrl, file.path))
    }

    /**
     * The G100D grants one successful 50001 download per Browse. Refresh the
     * directory before every queued file; if access has already expired,
     * request it again and re-enter playback.
     */
    suspend fun prepareTransferAccess(
        allowUserAccessRequest: Boolean = true,
        forceReauthorize: Boolean = false,
    ): LumixResult<Unit> = transferAccessMutex.withLock {
        if (!forceReauthorize) {
            val firstBrowse = upnp.refreshAccess()
            if (firstBrowse is LumixResult.Success) {
                logger?.logBody("transfer-access", "browse=ok refresh=1")
                return@withLock LumixResult.Success(Unit)
            }
        }
        if (!allowUserAccessRequest) {
            logger?.logBody("transfer-access", "browse=failed no-user-request")
            return@withLock LumixResult.Failure(
                LumixError.ProtocolMismatch("相机访问已失效，请回到连接页重新授权"),
            )
        }

        // A 50001 download consumes the Browse grant. The next file needs a
        // fresh accctrl grant, not just another Browse (which returns 403).
        logger?.logBody("transfer-access", "reauthorize request-access")
        val access = requestCameraAccess()
        if (access is LumixResult.Failure) return@withLock access
        // Some LUMIX Sync bodies (S5M2) report err_busy for playmode even
        // though the play-mode UPnP Browse already works. Try Browse even if
        // playmode failed, and only fail if Browse also fails.
        val playback = enterPlaybackMode()
        logger?.logBody(
            "transfer-access",
            "playmode=" + if (playback is LumixResult.Success) "ok" else "failed",
        )
        when (val browse = upnp.refreshAccess()) {
            is LumixResult.Success -> {
                logger?.logBody("transfer-access", "browse=ok reauthorized")
                LumixResult.Success(Unit)
            }

            is LumixResult.Failure -> {
                if (playback is LumixResult.Failure) playback else browse
            }
        }
    }

    suspend fun prepareFileCall(
        file: CameraFile,
        rangeStart: Long? = null,
    ): LumixResult<Call> {
        return http.prepareCall(
            url = LumixEndpoints.file(baseUrl, file.path),
            method = "GET",
            rangeStart = rangeStart,
        )
    }

    /**
     * Download path used by the transfer worker. It mirrors the known-good
     * Lumix client (HttpURLConnection + identity encoding) instead of OkHttp,
     * because the camera's 50001 server resets OkHttp-style connections.
     */
    suspend fun openFileConnection(
        file: CameraFile,
        rangeStart: Long? = null,
        rangeEnd: Long? = null,
    ): LumixResult<HttpURLConnection> = withContext(Dispatchers.IO) {
        val url = try {
            URL(LumixEndpoints.file(baseUrl, file.path))
        } catch (error: Throwable) {
            return@withContext LumixResult.Failure(
                LumixError.ProtocolMismatch("Invalid file URL: ${error.message}"),
            )
        }
        if (!HostGuard.isLocalHost(url.host)) {
            return@withContext LumixResult.Failure(
                LumixError.ReadOnlyViolation("non-local file host: ${url.host}"),
            )
        }
        try {
            val connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 5_000
            // A reset 50001 grant can otherwise leave a read blocked for two
            // minutes, which also wedges the queue behind it.
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "Panasonic MIL DLNA CP UPnP/1.0")
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("Accept", "*/*")
            rangeStart?.let { start ->
                val range = if (rangeEnd != null && rangeEnd >= start) {
                    "bytes=$start-$rangeEnd"
                } else {
                    "bytes=$start-"
                }
                connection.setRequestProperty("Range", range)
            }
            CameraSessionStore.sessionId?.let {
                connection.setRequestProperty("X-SESSION_ID", it)
            }
            LumixResult.Success(connection)
        } catch (error: Throwable) {
            logger?.log(
                LumixHttpLogEntry(
                    timestamp = System.currentTimeMillis(),
                    method = "GET",
                    url = url.toString(),
                    statusCode = null,
                    durationMs = 0L,
                    error = error.message,
                ),
            )
            LumixResult.Failure(
                LumixError.Offline(error.message ?: "camera file connection failed", error),
            )
        }
    }

    suspend fun headFile(file: CameraFile): LumixResult<Long?> {
        return http.head(LumixEndpoints.file(baseUrl, file.path))
    }

    private suspend fun readContentTotal(): Int {
        val info = readContentInfo() as? LumixResult.Success ?: return 0
        return Regex("<total_content_number>(\\d+)</total_content_number>")
            .find(info.value)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
    }

    suspend fun readContentInfo(): LumixResult<String> {
        val url = LumixEndpoints.contentInfo(baseUrl)
        return when (val response = http.getText(url)) {
            is LumixResult.Success -> {
                http.logBody(url, response.value.text)
                LumixResult.Success(response.value.text)
            }

            is LumixResult.Failure -> LumixResult.Failure(response.error)
        }
    }

    /**
     * Fixed, user-confirmed command to switch the camera to playback mode.
     * Required because the G100D stops the Wi-Fi session if the camera's own
     * menus are touched after connecting.
     */
    suspend fun requestCameraAccess(
        onStatus: (String) -> Unit = {},
    ): LumixResult<String?> = control.requestAccess(onStatus)

    suspend fun enterPlaybackMode(): LumixResult<Unit> = control.enterPlaybackMode()

    fun httpClient(): LumixHttpClient = http
}
