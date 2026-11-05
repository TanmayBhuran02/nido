package com.nido.music.feature.chat.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ContactEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        AttachmentEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(ChatConverters::class)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao

    companion object {
        const val DATABASE_NAME = "nido_chat_secure.db"
        const val LEGACY_DATABASE_NAME = "nido_chat.db"
    }
}
