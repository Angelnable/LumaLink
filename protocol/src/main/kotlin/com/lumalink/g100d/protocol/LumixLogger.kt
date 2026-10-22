package com.lumalink.g100d.protocol

data class LumixHttpLogEntry(
    val timestamp: Long,
    val method: String,
    val url: String,
    val statusCode: Int?,
    val durationMs: Long,
    val error: String? = null,
)

fun interface LumixLogger {
    fun log(entry: LumixHttpLogEntry)

    fun logBody(url: String, body: String) {
        // Optional: implementations may expose a truncated response body.
    }
}
