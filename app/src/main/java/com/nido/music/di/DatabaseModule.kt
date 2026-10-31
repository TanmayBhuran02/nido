package com.nido.music.di

import android.content.Context
import androidx.room.Room
import com.nido.music.core.database.NidoDatabase
import com.nido.music.core.database.dao.LibraryFolderDao
import com.nido.music.core.database.dao.PlaylistDao
import com.nido.music.core.database.dao.QueueDao
import com.nido.music.core.database.dao.SongDao
import com.nido.music.core.database.security.DatabaseKeyManager
import com.nido.music.core.database.security.DatabaseMigrationHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabaseKeyManager(@ApplicationContext context: Context): DatabaseKeyManager {
        return DatabaseKeyManager(context)
    }

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keyManager: DatabaseKeyManager
    ): NidoDatabase {
        val passphrase = keyManager.getOrCreatePassphrase()
        val factory = DatabaseMigrationHelper.migrateToEncryptedIfNecessary(
            context = context,
            plainDbName = NidoDatabase.LEGACY_DATABASE_NAME,
            encryptedDbName = NidoDatabase.DATABASE_NAME,
            passphrase = passphrase
        )

        return Room.databaseBuilder(
            context,
            NidoDatabase::class.java,
            NidoDatabase.DATABASE_NAME
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideSongDao(db: NidoDatabase): SongDao = db.songDao()

    @Provides
    fun providePlaylistDao(db: NidoDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideQueueDao(db: NidoDatabase): QueueDao = db.queueDao()

    @Provides
    fun provideLibraryFolderDao(db: NidoDatabase): LibraryFolderDao = db.libraryFolderDao()
}
