package com.nido.music.di

import android.content.Context
import androidx.room.Room
import com.nido.music.core.database.NidoDatabase
import com.nido.music.core.database.dao.LibraryFolderDao
import com.nido.music.core.database.dao.PlaylistDao
import com.nido.music.core.database.dao.QueueDao
import com.nido.music.core.database.dao.SongDao
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
    fun provideDatabase(@ApplicationContext context: Context): NidoDatabase {
        return Room.databaseBuilder(
            context,
            NidoDatabase::class.java,
            NidoDatabase.DATABASE_NAME
        ).build()
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
