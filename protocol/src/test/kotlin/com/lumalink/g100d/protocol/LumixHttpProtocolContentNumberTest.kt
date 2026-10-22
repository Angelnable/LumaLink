package com.lumalink.g100d.protocol

import com.google.common.truth.Truth.assertThat
import java.net.InetAddress
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test

class LumixHttpProtocolContentNumberTest {

    private lateinit var controlServer: MockWebServer
    private lateinit var mediaServer: MockWebServer
    private var mediaMode = "P100"

    @Before
    fun setUp() {
        CameraSessionStore.clear()
        controlServer = MockWebServer()
        controlServer.start()
        mediaServer = MockWebServer()
        mediaServer.start(InetAddress.getByName("127.0.0.1"), 50001)
        mediaServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                return when {
                    path.contains("P1000069", ignoreCase = true) ->
                        MockResponse().setResponseCode(404)
                    mediaMode == "DO" && path.startsWith("/DO0100") ->
                        MockResponse().setBody("x".repeat(123))
                    mediaMode == "P100" && path.startsWith("/P100") ->
                        MockResponse().setBody("x".repeat(123))
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
    }

    @After
    fun tearDown() {
        CameraSessionStore.clear()
        controlServer.shutdown()
        mediaServer.shutdown()
    }

    @Test
    fun enumeratesDoItemIds() = runBlocking {
        mediaMode = "DO"
        controlServer.enqueue(
            MockResponse().setBody(
                """
                <camrply>
                  <result>ok</result>
                  <current_position>1</current_position>
                  <total_content_number>2</total_content_number>
                  <content_number>2</content_number>
                </camrply>
                """.trimIndent(),
            ),
        )

        val protocol = LumixHttpProtocol(
            baseUrl = controlServer.url("/").toString().trimEnd('/'),
        )
        val result = protocol.browse("/DCIM")

        assertThat(result).isInstanceOf(LumixResult.Success::class.java)
        val files = (result as LumixResult.Success).value
        assertThat(files).hasSize(2)
        assertThat(files.first().name).isEqualTo("DO01000001.jpg")
        assertThat(files.last().name).isEqualTo("DO01000002.jpg")
    }

    @Test
    fun enumeratesP100FilesByContentCount() = runBlocking {
        mediaMode = "P100"
        controlServer.enqueue(
            MockResponse().setBody(
                """
                <camrply>
                  <result>ok</result>
                  <current_position>67</current_position>
                  <total_content_number>68</total_content_number>
                  <content_number>68</content_number>
                </camrply>
                """.trimIndent(),
            ),
        )

        val protocol = LumixHttpProtocol(
            baseUrl = controlServer.url("/").toString().trimEnd('/'),
        )
        val result = protocol.browse("/DCIM")

        assertThat(result).isInstanceOf(LumixResult.Success::class.java)
        val files = (result as LumixResult.Success).value
        assertThat(files).hasSize(68)
        assertThat(files.first().name).isEqualTo("P1000001.JPG")
        assertThat(files.first().sizeBytes).isEqualTo(123)
        assertThat(files.last().name).isEqualTo("P1000068.JPG")
    }
}
