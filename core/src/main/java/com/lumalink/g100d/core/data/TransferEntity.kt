package com.lumalink.g100d.core.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TransferState {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELED,
}

@Entity(
    tableName = "transfers",
    indices = [Index("state"), Index("createdAt")],
)
data class TransferEntity(
    @PrimaryKey val id: String,
    val cameraPath: String,
    val fileName: String,
    val sizeBytes: Long,
    val mimeType: String,
    val state: TransferState = TransferState.QUEUED,
    val bytesDownloaded: Long = 0L,
    val speedBytesPerSecond: Long = 0L,
    val errorMessage: String? = null,
    val localUri: String? = null,
    val sha256: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class NewTransfer(
    val cameraPath: String,
    val fileName: String,
    val sizeBytes: Long,
    val mimeType: String,
)
