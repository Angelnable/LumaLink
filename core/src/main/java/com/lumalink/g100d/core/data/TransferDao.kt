package com.lumalink.g100d.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {

    @Query("SELECT * FROM transfers ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE state = 'QUEUED' ORDER BY createdAt ASC LIMIT 1")
    suspend fun nextQueued(): TransferEntity?

    @Query("SELECT * FROM transfers WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): TransferEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TransferEntity)

    @Query(
        "UPDATE transfers SET state = :state, bytesDownloaded = :bytes, " +
            "speedBytesPerSecond = :speed, updatedAt = :now WHERE id = :id",
    )
    suspend fun updateProgress(
        id: String,
        state: TransferState,
        bytes: Long,
        speed: Long,
        now: Long,
    )

    @Query(
        "UPDATE transfers SET state = 'COMPLETED', bytesDownloaded = :bytes, " +
            "speedBytesPerSecond = 0, localUri = :localUri, sha256 = :sha256, " +
            "errorMessage = NULL, updatedAt = :now WHERE id = :id",
    )
    suspend fun markCompleted(
        id: String,
        bytes: Long,
        localUri: String,
        sha256: String,
        now: Long,
    )

    @Query(
        "UPDATE transfers SET state = 'FAILED', speedBytesPerSecond = 0, " +
            "errorMessage = :error, updatedAt = :now WHERE id = :id",
    )
    suspend fun markFailed(id: String, error: String, now: Long)

    @Query(
        "UPDATE transfers SET state = 'CANCELED', speedBytesPerSecond = 0, " +
            "updatedAt = :now WHERE id = :id",
    )
    suspend fun markCanceled(id: String, now: Long)

    @Query(
        "UPDATE transfers SET state = 'QUEUED', bytesDownloaded = 0, " +
            "speedBytesPerSecond = 0, errorMessage = NULL, updatedAt = :now WHERE id = :id",
    )
    suspend fun requeue(id: String, now: Long)

    /**
     * A killed service can leave rows RUNNING forever. New queue starts heal
     * those rows so the next selection is not blocked behind a ghost transfer.
     */
    @Query(
        "UPDATE transfers SET state = 'FAILED', speedBytesPerSecond = 0, " +
            "errorMessage = :error, updatedAt = :now WHERE state = 'RUNNING'",
    )
    suspend fun failRunning(error: String, now: Long)

    @Query("DELETE FROM transfers WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM transfers WHERE state IN ('COMPLETED','CANCELED')")
    suspend fun clearFinished()
}
