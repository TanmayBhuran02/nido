package com.nido.music.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "library_folders")
data class LibraryFolderEntity(
    @PrimaryKey
    val treeUri: String,
    /** Display name of the folder for the UI. */
    val displayName: String = "",
    /** Epoch millis of last completed scan. */
    val lastScanTimestamp: Long = 0
)
