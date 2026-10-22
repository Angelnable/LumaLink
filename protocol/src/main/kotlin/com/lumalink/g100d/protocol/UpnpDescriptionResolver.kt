package com.lumalink.g100d.protocol

import java.io.StringReader
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import org.xml.sax.InputSource

/**
 * Resolves the real ContentDirectory control URL from the camera's UPnP device
 * description. Some firmware returns 403 for the guessed /Server0/CDS_control
 * path, so we probe the description documents first. All requests are GETs.
 */
class UpnpDescriptionResolver(
    baseUrl: String,
    private val descriptionUrlsOverride: List<String>? = null,
    private val client: OkHttpClient = defaultClient(),
    private val logger: LumixLogger? = null,
    private val ssdp: UpnpSsdpDiscovery? = UpnpSsdpDiscovery(),
) {
    private val host = baseUrl.toHttpUrl().host

    suspend fun resolve(): LumixResult<String> = withContext(Dispatchers.IO) {
        if (!HostGuard.isLocalHost(host)) {
            return@withContext LumixResult.Failure(
                LumixError.ReadOnlyViolation("non-local UPnP host: $host"),
            )
        }
        val candidates = descriptionUrlsOverride ?: listOf(
            "http://$host:60606/Server0/description.xml",
            "http://$host:60606/Server0/",
            "http://$host:60606/description.xml",
            "http://$host:60606/",
        )
        var lastError: LumixError? = null
        for (url in candidates) {
            val request = try {
                Request.Builder().url(url).get().build()
            } catch (error: IllegalArgumentException) {
                continue
            }
            if (!HostGuard.isLocalHost(request.url.host)) continue
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    logger?.log(
                        LumixHttpLogEntry(
                            timestamp = System.currentTimeMillis(),
                            method = "GET",
                            url = url,
                            statusCode = response.code,
                            durationMs = 0,
                        ),
                    )
                    logger?.logBody(
                        url,
                        "HTTP ${response.code} headers: ${response.headers}\n$body",
                    )
                    if (!response.isSuccessful) {
                        lastError = LumixError.ProtocolMismatch(
                            "UPnP description HTTP ${response.code} at $url",
                        )
                        return@use
                    }
                    parseControlUrl(body, url)?.let { controlUrl ->
                        return@withContext LumixResult.Success(controlUrl)
                    }
                    lastError = LumixError.ProtocolMismatch(
                        "No ContentDirectory controlURL in $url",
                    )
                }
            } catch (error: Throwable) {
                lastError = LumixError.Offline(
                    error.message ?: "UPnP description failed",
                    error,
                )
            }
        }
        if (descriptionUrlsOverride == null && ssdp != null) {
            when (val location = ssdp.discoverLocation()) {
                is LumixResult.Success -> {
                    logger?.logBody("upnp-ssdp", "LOCATION=${location.value}")
                    val request = runCatching {
                        Request.Builder().url(location.value).get().build()
                    }.getOrNull()
                    if (request != null && HostGuard.isLocalHost(request.url.host)) {
                        try {
                            client.newCall(request).execute().use { response ->
                                val body = response.body?.string().orEmpty()
                                logger?.logBody(
                                    location.value,
                                    "HTTP ${response.code} headers: ${response.headers}\n$body",
                                )
                                if (response.isSuccessful) {
                                    parseControlUrl(body, location.value)?.let { control ->
                                        return@withContext LumixResult.Success(control)
                                    }
                                }
                            }
                        } catch (error: Throwable) {
                            lastError = LumixError.Offline(
                                error.message ?: "SSDP description failed",
                                error,
                            )
                        }
                    }
                }

                is LumixResult.Failure -> {
                    logger?.logBody("upnp-ssdp", "failed: ${location.error.message}")
                }
            }
        }

        LumixResult.Failure(
            lastError ?: LumixError.ProtocolMismatch("UPnP description unavailable"),
        )
    }

    private fun parseControlUrl(xml: String, baseUrl: String): String? {
        val document = runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isXIncludeAware = false
                isExpandEntityReferences = false
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            }
            factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        }.getOrNull() ?: return null

        val urlBase = document.getElementsByTagName("*")
            .asElementSequence()
            .firstOrNull { it.localName == "URLBase" }
            ?.textContent
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        val rootBase = urlBase ?: baseUrl

        val services = document.getElementsByTagName("*").asElementSequence()
            .filter { it.localName == "service" || it.tagName.endsWith(":service") }
        for (service in services) {
            val type = childText(service, "serviceType").orEmpty()
            if (!type.contains("ContentDirectory", ignoreCase = true)) continue
            val control = childText(service, "controlURL") ?: continue
            return runCatching {
                rootBase.toHttpUrl().resolve(control)?.toString()
            }.getOrNull()
        }

        // Namespace/parser variations: fall back to a strict textual extraction
        // of the ContentDirectory controlURL. This is still the same read-only
        // description document.
        val textual = Regex(
            """<(?:[\w-]+:)?controlURL[^>]*>\s*([^<]+?)\s*</(?:[\w-]+:)?controlURL>""",
            RegexOption.IGNORE_CASE,
        ).find(xml)?.groupValues?.getOrNull(1)?.trim()
        if (!textual.isNullOrEmpty()) {
            return runCatching {
                if (textual.startsWith("http://") || textual.startsWith("https://")) {
                    textual
                } else {
                    rootBase.toHttpUrl().resolve(textual)?.toString()
                }
            }.getOrNull()
        }
        return null
    }

    private fun childText(element: Element, name: String): String? {
        val children = element.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            if (child.localName == name || child.tagName == name ||
                child.tagName.endsWith(":$name")
            ) {
                return child.textContent?.trim()?.takeIf { it.isNotEmpty() }
            }
        }
        return null
    }

    private fun org.w3c.dom.NodeList.asElementSequence(): Sequence<Element> =
        (0 until length).asSequence().mapNotNull { item(it) as? Element }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
