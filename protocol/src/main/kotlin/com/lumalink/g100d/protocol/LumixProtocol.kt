package com.lumalink.g100d.protocol

/**
 * Capabilities are discovered at runtime. A capability is never reported as
 * available until the camera has answered a read-only probe for it.
 */
enum class LumixCapability {
    READ_STATE,
    BROWSE,
    DOWNLOAD,
    THUMBNAIL,
}

enum class MediaKind {
    JPEG,
    RAW,
    VIDEO,
    UNKNOWN;

    companion object {
        fun fromFileName(name: String): MediaKind {
            return when (name.substringAfterLast('.', "").lowercase()) {
                "jpg", "jpeg", "jpe" -> JPEG
                "rw2", "raw", "dng" -> RAW
                "mp4", "mov", "avi", "m4v" -> VIDEO
                else -> UNKNOWN
            }
        }
    }
}

data class CameraModel(
    val model: String,
    val firmware: String? = null,
    val serial: String? = null,
)

data class CameraStatus(
    val batteryPercent: Int? = null,
    val mode: String? = null,
    val recording: Boolean = false,
    val remainingShots: Int? = null,
    val rawFields: Map<String, String> = emptyMap(),
)

data class CameraFile(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val capturedAtEpochSeconds: Long? = null,
    val kind: MediaKind = MediaKind.UNKNOWN,
    val thumbnailPath: String? = null,
    val isDirectory: Boolean = false,
)

sealed interface LumixResult<out T> {
    data class Success<T>(val value: T) : LumixResult<T>
    data class Failure(val error: LumixError) : LumixResult<Nothing>
}

sealed class LumixError(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Timeout(
        message: String = "Camera did not answer in time",
        cause: Throwable? = null,
    ) : LumixError(message, cause)

    class Offline(
        message: String = "Camera is not reachable",
        cause: Throwable? = null,
    ) : LumixError(message, cause)
    class ProtocolMismatch(message: String) : LumixError(message)
    class ReadOnlyViolation(command: String) : LumixError("Blocked non-read-only command: $command")
    class Unsupported(feature: String) : LumixError("Camera does not support: $feature")
}

/**
 * Transport-agnostic camera protocol. Implementations must only expose
 * capabilities that were confirmed by the camera; UI code relies on that.
 */
interface LumixProtocol {
    val baseUrl: String

    suspend fun handshake(): LumixResult<CameraModel>

    suspend fun readStatus(): LumixResult<CameraStatus>

    suspend fun browse(directory: String = "/DCIM"): LumixResult<List<CameraFile>>

    suspend fun capabilities(): Set<LumixCapability>
}
