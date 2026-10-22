package com.lumalink.g100d.protocol

import java.io.StringReader
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import org.xml.sax.InputSource
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Read-only UPnP ContentDirectory client. G100D-class cameras expose their
 * media list on :60606 and files on :50001. The only SOAP action this class
 * can send is BrowseDirectChildren; there is no settings/capture command here.
 *
 * The camera expects the client to be in playback mode. We deliberately do not
 * send `camcmd playmode`; the user switches the camera manually.
 */
class UpnpContentDirectoryClient(
    baseUrl: String = LumixEndpoints.DEFAULT_BASE_URL,
    private val soapUrlOverride: String? = null,
    fileBaseUrlOverride: String? = null,
    private val client: OkHttpClient = defaultClient(),
    private val logger: LumixLogger? = null,
    private val descriptionResolver: UpnpDescriptionResolver = UpnpDescriptionResolver(
        baseUrl = baseUrl,
        logger = logger,
    ),
    private val tryLumixPrefix: Boolean = true,
    private val sendProtocolInfo: Boolean = soapUrlOverride == null,
) {
    private val host: String = baseUrl.toHttpUrl().host
    private val soapUrl: String = soapUrlOverride ?: "http://$host:60606/Server0/CDS_control"
    private var activeSoapUrl: String = soapUrl
    private var protocolInfoSent = false
    private var descriptionResolved = false
    private val fileBaseUrl: String = fileBaseUrlOverride ?: "http://$host:50001/"

    private suspend fun resolveDescriptionOnce() {
        if (soapUrlOverride != null || descriptionResolved) return
        when (val resolved = descriptionResolver.resolve()) {
            is LumixResult.Success -> {
                activeSoapUrl = resolved.value
                descriptionResolved = true
                logger?.logBody("upnp-resolve", "ContentDirectory=$activeSoapUrl (cached)")
            }

            is LumixResult.Failure -> {
                // The G100D advertises /Lumix/Server0/ddd but often serves the
                // control URL at /Server0/CDS_control. Cache the fallback too:
                // retrying SSDP for every photo costs ~1 s per file.
                descriptionResolved = true
                logger?.logBody(
                    "upnp-resolve",
                    "description failed, using fallback=$soapUrl: ${resolved.error.message}",
                )
            }
        }
    }

    /**
     * A single Browse is enough to renew the camera's one-file 50001 grant.
     * The full directory is intentionally not paged here: transfers should not
     * pay for two SOAP pages per photo.
     */
    suspend fun refreshAccess(): LumixResult<Unit> = withContext(Dispatchers.IO) {
        if (!HostGuard.isLocalHost(host)) {
            return@withContext LumixResult.Failure(
                LumixError.ReadOnlyViolation("non-local UPnP host: $host"),
            )
        }
        resolveDescriptionOnce()
        when (val page = browsePage(0, 1)) {
            is LumixResult.Success -> LumixResult.Success(Unit)
            is LumixResult.Failure -> page
        }
    }

    suspend fun browseAll(
        maxItems: Int = 10_000,
        expectedMinimum: Int = 0,
        onAuthRefresh: (suspend () -> Boolean)? = null,
    ): LumixResult<List<CameraFile>> =
        withContext(Dispatchers.IO) {
            if (!HostGuard.isLocalHost(host)) {
                return@withContext LumixResult.Failure(
                    LumixError.ReadOnlyViolation("non-local UPnP host: $host"),
                )
            }

            resolveDescriptionOnce()

            val items = linkedMapOf<String, CameraFile>()
            var startIndex = 0
            // S5M2 returns 50 items per page even when 200 are requested.
            // Use 50 so the camera's paging contract is explicit.
            val pageSize = 50
            var lastError: LumixError? = null
            var retried500 = false
            var authRefreshes = 0
            var declaredTotal = 0

            while (items.size < maxItems) {
                when (val pageResult = browsePage(startIndex, pageSize)) {
                    is LumixResult.Success -> {
                        val page = pageResult.value
                        val before = items.size
                        page.files.forEach { file -> items.putIfAbsent(file.path, file) }
                        val returned = page.numberReturned.coerceAtLeast(page.files.size)
                        if (returned <= 0 || page.files.isEmpty()) break
                        // Some LUMIX Sync bodies ignore StartingIndex or omit
                        // totalMatches; if a page adds no new item, stop
                        // instead of requesting the same first page forever.
                        if (items.size == before) break
                        if (page.totalMatches > 0) declaredTotal = page.totalMatches
                        startIndex += returned
                        logger?.logBody(
                            "upnp-page",
                            "start=$startIndex returned=$returned " +
                                "total=${page.totalMatches} loaded=${items.size} " +
                                "first=${page.files.firstOrNull()?.name} " +
                                "last=${page.files.lastOrNull()?.name}",
                        )
                        if (page.totalMatches > 0 &&
                            startIndex >= page.totalMatches &&
                            items.size >= expectedMinimum
                        ) break
                    }

                    is LumixResult.Failure -> {
                        lastError = pageResult.error
                        val message = pageResult.error.message.orEmpty()
                        // S5M2 grants roughly 150 Browse items per access.
                        // At start=150 the next page returns 403; reauthorize
                        // and retry the same start index.
                        if (message.contains("403") &&
                            onAuthRefresh != null && authRefreshes < 100
                        ) {
                            authRefreshes++
                            logger?.logBody(
                                "upnp-page",
                                "403 at start=$startIndex, reauthorize #$authRefreshes",
                            )
                            if (onAuthRefresh()) {
                                delay(150)
                                continue
                            }
                        }
                        // S5M2 can return one transient HTTP 500 right after
                        // playmode; retry once before surfacing the failure.
                        if (!retried500 && message.contains("500")) {
                            retried500 = true
                            delay(500)
                            continue
                        }
                        break
                    }
                }
            }

            // S5M2 can return an oversized page near the end and skip the
            // actual tail (e.g. start=600 returned 70 while total=605).
            // Re-read the last page window and merge the missing tail.
            if (declaredTotal > 0 && items.size < declaredTotal + pageSize) {
                val tailStart = maxOf(0, declaredTotal - pageSize)
                if (tailStart < startIndex) {
                    when (val tail = browsePage(tailStart, pageSize)) {
                        is LumixResult.Success -> {
                            val before = items.size
                            tail.value.files.forEach { items.putIfAbsent(it.path, it) }
                            logger?.logBody(
                                "upnp-tail",
                                "start=$tailStart before=$before after=${items.size} " +
                                    "first=${tail.value.files.firstOrNull()?.name} " +
                                    "last=${tail.value.files.lastOrNull()?.name}",
                            )
                        }

                        is LumixResult.Failure -> {
                            logger?.logBody("upnp-tail", "failed: ${tail.error.message}")
                        }
                    }
                }
            }

            if (items.isEmpty() && lastError != null) {
                LumixResult.Failure(lastError)
            } else {
                // Different res URLs can occasionally describe the same
                // logical camera file; dedupe by real file name for the UI.
                var unique = items.values.distinctBy { it.name.lowercase() }
                // The camera can expose extra resources while reporting 605
                // logical files. The browse order is ascending by capture,
                // so keep the newest declaredTotal items after tail repair.
                if (declaredTotal in 1 until unique.size) {
                    val removed = unique.size - declaredTotal
                    unique = unique.takeLast(declaredTotal)
                    logger?.logBody("upnp-page", "trimmed=$removed total=$declaredTotal")
                }
                LumixResult.Success(unique)
            }
        }

    private fun browsePage(startIndex: Int, count: Int): LumixResult<BrowsePage> {
        if (sendProtocolInfo && !protocolInfoSent) {
            protocolInfoSent = true
            sendGetProtocolInfo()
        }

        val body = buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            append(
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                    "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">",
            )
            append("<s:Body>")
            append(
                "<u:Browse xmlns:u=\"urn:schemas-upnp-org:service:ContentDirectory:1\" " +
                    "xmlns:pana=\"urn:schemas-panasonic-com:pana\">",
            )
            append("<ObjectID>0</ObjectID>")
            append("<BrowseFlag>BrowseDirectChildren</BrowseFlag>")
            append("<Filter>*</Filter>")
            append("<StartingIndex>$startIndex</StartingIndex>")
            append("<RequestedCount>$count</RequestedCount>")
            append("<SortCriteria></SortCriteria>")
            append("<pana:X_FromCP>LumixLink2.0</pana:X_FromCP>")
            append("</u:Browse>")
            append("</s:Body>")
            append("</s:Envelope>")
        }

        val startedAt = System.nanoTime()
        return try {
            val quotedAction =
                "\"urn:schemas-upnp-org:service:ContentDirectory:1#Browse\""
            val actionVariants = listOf(quotedAction.removeSurrounding("\""), quotedAction)
            val contentVariants = listOf(
                "text/xml; charset=\"utf-8\"",
                "text/xml; charset=utf-8",
            )
            val urls = linkedSetOf<String>()
            if (tryLumixPrefix && activeSoapUrl.contains("/Server0/") &&
                !activeSoapUrl.contains("/Lumix/Server0/")
            ) {
                // The SSDP LOCATION is under /Lumix/Server0/, while the
                // description advertises an absolute /Server0/controlURL.
                // Some firmware requires the /Lumix prefix on the control URL.
                urls += activeSoapUrl.replace("/Server0/", "/Lumix/Server0/")
            }
            urls += activeSoapUrl

            var success: okhttp3.Response? = null
            var lastCode: Int? = null
            urlLoop@ for (url in urls) {
                for (contentType in contentVariants) {
                    for (action in actionVariants) {
                        val attempt = executeBrowse(url, body, contentType, action)
                        logger?.logBody(
                            "upnp-attempt",
                            "$url content=$contentType action=$action -> HTTP ${attempt.code}",
                        )
                        lastCode = attempt.code
                        if (attempt.code in 200..299) {
                            success = attempt
                            activeSoapUrl = url
                            break@urlLoop
                        }
                        attempt.close()
                        // 404 means this URL is wrong: try the next control URL
                        // instead of retrying headers against the same path.
                        if (attempt.code != 403) break
                    }
                    if (success != null || lastCode != 403) break
                }
            }

            val result = success
            if (result == null) {
                return LumixResult.Failure(
                    LumixError.ProtocolMismatch("UPnP HTTP ${lastCode ?: "error"}"),
                )
            }
            result.use { response ->
                val text = response.body?.string().orEmpty()
                logger?.log(
                    LumixHttpLogEntry(
                        timestamp = System.currentTimeMillis(),
                        method = "POST",
                        url = activeSoapUrl,
                        statusCode = response.code,
                        durationMs = (System.nanoTime() - startedAt) / 1_000_000L,
                    ),
                )
                logger?.logBody(
                    activeSoapUrl,
                    "HTTP ${response.code} headers: ${response.headers}\n$text",
                )
                when (val parsed = parseResponse(text)) {
                    is LumixResult.Success -> {
                        val numberReturned = Regex("<NumberReturned>(\\d+)</NumberReturned>")
                            .find(text)
                            ?.groupValues
                            ?.getOrNull(1)
                            ?.toIntOrNull()
                            ?: parsed.value.size
                        // LUMIX Sync bodies often omit TotalMatches. Keep 0
                        // so browseAll continues until an empty/repeated page.
                        val totalMatches = Regex("<TotalMatches>(\\d+)</TotalMatches>")
                            .find(text)
                            ?.groupValues
                            ?.getOrNull(1)
                            ?.toIntOrNull()
                            ?: 0
                        LumixResult.Success(
                            BrowsePage(parsed.value, numberReturned, totalMatches),
                        )
                    }

                    is LumixResult.Failure -> parsed
                }
            }
        } catch (error: SocketTimeoutException) {
            logger?.log(
                LumixHttpLogEntry(
                    timestamp = System.currentTimeMillis(),
                    method = "POST",
                    url = activeSoapUrl,
                    statusCode = null,
                    durationMs = (System.nanoTime() - startedAt) / 1_000_000L,
                    error = error.message,
                ),
            )
            LumixResult.Failure(LumixError.Timeout(cause = error))
        } catch (error: Throwable) {
            logger?.log(
                LumixHttpLogEntry(
                    timestamp = System.currentTimeMillis(),
                    method = "POST",
                    url = activeSoapUrl,
                    statusCode = null,
                    durationMs = (System.nanoTime() - startedAt) / 1_000_000L,
                    error = error.message,
                ),
            )
            LumixResult.Failure(
                LumixError.Offline(error.message ?: "UPnP browse failed", error),
            )
        }
    }

    private data class BrowsePage(
        val files: List<CameraFile>,
        val numberReturned: Int,
        val totalMatches: Int,
    )

    private fun executeBrowse(
        url: String,
        body: String,
        contentType: String,
        soapAction: String,
    ) = client.newCall(
        Request.Builder()
            .url(url)
            .post(body.toRequestBody("text/xml".toMediaType()))
            .header("Content-Type", contentType)
            .header("SOAPAction", soapAction)
            .header("Accept", "*/*")
            // The camera identifies UPnP control points by this prefix.
            .header("User-Agent", "Panasonic MIL DLNA CP UPnP/1.0")
            .header("X-AV-Client-Info", "DLNADOC/1.50")
            .header("X-FromCP", "LumixLink2.0")
            .apply { CameraSessionStore.sessionId?.let { header("X-SESSION_ID", it) } }
            .build(),
    ).execute()

    /**
     * Standard DLNA control points call GetProtocolInfo before Browse. The
     * camera advertises a ConnectionManager service; some firmware rejects
     * ContentDirectory until this has been called.
     */
    private fun sendGetProtocolInfo() {
        val cmBody = buildString {
            append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            append(
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                    "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">",
            )
            append("<s:Body>")
            append(
                "<u:GetProtocolInfo " +
                    "xmlns:u=\"urn:schemas-upnp-org:service:ConnectionManager:1\">",
            )
            append("</u:GetProtocolInfo>")
            append("</s:Body></s:Envelope>")
        }
        val base = activeSoapUrl.substringBeforeLast('/')
        val cmUrls = linkedSetOf<String>()
        if (base.contains("/Server0") && !base.contains("/Lumix/Server0")) {
            cmUrls += base.replace("/Server0", "/Lumix/Server0") + "/CM_control"
        }
        cmUrls += "$base/CM_control"
        for (cmUrl in cmUrls) {
            try {
                val request = Request.Builder()
                    .url(cmUrl)
                    .post(cmBody.toRequestBody("text/xml".toMediaType()))
                    .header("Content-Type", "text/xml; charset=\"utf-8\"")
                    .header(
                        "SOAPAction",
                        "\"urn:schemas-upnp-org:service:ConnectionManager:1#GetProtocolInfo\"",
                    )
                    .header("X-AV-Client-Info", "DLNADOC/1.50")
                    .header("User-Agent", "Panasonic MIL DLNA CP UPnP/1.0")
                    .apply { CameraSessionStore.sessionId?.let { header("X-SESSION_ID", it) } }
                    .build()
                client.newCall(request).execute().use { response ->
                    logger?.logBody(
                        "upnp-protocol-info",
                        "$cmUrl -> HTTP ${response.code}",
                    )
                    if (response.code == 200) return
                }
            } catch (error: Throwable) {
                logger?.logBody("upnp-protocol-info", "$cmUrl -> ${error.message}")
            }
        }
    }

    private fun parseResponse(soap: String): LumixResult<List<CameraFile>> {
        // The camera's SOAP envelope can contain bytes the platform XML parser
        // rejects. Extract the escaped DIDL payload first, without parsing the
        // envelope, then parse the item records with the tolerant parser.
        val resultText = Regex("(?is)<(?:\\w+:)?Result\\b[^>]*>(.*?)</(?:\\w+:)?Result>")
            .find(soap)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (!resultText.isNullOrEmpty()) {
            val files = runCatching { parseDidlWithRegex(resultText) }.getOrNull()
            if (!files.isNullOrEmpty()) {
                logger?.logBody("upnp-parse", "items=${files.size} via=result-regex")
                return LumixResult.Success(files)
            }
        }

        val soapDocument = parseXml(soap)
            ?: return LumixResult.Failure(LumixError.ProtocolMismatch("Invalid UPnP SOAP XML"))
        val resultElement = soapDocument.getElementsByTagName("*")
            .asElementSequence()
            .firstOrNull { it.localName == "Result" || it.tagName.endsWith(":Result") }
            ?: return LumixResult.Failure(LumixError.ProtocolMismatch("UPnP response has no Result"))

        val didl = resultElement.textContent?.trim().orEmpty()
        if (didl.isEmpty()) return LumixResult.Success(emptyList())

        // Some camera firmware produces DIDL that the platform XML parser
        // rejects even though it is otherwise well-formed. A small regex
        // parser keeps the real list usable instead of dropping everything.
        val regexFiles = runCatching { parseDidlWithRegex(didl) }.getOrNull()
        if (!regexFiles.isNullOrEmpty()) {
            logger?.logBody("upnp-parse", "items=${regexFiles.size} via=regex")
            return LumixResult.Success(regexFiles)
        }

        val didlDocument = parseXml(didl)
            ?: return LumixResult.Failure(LumixError.ProtocolMismatch("Invalid DIDL-Lite XML"))

        val files = linkedMapOf<String, CameraFile>()
        didlDocument.getElementsByTagName("*").asElementSequence().forEach { element ->
            if (!isTag(element, "item")) return@forEach
            val file = runCatching { parseItem(element) }.getOrNull() ?: return@forEach
            files.putIfAbsent(file.path, file)
        }
        logger?.logBody("upnp-parse", "items=${files.size}")
        return LumixResult.Success(files.values.toList())
    }

    private fun parseDidlWithRegex(didl: String): List<CameraFile> {
        val decoded = didl
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
        val itemRegex = Regex("(?is)<item\\b([^>]*)>(.*?)</item>")
        val idRegex = Regex("(?is)\\bid=\"([^\"]+)\"")
        val resRegex = Regex("(?is)<res\\b([^>]*)>(https?://[^<]+)</res>")
        val sizeRegex = Regex("(?is)\\bsize=\"(\\d+)\"")
        val dateRegex = Regex("(?is)<dc:date[^>]*>(.*?)</dc:date>")
        val profileRegex = Regex("(?i)PANASONIC\\.COM_PN=([^;\"]+)")
        val files = linkedMapOf<String, CameraFile>()
        itemRegex.findAll(decoded).forEach { item ->
            val itemAttributes = item.groupValues[1]
            val itemBody = item.groupValues[2]
            val objectId = idRegex.find(itemAttributes)?.groupValues?.getOrNull(1)
            for (res in resRegex.findAll(itemBody)) {
                val resAttributes = res.groupValues[1]
                val virtualPath = res.groupValues[2].trim().replace("&amp;", "&")
                if (virtualPath.isEmpty()) continue
                val profile = profileRegex.find(resAttributes)?.groupValues?.getOrNull(1)
                    ?.trim()
                    ?.uppercase()
                // Thumbnails are not user-facing files.
                if (profile in setOf("CAM_TN", "CAM_LRGTN")) continue
                val baseName = virtualPath.substringAfterLast('/')
                if (baseName.startsWith("DT", ignoreCase = true)) continue
                val extension = extensionFor(virtualPath, profile)
                val realName = cameraFileName(objectId, extension)
                val path = virtualPath
                val host = runCatching { path.toHttpUrl().host }.getOrNull() ?: continue
                if (!HostGuard.isLocalHost(host)) continue
                val name = realName ?: baseName
                if (name.isBlank()) continue
                val size = sizeRegex.find(resAttributes)?.groupValues?.getOrNull(1)?.toLongOrNull()
                    ?: 0L
                val date = dateRegex.find(itemBody)?.groupValues?.getOrNull(1)?.trim()
                files.putIfAbsent(
                    path,
                    CameraFile(
                        path = path,
                        name = name,
                        sizeBytes = size,
                        capturedAtEpochSeconds = parseDate(date),
                        kind = MediaKind.fromFileName(name),
                    ),
                )
            }
        }
        return files.values.toList()
    }

    /**
     * DLNA resource names use an internal form such as DO01002205.JPG.
     * The real SD-card file is P1002205.JPG: P + folder (0100 -> 100) +
     * file number (2205). Use the real name for direct 50001 downloads.
     */
    private fun cameraFileName(objectId: String?, extension: String): String? {
        val id = objectId?.trim().orEmpty()
        if (id.length != 8 || id.any { !it.isDigit() }) return null
        val folder = id.substring(0, 4).toIntOrNull() ?: return null
        val file = id.substring(4)
        val ext = extension.trim().uppercase()
        if (ext.isEmpty() || ext.length > 5) return null
        return "P$folder$file.$ext"
    }

    private fun extensionFor(resourcePath: String, profile: String?): String {
        val fromPath = resourcePath.substringAfterLast('.', "")
            .takeIf { it.isNotBlank() && it.length <= 5 }
        if (fromPath != null) return fromPath
        return when {
            profile?.contains("RAW") == true -> "RW2"
            profile?.contains("AVC") == true -> "MP4"
            else -> "JPG"
        }
    }

    private fun parseItem(element: Element): CameraFile? {
        val title = childText(element, "title")
        val resElement = childElement(element, "res") ?: return null
        val resource = resElement.textContent?.trim().orEmpty()
        if (resource.isEmpty()) return null

        val virtualPath = when {
            resource.startsWith("http://") || resource.startsWith("https://") -> resource
            resource.startsWith("/") -> fileBaseUrl.trimEnd('/') + resource
            else -> fileBaseUrl.trimEnd('/') + "/" + resource
        }
        val profile = resElement.getAttribute("protocolInfo").uppercase()
        val realName = cameraFileName(
            element.getAttribute("id"),
            extensionFor(virtualPath, profile),
        )
        val path = virtualPath
        val host = runCatching { path.toHttpUrl().host }.getOrNull() ?: return null
        if (!HostGuard.isLocalHost(host)) return null

        val pathName = path.substringAfterLast('/')
        val titleName = title
            ?.substringAfterLast('/')
            ?.takeIf { it.contains('.') && it.length <= 255 }
        val name = realName ?: titleName ?: pathName
        if (name.isBlank()) return null
        val size = resElement.getAttribute("size").toLongOrNull()
            ?: childText(element, "size")?.toLongOrNull()
            ?: 0L
        val date = childText(element, "date")
            ?: resElement.getAttribute("date").takeIf { it.isNotBlank() }
        return CameraFile(
            path = path,
            name = name,
            sizeBytes = size,
            capturedAtEpochSeconds = parseDate(date),
            kind = MediaKind.fromFileName(name),
        )
    }

    private fun isTag(element: Element, name: String): Boolean =
        element.localName == name || element.tagName == name || element.tagName.endsWith(":$name")

    private fun childElement(element: Element, name: String): Element? {
        val children = element.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            if (isTag(child, name)) return child
        }
        return null
    }

    private fun childText(element: Element, name: String): String? =
        childElement(element, name)?.textContent?.trim()?.takeIf { it.isNotEmpty() }

    private fun parseDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val numeric = value.trim().toLongOrNull()
        if (numeric != null) return if (value.length >= 13) numeric / 1000L else numeric
        return runCatching {
            java.time.OffsetDateTime.parse(value).toEpochSecond()
        }.getOrNull()
    }

    private fun parseXml(value: String) = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isXIncludeAware = false
            isExpandEntityReferences = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        factory.newDocumentBuilder().parse(InputSource(StringReader(value)))
    }.getOrNull()

    private fun org.w3c.dom.NodeList.asElementSequence(): Sequence<Element> =
        (0 until length).asSequence().mapNotNull { item(it) as? Element }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
