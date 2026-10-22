package com.lumalink.g100d.protocol

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LumixEndpointsTest {

    @Test
    fun stateEndpointUsesCandidateCgiPath() {
        assertThat(LumixEndpoints.state())
            .isEqualTo("http://192.168.54.1/cam.cgi?mode=getstate")
    }

    @Test
    fun browseEndpointKeepsDirectoryParameter() {
        assertThat(LumixEndpoints.browse(directory = "/DCIM/100_PANA"))
            .isEqualTo("http://192.168.54.1/cam.cgi?mode=browse&dir=/DCIM/100_PANA")
    }

    @Test
    fun baseUrlTrailingSlashIsNormalised() {
        assertThat(LumixEndpoints.browse(baseUrl = "http://192.168.54.1/"))
            .isEqualTo("http://192.168.54.1/cam.cgi?mode=browse&dir=/DCIM")
    }
}
