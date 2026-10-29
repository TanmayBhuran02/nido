package com.nido.music.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nido.music.core.database.entity.LibraryFolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryFolderDao {

    @Query("SELECT * FROM library_folders ORDER BY displayName ASC")
    fun getAllFolders(): Flow<List<LibraryFolderEntity>>

    @Query("SELECT * FROM library_folders")
    suspend fun getAllFoldersList(): List<LibraryFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: LibraryFolderEntity)

    @Query("DELETE FROM library_folders WHERE treeUri = :treeUri")
    suspend fun deleteFolder(treeUri: String)

    @Query("UPDATE library_folders SET lastScanTimestamp = :timestamp WHERE treeUri = :treeUri")
    suspend fun updateLastScanTimestamp(treeUri: String, timestamp: Long)
}
