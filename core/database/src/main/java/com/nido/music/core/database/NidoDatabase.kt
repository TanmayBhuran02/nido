package com.nido.music.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.nido.music.core.database.dao.LibraryFolderDao
import com.nido.music.core.database.dao.PlaylistDao
import com.nido.music.core.database.dao.QueueDao
import com.nido.music.core.database.dao.SongDao
import com.nido.music.core.database.entity.LibraryFolderEntity
import com.nido.music.core.database.entity.PlaylistEntity
import com.nido.music.core.database.entity.PlaylistSongEntity
import com.nido.music.core.database.entity.QueueItemEntity
import com.nido.music.core.database.entity.SongEntity

/**
 * Main Room database for Nido.
 *
 * Stage 2 note: This uses plain Room. When E2EE chat is added,
 * the SupportSQLiteOpenHelper will be swapped to SQLCipher's implementation
 * with a key wrapped by Android Keystore. Existing data will be migrated
 * via an encrypted-copy migration.
 */
@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        QueueItemEntity::class,
        LibraryFolderEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NidoDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun queueDao(): QueueDao
    abstract fun libraryFolderDao(): LibraryFolderDao

    companion object {
        const val DATABASE_NAME = "nido_music.db"
    }
}
