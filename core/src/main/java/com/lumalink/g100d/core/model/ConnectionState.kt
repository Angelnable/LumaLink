package com.lumalink.g100d.core.model

/**
 * Connection state shared by the UI shell and (later) the transport layer.
 * Phase 1 only renders DISCONNECTED; the state machine is wired in phase 2.
 */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DEGRADED,
    ERROR,
}

data class CameraInfo(
    val model: String = "LUMIX G100D",
    val firmware: String? = null,
    val ssid: String? = null,
)
