package com.lumalink.g100d.protocol

import java.net.SocketTimeoutException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Deliberately tiny control surface. Every method maps to one fixed camera
 * command. There is no generic command API, no settings write, no firmware or
 * register access.
 *
 * Transfer-only build: the only camera state change is the user-confirmed
 * playback-mode command; this class never sends capture/record/stream commands.
 */
class LumixControlClient(
    baseUrl: String,
    private val client: OkHttpClient = defaultClient(),
    private val logger: LumixLogger? = null,
) {
    private val baseUrl = baseUrl.trimEnd('/')
    private val clientId = "4D454930-0100-1000-8001-" +
        UUID.randomUUID().toString().replace("-", "").take(12)

    suspend fun enterPlaybackMode(): LumixResult<Unit> {
        // S5M2 / LUMIX Sync bodies expect a capability/state/menu warm-up
        // before playmode. Without it the camera replies err_busy forever.
        rawGet(LumixEndpoints.infoCapability(baseUrl), "capability")
        rawGet(LumixEndpoints.state(baseUrl), "getstate")
        rawGet(LumixEndpoints.currentMenu(baseUrl), "curmenu")

        var last: LumixResult<Unit> = LumixResult.Failure(LumixError.ProtocolMismatch("playmode not attempted"))
        repeat(3) { attempt ->
            last = fixedCommand("mode=camcmd&value=playmode", "playmode")
            if (last is LumixResult.Success) return last
            val message = (last as? LumixResult.Failure)?.error?.message.orEmpty()
            if (!message.contains("err_busy")) return last
            if (attempt < 2) delay(1_200)
        }
        return last
    }

    /**
     * Requests content access and waits for the user to approve the app on the
     * camera screen. `ok_under_research*` is not success; the camera keeps
     * returning it until the user confirms, then returns `ok,...,<session>`.
     */
    suspend fun requestAccess(
        onStatus: (String) -> Unit = {},
    ): LumixResult<String?> = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + APPROVAL_TIMEOUT_MS
        var attempts = 0

        // Some G100D firmware accepts the challenge handshake even though the
        // description omits X_CamCapability. Probe it first; fall back to the
        // legacy request if the camera rejects req_acc_g.
        var identity = clientId
        var clientName = "LumaLink"
        var challengeMode = false
        val challengeUrl = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("cam.cgi")
            .addQueryParameter("mode", "accctrl")
            .addQueryParameter("type", "req_acc_g")
            .build()
            .toString()
        suspend fun probeChallenge(label: String): Boolean {
            when (val probe = rawGet(challengeUrl, label)) {
                is LumixResult.Success -> {
                    val fields = probe.value.trim().split(',')
                    val challenge = fields.getOrNull(1)?.trim()?.toLongOrNull(16)
                    if (fields.firstOrNull()?.trim() == "ok" && challenge != null &&
                        challenge in 0..0xffffffffL
                    ) {
                        challengeMode = true
                        identity = challengeEncode(clientId, challenge.toInt())
                        clientName = challengeEncode(clientName, challenge.toInt())
                    }
                    logger?.logBody(
                        "access",
                        "challengeProbe=ok mode=${if (challengeMode) "challenge" else "legacy"}",
                    )
                }

                is LumixResult.Failure -> {
                    logger?.logBody("access", "challengeProbe=failed mode=legacy")
                }
            }
            return challengeMode
        }

        probeChallenge("accctrl-probe")
        if (!challengeMode) {
            // S5M2 occasionally drops the first probe right after Wi-Fi
            // association; one short retry avoids falling back to the legacy
            // request (which returns err_param on this body).
            delay(120)
            probeChallenge("accctrl-probe-retry")
        }

        while (System.currentTimeMillis() < deadline) {
            attempts++
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("cam.cgi")
                .addQueryParameter("mode", "accctrl")
                .addQueryParameter("type", if (challengeMode) "req_acc_e" else "req_acc")
                .addQueryParameter("value", identity)
                .addQueryParameter("value2", clientName)
                .build()
                .toString()
            when (val result = rawGet(url, "accctrl")) {
                is LumixResult.Failure -> return@withContext result
                is LumixResult.Success -> {
                    val body = result.value.trim()
                    val status = if (body.startsWith("<")) {
                        Regex("<result>([^<]+)</result>")
                            .find(body)
                            ?.groupValues
                            ?.getOrNull(1)
                            ?.trim()
                            .orEmpty()
                    } else {
                        body.substringBefore(',').trim()
                    }
                    val fields = body.split(',')
                    val session = fields.getOrNull(4)?.trim()
                    logger?.logBody(
                        "access",
                        "attempt=$attempts mode=${if (challengeMode) "challenge" else "legacy"} " +
                            "status=$status fields=${fields.size} session=${session != null}",
                    )
                    when (status) {
                        "ok_under_research", "ok_under_research_no_msg" -> {
                            onStatus("请在相机屏幕上确认连接（第 $attempts 次等待）")
                            delay(1_000)
                        }

                        "err_busy" -> {
                            onStatus("相机忙，等待后重试（第 $attempts 次）")
                            delay(1_000)
                        }

                        "ok", "upload_bt", "transfer" -> {
                            CameraSessionStore.set(session)
                            return@withContext LumixResult.Success(session)
                        }

                        else -> {
                            // Some bodies only expose err_param for the
                            // legacy request; retry once through challenge.
                            if (!challengeMode && status == "err_param") {
                                if (probeChallenge("accctrl-probe-fallback")) {
                                    onStatus("正在切换授权方式…")
                                    continue
                                }
                            }
                            return@withContext LumixResult.Failure(
                                LumixError.ProtocolMismatch("access rejected: ${status.ifBlank { "empty" }}"),
                            )
                        }
                    }
                }
            }
        }
        LumixResult.Failure(LumixError.Timeout())
    }

    private suspend fun rawGet(url: String, label: String): LumixResult<String> =
        withContext(Dispatchers.IO) {
            val request = try {
                Request.Builder()
                    .url(url)
                    .cameraHeaders()
                    .get()
                    .build()
            } catch (error: IllegalArgumentException) {
                return@withContext LumixResult.Failure(
                    LumixError.ProtocolMismatch("Invalid $label URL: ${error.message}"),
                )
            }
            if (!HostGuard.isLocalHost(request.url.host)) {
                return@withContext LumixResult.Failure(
                    LumixError.ReadOnlyViolation("non-local camera host: ${request.url.host}"),
                )
            }
            val startedAt = System.nanoTime()
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    logger?.log(
                        LumixHttpLogEntry(
                            timestamp = System.currentTimeMillis(),
                            method = "GET",
                            url = url,
                            statusCode = response.code,
                            durationMs = (System.nanoTime() - startedAt) / 1_000_000L,
                        ),
                    )
                    // Never log the access reply body: it can contain a session token.
                    if (!response.isSuccessful) {
                        return@withContext LumixResult.Failure(
                            LumixError.ProtocolMismatch("$label HTTP ${response.code}"),
                        )
                    }
                    LumixResult.Success(body)
                }
            } catch (error: SocketTimeoutException) {
                LumixResult.Failure(LumixError.Timeout(cause = error))
            } catch (error: Throwable) {
                LumixResult.Failure(
                    LumixError.Offline(error.message ?: "$label request failed", error),
                )
            }
        }

    private suspend fun fixedCommand(query: String, label: String): LumixResult<Unit> =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/cam.cgi?$query"
            val request = try {
                Request.Builder()
                    .url(url)
                    .cameraHeaders()
                    .get()
                    .build()
            } catch (error: IllegalArgumentException) {
                return@withContext LumixResult.Failure(
                    LumixError.ProtocolMismatch("Invalid $label URL: ${error.message}"),
                )
            }
            if (!HostGuard.isLocalHost(request.url.host)) {
                return@withContext LumixResult.Failure(
                    LumixError.ReadOnlyViolation("non-local camera host: ${request.url.host}"),
                )
            }

            val startedAt = System.nanoTime()
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    logger?.log(
                        LumixHttpLogEntry(
                            timestamp = System.currentTimeMillis(),
                            method = "GET",
                            url = url,
                            statusCode = response.code,
                            durationMs = (System.nanoTime() - startedAt) / 1_000_000L,
                        ),
                    )
                    logger?.logBody(url, body)
                    if (!response.isSuccessful) {
                        return@withContext LumixResult.Failure(
                            LumixError.ProtocolMismatch("$label HTTP ${response.code}"),
                        )
                    }
                    if (body.contains("err_")) {
                        return@withContext LumixResult.Failure(
                            LumixError.ProtocolMismatch("$label rejected: ${body.take(300)}"),
                        )
                    }
                    LumixResult.Success(Unit)
                }
            } catch (error: SocketTimeoutException) {
                LumixResult.Failure(LumixError.Timeout(cause = error))
            } catch (error: Throwable) {
                LumixResult.Failure(
                    LumixError.Offline(error.message ?: "$label request failed", error),
                )
            }
        }

    private fun Request.Builder.cameraHeaders(): Request.Builder {
        header("User-Agent", "Panasonic MIL DLNA CP UPnP/1.0")
        header("Accept-Encoding", "identity")
        CameraSessionStore.sessionId?.let { header("X-SESSION_ID", it) }
        return this
    }

    companion object {
        private const val APPROVAL_TIMEOUT_MS = 120_000L
        /** req_acc_e wire encoding: padded UTF-16 code units, challenge XOR, little-endian hex. */
        private fun challengeEncode(value: String, challenge: Int): String {
            val padded = value.padEnd((value.length + 3) / 4 * 4, '\u00ff')
            val key = (if (challenge == 0) -1875740628 else challenge).toLong() and 0xffffffffL
            return padded.chunked(4).joinToString("") { block ->
                var word = 0L
                block.forEach { word = (word shl 8) or it.code.toLong() }
                word = word xor key
                val swapped = ((word and 255) shl 24) or ((word and 0xff00) shl 8) or
                    ((word ushr 8) and 0xff00) or ((word ushr 24) and 255)
                String.format(Locale.ROOT, "%08x", swapped)
            }
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
