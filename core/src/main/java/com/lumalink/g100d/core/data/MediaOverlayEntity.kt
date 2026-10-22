package com.lumalink.g100d.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User-owned metadata overlaid on a MediaStore item. The media itself stays in
 * MediaStore; only favorite/tag state is stored here.
 */
@Entity(tableName = "media_overlays")
data class MediaOverlayEntity(
    @PrimaryKey val uri: String,
    val favorite: Boolean = false,
    val tags: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)
