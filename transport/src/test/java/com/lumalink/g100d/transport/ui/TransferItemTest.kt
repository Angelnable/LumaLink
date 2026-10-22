package com.lumalink.g100d.transport.ui

import com.google.common.truth.Truth.assertThat
import com.lumalink.g100d.core.data.TransferState
import org.junit.Test

class TransferItemTest {

    @Test
    fun computesProgressAndEta() {
        val item = TransferItem(
            id = "1",
            fileName = "P1000001.JPG",
            sizeBytes = 1_000L,
            bytesDownloaded = 250L,
            speedBytesPerSecond = 100L,
            state = TransferState.RUNNING,
            errorMessage = null,
            localUri = null,
            mimeType = "image/jpeg",
            updatedAt = 0L,
        )

        assertThat(item.progress).isEqualTo(0.25f)
        assertThat(item.etaSeconds).isEqualTo(7L)
    }

    @Test
    fun doesNotInventProgressForUnknownSize() {
        val item = TransferItem(
            id = "2",
            fileName = "P1000002.RW2",
            sizeBytes = 0L,
            bytesDownloaded = 0L,
            speedBytesPerSecond = 0L,
            state = TransferState.QUEUED,
            errorMessage = null,
            localUri = null,
            mimeType = "application/octet-stream",
            updatedAt = 0L,
        )

        assertThat(item.progress).isNull()
        assertThat(item.etaSeconds).isNull()
    }
}
