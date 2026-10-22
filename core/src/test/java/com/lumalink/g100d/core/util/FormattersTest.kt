package com.lumalink.g100d.core.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormattersTest {

    @Test
    fun bytesBelowOneKilobyteUseByteUnit() {
        assertThat(formatBytes(0)).isEqualTo("0 B")
        assertThat(formatBytes(1023)).isEqualTo("1023 B")
    }

    @Test
    fun bytesUseBinaryUnits() {
        assertThat(formatBytes(1024)).isEqualTo("1.0 KB")
        assertThat(formatBytes(1536)).isEqualTo("1.5 KB")
        assertThat(formatBytes(10L * 1024L)).isEqualTo("10 KB")
        assertThat(formatBytes(5L * 1024L * 1024L)).isEqualTo("5.0 MB")
    }

    @Test
    fun speedAppendsPerSecondSuffix() {
        assertThat(formatSpeed(1024)).isEqualTo("1.0 KB/s")
    }
}
