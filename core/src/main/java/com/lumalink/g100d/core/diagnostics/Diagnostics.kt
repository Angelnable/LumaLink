package com.lumalink.g100d.core.diagnostics

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class DiagnosticLevel {
    INFO,
    WARN,
    ERROR,
}

data class DiagnosticEntry(
    val timestamp: Long,
    val level: DiagnosticLevel,
    val message: String,
)

/**
 * In-memory diagnostics for the current process. Nothing is uploaded; the
 * user can copy or clear the log from Settings.
 */
object Diagnostics {

    private const val MAX_ENTRIES = 250

    private val _entries = MutableStateFlow<List<DiagnosticEntry>>(emptyList())
    val entries: StateFlow<List<DiagnosticEntry>> = _entries.asStateFlow()

    fun info(message: String) = add(DiagnosticLevel.INFO, message)

    fun warn(message: String) = add(DiagnosticLevel.WARN, message)

    fun error(message: String) = add(DiagnosticLevel.ERROR, message)

    fun logHttp(
        method: String,
        url: String,
        statusCode: Int?,
        durationMs: Long,
        error: String? = null,
    ) {
        val result = when {
            error != null -> "ERROR $error"
            statusCode != null -> "HTTP $statusCode"
            else -> "OK"
        }
        add(
            if (error != null) DiagnosticLevel.ERROR else DiagnosticLevel.INFO,
            "$method $url -> $result (${durationMs}ms)",
        )
    }

    fun clear() {
        _entries.value = emptyList()
    }

    fun asText(): String {
        val format = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return _entries.value.joinToString(separator = "\n") { entry ->
            "${format.format(Date(entry.timestamp))} ${entry.level.name} ${entry.message}"
        }
    }

    private fun add(level: DiagnosticLevel, message: String) {
        _entries.update { current ->
            (current + DiagnosticEntry(System.currentTimeMillis(), level, message))
                .takeLast(MAX_ENTRIES)
        }
    }
}
