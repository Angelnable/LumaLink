package com.lumalink.g100d.protocol

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class UpnpContentDirectoryClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun parsesDidlItemsFromBrowseResponse() = runBlocking {
        val didl = """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
                xmlns:dc="http://purl.org/dc/elements/1.1/">
              <item id="1" parentID="0" restricted="1">
                <dc:title>P1000001.JPG</dc:title>
                <res protocolInfo="http-get:*:image/jpeg:*" size="12345">
                  http://192.168.54.1:50001/DCIM/100_PANA/P1000001.JPG
                </res>
              </item>
            </DIDL-Lite>
        """.trimIndent()
        val escaped = didl
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
        val soap = """
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <u:BrowseResponse xmlns:u="urn:schemas-upnp-org:service:ContentDirectory:1">
                  <Result>$escaped</Result>
                  <NumberReturned>1</NumberReturned>
                  <TotalMatches>1</TotalMatches>
                </u:BrowseResponse>
              </s:Body>
            </s:Envelope>
        """.trimIndent()
        server.enqueue(MockResponse().setBody(soap))

        val client = UpnpContentDirectoryClient(
            baseUrl = "http://127.0.0.1",
            soapUrlOverride = server.url("/Server0/CDS_control").toString(),
            fileBaseUrlOverride = server.url("/").toString(),
            tryLumixPrefix = false,
        )
        val result = client.browseAll()

        assertThat(result).isInstanceOf(LumixResult.Success::class.java)
        val files = (result as LumixResult.Success).value
        assertThat(files).hasSize(1)
        val file = files.single()
        assertThat(file.name).isEqualTo("P1000001.JPG")
        assertThat(file.path).isEqualTo(
            "http://192.168.54.1:50001/DCIM/100_PANA/P1000001.JPG",
        )
        assertThat(file.sizeBytes).isEqualTo(12345)
        assertThat(file.kind).isEqualTo(MediaKind.JPEG)

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.getHeader("SOAPAction")).contains("ContentDirectory:1#Browse")
        assertThat(request.body.readUtf8()).contains("BrowseDirectChildren")
    }

    @Test
    fun retriesLumixPrefixedControlUrlAfter403() = runBlocking {
        val didl = """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
                xmlns:dc="http://purl.org/dc/elements/1.1/">
              <item id="01002279">
                <dc:title>P1002279.JPG</dc:title>
                <res protocolInfo="http-get:*:image/jpeg:*" size="7">
                  http://192.168.54.1:50001/DO01002279.jpg
                </res>
              </item>
            </DIDL-Lite>
        """.trimIndent()
        val escaped = didl.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val soap = """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><Result>$escaped</Result></s:Body></s:Envelope>"""
        server.dispatcher = object : okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return if (path.startsWith("/Lumix/Server0/CDS_control")) {
                    MockResponse().setBody(soap)
                } else {
                    MockResponse().setResponseCode(403)
                }
            }
        }

        val client = UpnpContentDirectoryClient(
            baseUrl = "http://127.0.0.1",
            soapUrlOverride = server.url("/Server0/CDS_control").toString(),
            fileBaseUrlOverride = server.url("/").toString(),
            tryLumixPrefix = true,
        )
        val result = client.browseAll()

        assertThat(result).isInstanceOf(LumixResult.Success::class.java)
        val files = (result as LumixResult.Success).value
        assertThat(files.single().name).isEqualTo("P1002279.JPG")
        assertThat(files.single().path).isEqualTo("http://192.168.54.1:50001/DO01002279.jpg")
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test
    fun resolvesRelativeResourceAgainstFileServer() = runBlocking {
        val didl = """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
                xmlns:dc="http://purl.org/dc/elements/1.1/">
              <item id="2">
                <dc:title>P1000002.RW2</dc:title>
                <res size="99">/DCIM/100_PANA/P1000002.RW2</res>
              </item>
            </DIDL-Lite>
        """.trimIndent()
        val escaped = didl.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        server.enqueue(
            MockResponse().setBody(
                """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body><Result>$escaped</Result></s:Body></s:Envelope>""",
            ),
        )

        val client = UpnpContentDirectoryClient(
            baseUrl = "http://127.0.0.1",
            soapUrlOverride = server.url("/Server0/CDS_control").toString(),
            fileBaseUrlOverride = "http://192.168.54.1:50001/",
            tryLumixPrefix = false,
        )
        val result = client.browseAll() as LumixResult.Success
        val file = result.value.single()

        assertThat(file.path).isEqualTo("http://192.168.54.1:50001/DCIM/100_PANA/P1000002.RW2")
        assertThat(file.kind).isEqualTo(MediaKind.RAW)
    }
}
