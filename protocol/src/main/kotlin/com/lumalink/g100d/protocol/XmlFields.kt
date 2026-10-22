package com.lumalink.g100d.protocol

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource

internal object XmlFields {

    fun extract(body: String): Map<String, String> {
        val values = linkedMapOf<String, String>()
        val document = parse(body)
        if (document != null) {
            val nodes = document.getElementsByTagName("*")
            for (index in 0 until nodes.length) {
                val element = nodes.item(index) as? Element ?: continue
                val tag = normalize(element.tagName)
                val attributes = element.attributes
                for (attributeIndex in 0 until attributes.length) {
                    val attribute = attributes.item(attributeIndex)
                    val value = attribute.nodeValue?.trim().orEmpty()
                    if (value.isNotEmpty()) {
                        values.putIfAbsent(normalize(attribute.nodeName), value)
                    }
                }
                if (element.childNodes.length == 1 && element.firstChild.nodeType == Node.TEXT_NODE) {
                    val text = element.textContent?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        values.putIfAbsent(tag, text)
                    }
                }
            }
        }

        if (values.isEmpty()) {
            FALLBACK_PATTERN.findAll(body).forEach { match ->
                val key = normalize(match.groupValues[1])
                val value = match.groupValues[2]
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    values.putIfAbsent(key, value)
                }
            }
        }
        return values
    }

    fun value(values: Map<String, String>, vararg keys: String): String? {
        for (key in keys) {
            values[normalize(key)]?.let { if (it.isNotBlank()) return it.trim() }
        }
        return null
    }

    fun normalize(key: String): String = key.lowercase().filter { it.isLetterOrDigit() }

    private fun parse(body: String) = runCatching {
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

    private val FALLBACK_PATTERN = Regex(
        """([A-Za-z_][A-Za-z0-9_\-]*)\s*[=:]\s*([^\s<>&,;]+)""",
    )
}
