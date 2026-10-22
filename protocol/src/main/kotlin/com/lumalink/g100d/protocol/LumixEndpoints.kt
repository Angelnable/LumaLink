package com.lumalink.g100d.protocol

/**
 * Candidate endpoints collected from public community work. These are NOT an
 * official Panasonic API and must be confirmed against the G100D with
 * read-only probes before they are exposed as working features.
 */
object LumixEndpoints {
    const val DEFAULT_BASE_URL: String = "http://192.168.54.1"

    fun camCgi(baseUrl: String, mode: String, vararg parameters: Pair<String, String>): String {
        val query = buildList {
            add("mode" to mode)
            addAll(parameters)
        }.joinToString("&") { (key, value) -> "$key=$value" }
        return "${baseUrl.trimEnd('/')}/cam.cgi?$query"
    }

    fun state(baseUrl: String = DEFAULT_BASE_URL): String =
        camCgi(baseUrl, "getstate")

    fun info(baseUrl: String = DEFAULT_BASE_URL): String =
        camCgi(baseUrl, "getinfo")

    fun infoCapability(baseUrl: String = DEFAULT_BASE_URL): String =
        camCgi(baseUrl, "getinfo", "type" to "capability")

    fun currentMenu(baseUrl: String = DEFAULT_BASE_URL): String =
        camCgi(baseUrl, "getinfo", "type" to "curmenu")

    fun browse(baseUrl: String = DEFAULT_BASE_URL, directory: String = "/DCIM"): String =
        camCgi(baseUrl, "browse", "dir" to directory)

    fun contentInfo(baseUrl: String = DEFAULT_BASE_URL): String =
        camCgi(baseUrl, "get_content_info")

    fun thumb(
        baseUrl: String = DEFAULT_BASE_URL,
        directory: String,
        fileName: String,
    ): String = camCgi(baseUrl, "thumb", "dir" to directory, "file" to fileName)

    fun file(baseUrl: String = DEFAULT_BASE_URL, path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path
        }
        val normalized = path.trim().trimStart('/')
        require(normalized.isNotEmpty()) { "Empty camera file path" }
        require(!normalized.contains("..")) { "Path traversal is not allowed" }
        return "${baseUrl.trimEnd('/')}/$normalized"
    }
}
