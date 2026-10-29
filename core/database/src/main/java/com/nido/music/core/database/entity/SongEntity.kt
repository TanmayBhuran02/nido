package com.nido.music.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a scanned audio file in the local library.
 * Schema designed to be forward-compatible with Stage 2 SQLCipher migration.
 */
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    /** Content URI or file URI string. */
    val uri: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    /** Parent folder path for folder-tree browsing. */
    val folderPath: String,
    /** Epoch millis when the file was added to the library. */
    val dateAdded: Long,
    /** Whether the user has liked/hearted this song. */
    val liked: Boolean = false,
    /** File's last-modified timestamp for incremental rescan. */
    val lastModified: Long = 0,
    /** Cached cover art file path (app-internal), null if none extracted. */
    val coverArtPath: String? = null
)
