package com.lumalink.g100d.protocol

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LumixStatusParserTest {

    @Test
    fun parsesBatteryModeRecordingAndRemainingShots() {
        val body = """
            <state>
              <battery>75%</battery>
              <mode>rec</mode>
              <recording>false</recording>
              <remain>123</remain>
            </state>
        """.trimIndent()

        val status = LumixStatusParser.parse(body)

        assertThat(status.batteryPercent).isEqualTo(75)
        assertThat(status.mode).isEqualTo("rec")
        assertThat(status.recording).isFalse()
        assertThat(status.remainingShots).isEqualTo(123)
    }

    @Test
    fun doesNotMistakeBatteryBarsForPercent() {
        val status = LumixStatusParser.parse("<state><battery>4</battery></state>")
        assertThat(status.batteryPercent).isNull()
    }

    @Test
    fun acceptsRecordingFlag() {
        val status = LumixStatusParser.parse("<state><recording>true</recording></state>")
        assertThat(status.recording).isTrue()
    }
}
