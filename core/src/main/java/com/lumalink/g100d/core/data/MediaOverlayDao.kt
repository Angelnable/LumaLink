package com.lumalink.g100d.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaOverlayDao {

    @Query("SELECT * FROM media_overlays")
    fun observeAll(): Flow<List<MediaOverlayEntity>>

    @Query("SELECT * FROM media_overlays WHERE uri = :uri LIMIT 1")
    suspend fun find(uri: String): MediaOverlayEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MediaOverlayEntity)

    @Query("DELETE FROM media_overlays WHERE uri = :uri")
    suspend fun delete(uri: String)
}
