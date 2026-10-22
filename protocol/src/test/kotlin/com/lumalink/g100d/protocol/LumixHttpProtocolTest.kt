package com.lumalink.g100d.protocol

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class LumixHttpProtocolTest {

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

    private fun baseUrl(): String = server.url("/").toString().trimEnd('/')

    @Test
    fun handshakeParsesCameraInfo() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """<info><model>DC-G100D</model><firmware>1.2</firmware><serial>ABC123</serial></info>""",
            ),
        )

        val result = LumixHttpProtocol(baseUrl()).handshake()

        assertThat(result).isInstanceOf(LumixResult.Success::class.java)
        val model = (result as LumixResult.Success).value
        assertThat(model.model).isEqualTo("DC-G100D")
        assertThat(model.firmware).isEqualTo("1.2")
        assertThat(model.serial).isEqualTo("ABC123")
        assertThat(server.takeRequest().path).isEqualTo("/cam.cgi?mode=getinfo")
    }

    @Test
    fun handshakeFallsBackToGetStateWhenGetInfoIsMissing() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody("<state><mode>rec</mode></state>"))

        val result = LumixHttpProtocol(baseUrl()).handshake()

        assertThat(result).isInstanceOf(LumixResult.Success::class.java)
        assertThat((result as LumixResult.Success).value.model).isEqualTo("LUMIX G100D")
    }

    @Test
    fun readStatusUsesGetState() = runBlocking {
        server.enqueue(
            MockResponse().setBody("<state><battery>88%</battery><mode>play</mode></state>"),
        )

        val result = LumixHttpProtocol(baseUrl()).readStatus()

        assertThat(result).isInstanceOf(LumixResult.Success::class.java)
        assertThat((result as LumixResult.Success).value.batteryPercent).isEqualTo(88)
        assertThat((result as LumixResult.Success).value.mode).isEqualTo("play")
    }

    @Test
    fun browseReturnsParsedFiles() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """<browse><file name="P1000001.JPG" size="100" /></browse>""",
            ),
        )

        val result = LumixHttpProtocol(
            baseUrl(),
            contentNumberBrowse = false,
        ).browse("/DCIM/100_PANA")

        assertThat(result).isInstanceOf(LumixResult.Success::class.java)
        val files = (result as LumixResult.Success).value
        assertThat(files).hasSize(1)
        assertThat(files.single().path).isEqualTo("/DCIM/100_PANA/P1000001.JPG")
    }

    @Test
    fun browseRetriesDcimWithoutLeadingSlash() = runBlocking {
        server.enqueue(MockResponse().setBody("<browse></browse>"))
        server.enqueue(MockResponse().setBody("""<browse><dir name="100_PANA" /></browse>"""))

        val result = LumixHttpProtocol(
            baseUrl(),
            contentNumberBrowse = false,
        ).browse("/DCIM")

        assertThat(result).isInstanceOf(LumixResult.Success::class.java)
        val files = (result as LumixResult.Success).value
        assertThat(files).hasSize(1)
        assertThat(files.single().isDirectory).isTrue()
        assertThat(files.single().path).isEqualTo("/DCIM/100_PANA")
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun readOnlyGuardBlocksControlBeforeNetworkCall() = runBlocking {
        val client = LumixHttpClient(baseUrl())
        val result = client.getText(
            LumixEndpoints.camCgi(baseUrl(), "camcmd", "value" to "capture"),
        )

        assertThat(result).isInstanceOf(LumixResult.Failure::class.java)
        assertThat((result as LumixResult.Failure).error)
            .isInstanceOf(LumixError.ReadOnlyViolation::class.java)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun openFileStreamsBytesAndGuardAllowsDcimPath() = runBlocking {
        server.enqueue(MockResponse().setBody("JPEGDATA"))
        val protocol = LumixHttpProtocol(baseUrl())

        val result = protocol.openFile(
            CameraFile(
                path = "/DCIM/100_PANA/P1000001.JPG",
                name = "P1000001.JPG",
                sizeBytes = 8,
            ),
        )

        assertThat(result).isInstanceOf(LumixResult.Success::class.java)
        val response = (result as LumixResult.Success).value
        assertThat(response.body?.bytes()).isEqualTo("JPEGDATA".toByteArray())
        response.close()
    }
}
