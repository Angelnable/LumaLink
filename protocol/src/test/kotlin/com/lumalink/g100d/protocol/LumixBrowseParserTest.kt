package com.lumalink.g100d.protocol

import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class LumixBrowseParserTest {

    @Test
    fun parsesAttributeBasedBrowseResponse() {
        val body = """
            <browse>
              <file name="P1000001.JPG" size="12345" date="2024-01-02 12:34:56" />
              <file filename="P1000002.RW2" bytes="67890" type="raw" />
              <dir name="100_PANA" type="folder" />
            </browse>
        """.trimIndent()

        val files = LumixBrowseParser.parse(body, "/DCIM/100_PANA")

        assertThat(files).hasSize(3)
        assertThat(files.first { it.name == "100_PANA" }.isDirectory).isTrue()
        val jpeg = files.first { it.name == "P1000001.JPG" }
        assertThat(jpeg.path).isEqualTo("/DCIM/100_PANA/P1000001.JPG")
        assertThat(jpeg.sizeBytes).isEqualTo(12345)
        assertThat(jpeg.kind).isEqualTo(MediaKind.JPEG)
        assertThat(jpeg.capturedAtEpochSeconds).isEqualTo(
            LocalDateTime.of(2024, 1, 2, 12, 34, 56)
                .atZone(ZoneId.systemDefault())
                .toEpochSecond(),
        )

        val raw = files.first { it.name == "P1000002.RW2" }
        assertThat(raw.sizeBytes).isEqualTo(67890)
        assertThat(raw.kind).isEqualTo(MediaKind.RAW)
    }

    @Test
    fun fallsBackToFileNameScanWhenXmlHasNoFileElements() {
        val files = LumixBrowseParser.parse(
            "camera says: P1000001.JPG and P1000002.MP4 are ready",
            "/DCIM",
        )

        assertThat(files.map { it.name }).containsExactly("P1000001.JPG", "P1000002.MP4")
        assertThat(files.map { it.kind }).containsExactly(MediaKind.JPEG, MediaKind.VIDEO)
    }

    @Test
    fun treatsDirTagAsDirectoryWithoutTypeAttribute() {
        val body = """<browse><dir name="100_PANA" /></browse>"""

        val file = LumixBrowseParser.parse(body, "/DCIM").single()

        assertThat(file.isDirectory).isTrue()
        assertThat(file.name).isEqualTo("100_PANA")
        assertThat(file.path).isEqualTo("/DCIM/100_PANA")
    }

    @Test
    fun resolvesRelativePathThatRepeatsFolderName() {
        val body = """<files><file path="100_PANA/P1000004.JPG" size="7" /></files>"""

        val file = LumixBrowseParser.parse(body, "/DCIM/100_PANA").single()

        assertThat(file.name).isEqualTo("P1000004.JPG")
        assertThat(file.path).isEqualTo("/DCIM/100_PANA/P1000004.JPG")
    }

    @Test
    fun parsesFullPathWithoutDuplicatingDirectory() {
        val body = """<files><file path="/DCIM/100_PANA/P1000003.JPG" size="42" /></files>"""

        val file = LumixBrowseParser.parse(body, "/DCIM").single()

        assertThat(file.path).isEqualTo("/DCIM/100_PANA/P1000003.JPG")
        assertThat(file.name).isEqualTo("P1000003.JPG")
        assertThat(file.sizeBytes).isEqualTo(42)
    }
}
