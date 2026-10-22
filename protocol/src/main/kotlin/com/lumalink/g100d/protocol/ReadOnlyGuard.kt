package com.lumalink.g100d.protocol

import okhttp3.HttpUrl

/**
 * Phase 2 policy: metadata and file reads only. Any camera control command,
 * settings write, stream start, or non-local host is rejected before a socket
 * is opened.
 */
class ReadOnlyGuard(
    private val allowedModes: Set<String> = setOf(
        "getstate",
        "getinfo",
        "browse",
        "get_content_info",
    ),
) {

    fun check(method: String, url: HttpUrl) {
        if (method != "GET" && method != "HEAD") {
            throw LumixError.ReadOnlyViolation("$method ${url.encodedPath}")
        }
        if (!HostGuard.isLocalHost(url.host)) {
            throw LumixError.ReadOnlyViolation("non-local host: ${url.host}")
        }
        // UPnP media files are served from :50001, not /DCIM on :80.
        if (url.port == 50001) return

        val path = url.encodedPath
        if (path == "/cam.cgi") {
            val mode = url.queryParameter("mode")?.lowercase()
                ?: throw LumixError.ReadOnlyViolation("missing mode parameter")
            if (mode !in allowedModes) {
                throw LumixError.ReadOnlyViolation("mode=$mode")
            }
            return
        }

        if (!path.startsWith("/DCIM/") && !path.startsWith("/PRIVATE/")) {
            throw LumixError.ReadOnlyViolation("path: $path")
        }
    }
}
