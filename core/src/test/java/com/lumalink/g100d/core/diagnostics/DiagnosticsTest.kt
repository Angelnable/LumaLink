package com.lumalink.g100d.core.diagnostics

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test

class DiagnosticsTest {

    @Before
    fun setUp() {
        Diagnostics.clear()
    }

    @After
    fun tearDown() {
        Diagnostics.clear()
    }

    @Test
    fun recordsEntriesAndFormatsText() {
        Diagnostics.info("connected")
        Diagnostics.error("timeout")

        assertThat(Diagnostics.entries.value).hasSize(2)
        assertThat(Diagnostics.asText()).contains("connected")
        assertThat(Diagnostics.asText()).contains("ERROR timeout")
    }

    @Test
    fun httpEntryIncludesMethodPathAndStatus() {
        Diagnostics.logHttp("GET", "http://192.168.54.1/cam.cgi?mode=getstate", 200, 42)

        val text = Diagnostics.asText()
        assertThat(text).contains("GET http://192.168.54.1/cam.cgi?mode=getstate")
        assertThat(text).contains("HTTP 200")
        assertThat(text).contains("42ms")
    }
}
