package com.lumalink.g100d.protocol

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class UpnpDescriptionResolverTest {

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
    fun resolvesContentDirectoryControlUrl() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                <root xmlns="urn:schemas-upnp-org:device-1-0">
                  <URLBase>http://192.168.54.1:60606/</URLBase>
                  <device>
                    <serviceList>
                      <service>
                        <serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType>
                        <controlURL>/Server0/CDS_control</controlURL>
                      </service>
                    </serviceList>
                  </device>
                </root>
                """.trimIndent(),
            ),
        )

        val resolver = UpnpDescriptionResolver(
            baseUrl = "http://127.0.0.1",
            descriptionUrlsOverride = listOf(
                server.url("/Server0/description.xml").toString(),
            ),
        )
        val result = resolver.resolve()

        assertThat(result).isInstanceOf(LumixResult.Success::class.java)
        assertThat((result as LumixResult.Success).value)
            .isEqualTo("http://192.168.54.1:60606/Server0/CDS_control")
    }

    @Test
    fun resolvesAbsoluteControlUrlWithoutUrlBase() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """
                <root xmlns="urn:schemas-upnp-org:device-1-0" xmlns:pana="urn:schemas-panasonic-com:pana">
                  <device>
                    <serviceList>
                      <service>
                        <serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType>
                        <controlURL>http://192.168.54.1:60606/Server0/CDS_control</controlURL>
                      </service>
                    </serviceList>
                  </device>
                </root>
                """.trimIndent(),
            ),
        )

        val resolver = UpnpDescriptionResolver(
            baseUrl = "http://127.0.0.1",
            descriptionUrlsOverride = listOf(
                server.url("/Lumix/Server0/ddd").toString(),
            ),
        )
        val result = resolver.resolve()

        assertThat(result).isInstanceOf(LumixResult.Success::class.java)
        assertThat((result as LumixResult.Success).value)
            .isEqualTo("http://192.168.54.1:60606/Server0/CDS_control")
    }

    @Test
    fun returnsFailureWhenDescriptionHasNoContentDirectory() = runBlocking {
        server.enqueue(MockResponse().setBody("<root><device /></root>"))

        val resolver = UpnpDescriptionResolver(
            baseUrl = "http://127.0.0.1",
            descriptionUrlsOverride = listOf(
                server.url("/Server0/description.xml").toString(),
            ),
        )
        val result = resolver.resolve()

        assertThat(result).isInstanceOf(LumixResult.Failure::class.java)
    }
}
