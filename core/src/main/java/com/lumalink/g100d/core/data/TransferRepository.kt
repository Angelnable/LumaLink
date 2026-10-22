package com.lumalink.g100d.core.data

import java.util.UUID
import kotlinx.coroutines.flow.Flow

class TransferRepository(private val dao: TransferDao) {

    val transfers: Flow<List<TransferEntity>> = dao.observeAll()

    suspend fun enqueue(items: List<NewTransfer>): List<String> {
        val now = System.currentTimeMillis()
        return items.map { item ->
            val id = UUID.randomUUID().toString()
            dao.insert(
                TransferEntity(
                    id = id,
                    cameraPath = item.cameraPath,
                    fileName = item.fileName,
                    sizeBytes = item.sizeBytes,
                    mimeType = item.mimeType,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            id
        }
    }

    suspend fun nextQueued(): TransferEntity? = dao.nextQueued()

    suspend fun findById(id: String): TransferEntity? = dao.findById(id)

    suspend fun updateProgress(
        id: String,
        state: TransferState,
        bytes: Long,
        speed: Long,
    ) {
        dao.updateProgress(id, state, bytes, speed, System.currentTimeMillis())
    }

    suspend fun markCompleted(
        id: String,
        bytes: Long,
        localUri: String,
        sha256: String,
    ) {
        dao.markCompleted(id, bytes, localUri, sha256, System.currentTimeMillis())
    }

    suspend fun markFailed(id: String, error: String) {
        dao.markFailed(id, error, System.currentTimeMillis())
    }

    suspend fun markCanceled(id: String) {
        dao.markCanceled(id, System.currentTimeMillis())
    }

    suspend fun retry(id: String) {
        dao.requeue(id, System.currentTimeMillis())
    }

    suspend fun failStaleRunning(error: String) {
        dao.failRunning(error, System.currentTimeMillis())
    }

    suspend fun delete(id: String) {
        dao.deleteById(id)
    }

    suspend fun clearFinished() {
        dao.clearFinished()
    }
}
