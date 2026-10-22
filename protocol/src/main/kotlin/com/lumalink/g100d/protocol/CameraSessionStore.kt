package com.lumalink.g100d.protocol

/**
 * The camera issues an opaque session token after the user approves the app on
 * the camera screen. Every subsequent request must carry X-SESSION_ID.
 */
object CameraSessionStore {
    @Volatile
    var sessionId: String? = null
        private set

    fun set(value: String?) {
        sessionId = value?.takeIf { it.isNotBlank() }
    }

    fun clear() {
        sessionId = null
    }
}
