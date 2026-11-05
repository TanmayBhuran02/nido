package com.nido.music.feature.chat.di

import android.content.Context
import androidx.room.Room
import com.nido.music.core.database.security.DatabaseKeyManager
import com.nido.music.core.database.security.DatabaseMigrationHelper
import com.nido.music.feature.chat.data.ChatDao
import com.nido.music.feature.chat.data.ChatDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ChatModule {

    @Provides
    @Singleton
    fun provideChatDatabase(
        @ApplicationContext context: Context,
        keyManager: DatabaseKeyManager
    ): ChatDatabase {
        val passphrase = keyManager.getOrCreatePassphrase()
        val factory = DatabaseMigrationHelper.migrateToEncryptedIfNecessary(
            context = context,
            plainDbName = ChatDatabase.LEGACY_DATABASE_NAME,
            encryptedDbName = ChatDatabase.DATABASE_NAME,
            passphrase = passphrase
        )

        return Room.databaseBuilder(
            context,
            ChatDatabase::class.java,
            ChatDatabase.DATABASE_NAME
        )
            .openHelperFactory(factory)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideChatDao(database: ChatDatabase): ChatDao = database.chatDao()
}
