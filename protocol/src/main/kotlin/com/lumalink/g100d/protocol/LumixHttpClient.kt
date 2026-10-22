package com.lumalink.g100d.protocol

import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

data class LumixHttpResponse(
    val code: Int,
    val body: ByteArray,
    val contentType: String?,
    val url: String,
) {
    val text: String get() = body.toString(Charsets.UTF_8)
}

class LumixHttpClient(
    baseUrl: String,
    private val client: OkHttpClient = defaultClient(),
    private val guard: ReadOnlyGuard = ReadOnlyGuard(),
    private val logger: LumixLogger? = null,
) {

    val baseUrl: String = baseUrl.trimEnd('/')

    suspend fun getText(url: String): LumixResult<LumixHttpResponse> = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        val request = try {
            Request.Builder()
                .url(url)
                .cameraHeaders()
                .get()
                .build()
        } catch (error: IllegalArgumentException) {
            log("GET", url, null, startedAt, error.message)
            return@withContext LumixResult.Failure(
                LumixError.ProtocolMismatch("Invalid camera URL: ${error.message}"),
            )
        }

        try {
            guard.check("GET", request.url)
        } catch (error: LumixError) {
            log("GET", request.url.toString(), null, startedAt, error.message)
            return@withContext LumixResult.Failure(error)
        }

        try {
            client.newCall(request).execute().use { response ->
                log("GET", request.url.toString(), response.code, startedAt)
                if (!response.isSuccessful) {
                    return@withContext LumixResult.Failure(
                        LumixError.ProtocolMismatch("HTTP ${response.code} for ${request.url.encodedPath}"),
                    )
                }
                LumixResult.Success(
                    LumixHttpResponse(
                        code = response.code,
                        body = response.body?.bytes() ?: ByteArray(0),
                        contentType = response.header("Content-Type"),
                        url = response.request.url.toString(),
                    ),
                )
            }
        } catch (error: Throwable) {
            log("GET", request.url.toString(), null, startedAt, error.message)
            if (error is IOException && error.isTransientEof()) {
                // The camera sometimes closes a reused keep-alive socket without
                // a response. Metadata GETs are idempotent, so retry once on a
                // fresh connection; file transfers never take this path.
                val retryRequest = request.newBuilder()
                    .header("Connection", "close")
                    .build()
                try {
                    client.newCall(retryRequest).execute().use { response ->
                        log("GET", retryRequest.url.toString(), response.code, startedAt)
                        if (!response.isSuccessful) {
                            return@withContext LumixResult.Failure(
                                LumixError.ProtocolMismatch(
                                    "HTTP ${response.code} for ${retryRequest.url.encodedPath}",
                                ),
                            )
                        }
                        return@withContext LumixResult.Success(response.toLumixHttpResponse())
                    }
                } catch (retryError: Throwable) {
                    log("GET", retryRequest.url.toString(), null, startedAt, retryError.message)
                    return@withContext LumixResult.Failure(retryError.toLumixError())
                }
            }
            LumixResult.Failure(error.toLumixError())
        }
    }

    suspend fun head(
        url: String,
        logRequest: Boolean = true,
    ): LumixResult<Long?> = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        fun maybeLog(statusCode: Int?, error: String? = null) {
            if (logRequest) log("HEAD", url, statusCode, startedAt, error)
        }
        val request = try {
            Request.Builder()
                .url(url)
                .cameraHeaders()
                .head()
                .build()
        } catch (error: IllegalArgumentException) {
            maybeLog(null, error.message)
            return@withContext LumixResult.Failure(
                LumixError.ProtocolMismatch("Invalid camera URL: ${error.message}"),
            )
        }

        try {
            guard.check("HEAD", request.url)
        } catch (error: LumixError) {
            maybeLog(null, error.message)
            return@withContext LumixResult.Failure(error)
        }

        try {
            client.newCall(request).execute().use { response ->
                maybeLog(response.code)
                if (!response.isSuccessful) {
                    return@withContext LumixResult.Failure(
                        LumixError.ProtocolMismatch("HTTP ${response.code} for ${request.url.encodedPath}"),
                    )
                }
                LumixResult.Success(response.header("Content-Length")?.toLongOrNull())
            }
        } catch (error: Throwable) {
            maybeLog(null, error.message)
            LumixResult.Failure(error.toLumixError())
        }
    }

    /**
     * Read-only existence/size probe for a media URL. Some cameras do not
     * implement HEAD, so we ask for the first 16 bytes with Range and inspect
     * the response headers without reading the whole file.
     */
    suspend fun probeFile(url: String): LumixResult<Long?> = withContext(Dispatchers.IO) {
        val request = try {
            Request.Builder()
                .url(url)
                .cameraHeaders()
                .header("Range", "bytes=0-15")
                .get()
                .build()
        } catch (error: IllegalArgumentException) {
            return@withContext LumixResult.Failure(
                LumixError.ProtocolMismatch("Invalid media URL: ${error.message}"),
            )
        }
        try {
            guard.check("GET", request.url)
        } catch (error: LumixError) {
            return@withContext LumixResult.Failure(error)
        }

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext LumixResult.Failure(
                        LumixError.ProtocolMismatch("HTTP ${response.code} for ${request.url.encodedPath}"),
                    )
                }
                val contentRange = response.header("Content-Range")
                val total = contentRange
                    ?.substringAfterLast('/')
                    ?.toLongOrNull()
                val length = response.header("Content-Length")?.toLongOrNull()
                LumixResult.Success(total ?: length ?: 0L)
            }
        } catch (error: Throwable) {
            LumixResult.Failure(error.toLumixError())
        }
    }

    /**
     * Creates a cancellable call. The transfer service keeps the call so the
     * user can cancel a large download without waiting for the socket timeout.
     */
    suspend fun prepareCall(
        url: String,
        method: String = "GET",
        rangeStart: Long? = null,
    ): LumixResult<Call> = withContext(Dispatchers.IO) {
        val builder = try {
            Request.Builder()
                .url(url)
                .cameraHeaders()
        } catch (error: IllegalArgumentException) {
            return@withContext LumixResult.Failure(
                LumixError.ProtocolMismatch("Invalid camera URL: ${error.message}"),
            )
        }

        if (rangeStart != null && rangeStart > 0L) {
            builder.header("Range", "bytes=$rangeStart-")
        }
        if (method == "HEAD") builder.head() else builder.get()
        val request = builder.build()

        try {
            guard.check(method, request.url)
        } catch (error: LumixError) {
            return@withContext LumixResult.Failure(error)
        }
        LumixResult.Success(client.newCall(request))
    }

    /**
     * Opens a file response for streaming. The caller must close it. This is
     * used by the transfer service, not by metadata parsing.
     */
    suspend fun open(url: String): LumixResult<Response> = withContext(Dispatchers.IO) {
        val startedAt = System.nanoTime()
        val request = try {
            Request.Builder()
                .url(url)
                .cameraHeaders()
                .get()
                .build()
        } catch (error: IllegalArgumentException) {
            log("GET", url, null, startedAt, error.message)
            return@withContext LumixResult.Failure(
                LumixError.ProtocolMismatch("Invalid camera URL: ${error.message}"),
            )
        }

        try {
            guard.check("GET", request.url)
        } catch (error: LumixError) {
            log("GET", request.url.toString(), null, startedAt, error.message)
            return@withContext LumixResult.Failure(error)
        }

        try {
            val response = client.newCall(request).execute()
            log("GET", request.url.toString(), response.code, startedAt)
            if (!response.isSuccessful) {
                response.close()
                return@withContext LumixResult.Failure(
                    LumixError.ProtocolMismatch("HTTP ${response.code} for ${request.url.encodedPath}"),
                )
            }
            LumixResult.Success(response)
        } catch (error: Throwable) {
            log("GET", request.url.toString(), null, startedAt, error.message)
            LumixResult.Failure(error.toLumixError())
        }
    }

    fun logBody(url: String, body: String) {
        logger?.logBody(url, body)
    }

    private fun log(
        method: String,
        url: String,
        statusCode: Int?,
        startedAt: Long,
        error: String? = null,
    ) {
        logger?.log(
            LumixHttpLogEntry(
                timestamp = System.currentTimeMillis(),
                method = method,
                url = url,
                statusCode = statusCode,
                durationMs = (System.nanoTime() - startedAt) / 1_000_000L,
                error = error,
            ),
        )
    }

    private fun Request.Builder.cameraHeaders(): Request.Builder {
        header("User-Agent", CAMERA_USER_AGENT)
        // The camera's 50001 file server closes mid-body when it sees the
        // default gzip negotiation. Match the known-good Lumix client.
        header("Accept-Encoding", "identity")
        header("Connection", "close")
        CameraSessionStore.sessionId?.let { header("X-SESSION_ID", it) }
        return this
    }

    private fun Response.toLumixHttpResponse(): LumixHttpResponse = LumixHttpResponse(
        code = code,
        body = body?.bytes() ?: ByteArray(0),
        contentType = header("Content-Type"),
        url = request.url.toString(),
    )

    private fun IOException.isTransientEof(): Boolean {
        val text = message.orEmpty().lowercase()
        return text.contains("unexpected end of stream") ||
            text.contains("connection abort") ||
            text.contains("connection reset") ||
            text.contains("broken pipe")
    }

    private fun Throwable.toLumixError(): LumixError = when (this) {
        is LumixError -> this
        is SocketTimeoutException -> LumixError.Timeout(cause = this)
        is IOException -> LumixError.Offline(message ?: "Network error", this)
        else -> LumixError.ProtocolMismatch(message ?: javaClass.simpleName)
    }

    companion object {
        const val CAMERA_USER_AGENT = "Panasonic Android/1 DM-CP"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
