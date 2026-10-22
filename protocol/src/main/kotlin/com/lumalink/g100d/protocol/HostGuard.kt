package com.lumalink.g100d.protocol

/**
 * The camera is expected on a private / link-local address. Refusing public
 * hosts keeps a malformed camera response or a typo from turning the app into
 * a generic HTTP client.
 */
object HostGuard {

    fun isLocalHost(host: String): Boolean {
        val normalized = host.trim().lowercase()
        if (normalized == "localhost") return true
        if (normalized.contains(':')) {
            return normalized == "::1" ||
                normalized == "0:0:0:0:0:0:0:1" ||
                normalized.startsWith("fe80:") ||
                normalized.startsWith("fc") ||
                normalized.startsWith("fd")
        }

        val parts = normalized.split('.')
        if (parts.size != 4) return false
        val octets = parts.map { it.toIntOrNull() ?: return false }
        if (octets.any { it !in 0..255 }) return false

        return when {
            octets[0] == 10 -> true
            octets[0] == 127 -> true
            octets[0] == 169 && octets[1] == 254 -> true
            octets[0] == 172 && octets[1] in 16..31 -> true
            octets[0] == 192 && octets[1] == 168 -> true
            else -> false
        }
    }
}
