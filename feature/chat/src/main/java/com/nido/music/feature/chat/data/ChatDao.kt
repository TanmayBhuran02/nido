package com.nido.music.feature.chat.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM conversations ORDER BY lastMessageTimestamp DESC")
    fun getConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversation(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET title = :newTitle WHERE id = :convId")
    suspend fun updateConversationTitle(convId: String, newTitle: String)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE id = :convId")
    suspend fun markConversationRead(convId: String)

    @Query("SELECT COALESCE(SUM(unreadCount), 0) FROM conversations")
    fun getTotalUnreadCount(): Flow<Int>

    @Query("UPDATE messages SET status = 'READ' WHERE conversationId = :convId AND status != 'READ' AND senderId != 'me'")
    suspend fun markMessagesAsRead(convId: String)

    @Query("SELECT id FROM messages WHERE conversationId = :convId AND status != 'READ' AND senderId != 'me'")
    suspend fun getUnreadIncomingMessageIds(convId: String): List<String>

    @Query("SELECT * FROM messages WHERE conversationId = :convId ORDER BY timestamp ASC")
    fun getMessages(convId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :msgId")
    suspend fun updateMessageStatus(msgId: String, status: MessageStatus)

    @Query("UPDATE messages SET content = :newContent, isEdited = 1 WHERE id = :msgId")
    suspend fun editMessage(msgId: String, newContent: String)

    @Query("UPDATE messages SET isStarred = :isStarred WHERE id = :msgId")
    suspend fun updateStarred(msgId: String, isStarred: Boolean)

    @Query("UPDATE messages SET isDeleted = 1, content = 'This message was deleted' WHERE id = :msgId")
    suspend fun deleteForEveryone(msgId: String)

    @Query("DELETE FROM messages WHERE id = :msgId")
    suspend fun deleteForMe(msgId: String)

    @Query("SELECT * FROM messages WHERE conversationId = :convId AND isDeleted = 0 AND content LIKE '%' || :escapedQuery || '%' ESCAPE '\\' ORDER BY timestamp DESC")
    fun searchMessagesInternal(convId: String, escapedQuery: String): Flow<List<MessageEntity>>

    fun searchMessages(convId: String, query: String): Flow<List<MessageEntity>> {
        val escaped = query
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return searchMessagesInternal(convId, escaped)
    }

    @Query("SELECT * FROM messages WHERE conversationId = :convId AND type IN ('IMAGE', 'VIDEO', 'DOC', 'CLIP') AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getMediaGalleryMessages(convId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE isStarred = 1 AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getStarredMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE status IN ('OUTBOX', 'SENDING') ORDER BY timestamp ASC")
    suspend fun getPendingOutboxMessages(): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: AttachmentEntity)

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun getAttachment(id: String): AttachmentEntity?

    @Query("DELETE FROM messages WHERE conversationId = :convId")
    suspend fun deleteMessagesForConversation(convId: String)

    @Query("DELETE FROM conversations WHERE id = :convId")
    suspend fun deleteConversationOnly(convId: String)

    @Transaction
    suspend fun deleteConversation(convId: String) {
        deleteMessagesForConversation(convId)
        deleteConversationOnly(convId)
    }

    @Query("SELECT * FROM contacts WHERE id = :id")
    suspend fun getContact(id: String): ContactEntity?

    @Query("SELECT * FROM contacts ORDER BY displayName ASC")
    fun getContacts(): Flow<List<ContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContact(contact: ContactEntity)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteContact(id: String)
}
