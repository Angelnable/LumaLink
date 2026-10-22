package com.lumalink.g100d.protocol

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class LumixControlClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        CameraSessionStore.clear()
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        CameraSessionStore.clear()
        server.shutdown()
    }

    @Test
    fun waitsForCameraApprovalAndStoresSession() = runBlocking {
        server.enqueue(
            MockResponse().setBody("ok_under_research_no_msg,G100D-XXXXXX,remote,encrypted"),
        )
        server.enqueue(
            MockResponse().setBody("ok,G100D-XXXXXX,remote,encrypted,SESSION-123"),
        )
        server.enqueue(MockResponse().setBody("<camrply><result>ok</result></camrply>"))
        val baseUrl = server.url("/").toString().trimEnd('/')
        val client = LumixControlClient(baseUrl)
        val statuses = mutableListOf<String>()

        val result = client.requestAccess { statuses += it }

        assertThat(result).isInstanceOf(LumixResult.Success::class.java)
        assertThat((result as LumixResult.Success).value).isEqualTo("SESSION-123")
        assertThat(CameraSessionStore.sessionId).isEqualTo("SESSION-123")
        assertThat(server.requestCount).isEqualTo(2)

        client.enterPlaybackMode()
        repeat(2) { server.takeRequest() }
        val playback = server.takeRequest()
        assertThat(playback.getHeader("X-SESSION_ID")).isEqualTo("SESSION-123")
    }

    @Test
    fun sendsOnlyFixedPlaymodeCommand() = runBlocking {
        server.enqueue(MockResponse().setBody("<camrply><result>ok</result></camrply>"))
        val baseUrl = server.url("/").toString().trimEnd('/')

        val result = LumixControlClient(baseUrl).enterPlaybackMode()

        assertThat(result).isInstanceOf(LumixResult.Success::class.java)
        assertThat(server.takeRequest().path)
            .isEqualTo("/cam.cgi?mode=camcmd&value=playmode")
    }

    @Test
    fun reportsCameraError() = runBlocking {
        server.enqueue(MockResponse().setBody("<camrply><result>err_param</result></camrply>"))
        val baseUrl = server.url("/").toString().trimEnd('/')

        val result = LumixControlClient(baseUrl).enterPlaybackMode()

        assertThat(result).isInstanceOf(LumixResult.Failure::class.java)
    }
}
