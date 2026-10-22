package com.lumalink.g100d.protocol

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The G100D grants one 50001 file per Browse, and a download consumes that
 * grant. Camera Browse, thumbnail access and transfer therefore share one
 * mutex: a transfer file holds it for the whole reauth + Browse + download,
 * so a gallery prefetch cannot invalidate the active grant mid-file.
 */
object CameraAccessGate {
    @Volatile
    var transferActive: Boolean = false

    private val cameraMutex = Mutex()

    suspend fun <T> withCamera(block: suspend () -> T): T = cameraMutex.withLock {
        block()
    }

    /**
     * Gallery-side operation: skip instead of waiting if transfer owns the
     * camera, and re-check after acquiring the mutex to close the start race.
     */
    suspend fun <T> withGalleryCamera(block: suspend () -> T): T? {
        if (transferActive) return null
        return cameraMutex.withLock {
            if (transferActive) null else block()
        }
    }
}
