package com.lumalink.g100d.protocol

import java.io.StringReader
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

/**
 * Tolerant parser for the undocumented browse response. The G100D format has
 * not been confirmed yet, so this accepts attributes and child elements with
 * several common names and falls back to scanning the body for file names.
 */
object LumixBrowseParser {

    fun parse(body: String, baseDirectory: String = "/DCIM"): List<CameraFile> {
        val files = linkedMapOf<String, CameraFile>()
        val document = parseDocument(body)
        if (document != null) {
            val nodes = document.getElementsByTagName("*")
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                readFile(element, baseDirectory)?.let { file ->
                    files.putIfAbsent(file.path, file)
                }
            }
        }

        if (files.isEmpty()) {
            FILE_NAME_PATTERN.findAll(body).forEach { match ->
                val name = match.groupValues[1]
                val path = joinPath(baseDirectory, name)
                files.putIfAbsent(
                    path,
                    CameraFile(
                        path = path,
                        name = name,
                        sizeBytes = 0L,
                        kind = MediaKind.fromFileName(name),
                    ),
                )
            }
        }
        return files.values.toList()
    }

    private fun readFile(element: Element, baseDirectory: String): CameraFile? {
        val values = readValues(element)
        val type = value(values, "type", "kind", "mediatype")
        val tag = element.tagName.lowercase()
        val isDirectory = (type != null && type.lowercase() in FOLDER_TYPES) ||
            tag in FOLDER_TAGS

        val pathValue = value(values, "path", "fullpath", "url", "fileurl")
        val rawName = value(values, "name", "filename", "file", "basename")
            ?: pathValue?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: run {
                val text = element.textContent?.trim().orEmpty()
                if (text.matches(FILE_NAME_ONLY)) text else null
            }
            ?: return null
        val path = when {
            pathValue.isNullOrBlank() -> joinPath(baseDirectory, rawName)
            pathValue.startsWith("http://") || pathValue.startsWith("https://") -> pathValue
            pathValue.startsWith("/") -> pathValue
            pathValue.startsWith("DCIM/") || pathValue.startsWith("PRIVATE/") -> "/$pathValue"
            pathValue.contains('/') &&
                pathValue.substringBefore('/') ==
                baseDirectory.trim('/').substringAfterLast('/') ->
                "${baseDirectory.substringBeforeLast('/', "")}/$pathValue"

            else -> joinPath(baseDirectory, pathValue)
        }
        if (path.contains("..")) return null

        val name = rawName.substringAfterLast('/').ifBlank {
            path.substringAfterLast('/')
        }
        if (name.isBlank()) return null
        if (isDirectory) {
            return CameraFile(
                path = path,
                name = name,
                sizeBytes = 0L,
                isDirectory = true,
            )
        }
        if (!name.contains('.') && value(values, "size", "bytes") == null) {
            return null
        }

        val size = value(values, "size", "bytes", "length", "filesize")
            ?.filter { it.isDigit() }
            ?.toLongOrNull()
            ?: 0L
        val capturedAt = parseDate(value(values, "date", "time", "datetime", "created", "timestamp"))
        val kind = kindFromType(type) ?: MediaKind.fromFileName(name)

        return CameraFile(
            path = path,
            name = name,
            sizeBytes = size,
            capturedAtEpochSeconds = capturedAt,
            kind = kind,
            thumbnailPath = value(values, "thumb", "thumbnail", "thumbpath", "thumbnailurl"),
        )
    }

    private fun readValues(element: Element): Map<String, String> {
        val values = linkedMapOf<String, String>()
        val attributes = element.attributes
        for (index in 0 until attributes.length) {
            val attribute = attributes.item(index)
            val value = attribute.nodeValue?.trim().orEmpty()
            if (value.isNotEmpty()) {
                values.putIfAbsent(normalize(attribute.nodeName), value)
            }
        }
        val children = element.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index) as? Element ?: continue
            val text = child.textContent?.trim().orEmpty()
            if (text.isNotEmpty()) {
                values.putIfAbsent(normalize(child.tagName), text)
            }
        }
        return values
    }

    private fun value(values: Map<String, String>, vararg keys: String): String? {
        for (key in keys) {
            values[normalize(key)]?.let { if (it.isNotBlank()) return it.trim() }
        }
        return null
    }

    private fun normalize(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }

    private fun joinPath(baseDirectory: String, name: String): String {
        val cleanName = name.trim().trimStart('/')
        if (cleanName.startsWith("DCIM/") || cleanName.startsWith("PRIVATE/")) {
            return "/$cleanName"
        }
        return "${baseDirectory.trimEnd('/')}/$cleanName"
    }

    private fun kindFromType(type: String?): MediaKind? {
        val normalized = type?.lowercase() ?: return null
        return when {
            "raw" in normalized || "rw2" in normalized -> MediaKind.RAW
            "jpg" in normalized || "jpeg" in normalized || "photo" in normalized -> MediaKind.JPEG
            "video" in normalized || "movie" in normalized || "mp4" in normalized -> MediaKind.VIDEO
            else -> null
        }
    }

    private fun parseDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()
        if (trimmed.all { it.isDigit() }) {
            val number = trimmed.toLongOrNull() ?: return null
            return if (trimmed.length >= 13) number / 1000L else number
        }
        for (pattern in DATE_PATTERNS) {
            val parsed = runCatching {
                LocalDateTime.parse(trimmed, DateTimeFormatter.ofPattern(pattern, Locale.US))
                    .atZone(ZoneId.systemDefault())
                    .toEpochSecond()
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    private fun parseDocument(body: String) = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isXIncludeAware = false
            isExpandEntityReferences = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        factory.newDocumentBuilder().parse(InputSource(StringReader(body)))
    }.getOrNull()

    private val FILE_NAME_ONLY = Regex(
        """(?i)^[A-Z0-9_\-]+\.(?:JPG|JPEG|RW2|MP4|MOV|AVI|M4V)$""",
    )
    private val FILE_NAME_PATTERN = Regex(
        """(?i)\b([A-Z0-9_\-]+\.(?:JPG|JPEG|RW2|MP4|MOV|AVI|M4V))\b""",
    )
    private val FOLDER_TYPES = setOf("folder", "directory", "dir")
    private val FOLDER_TAGS = setOf("dir", "directory", "folder")
    private val DATE_PATTERNS = listOf(
        "yyyyMMdd'T'HHmmss",
        "yyyyMMddHHmmss",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy:MM:dd HH:mm:ss",
    )
}
