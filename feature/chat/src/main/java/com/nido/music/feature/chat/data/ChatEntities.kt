package com.nido.music.feature.chat.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey val id: String,
    val username: String,
    val displayName: String,
    val identityKey: String,
    val isVerified: Boolean = false,
    val lastSeenAt: Long = 0L
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val peerId: String,
    val title: String,
    val lastMessageSnippet: String,
    val lastMessageTimestamp: Long,
    val unreadCount: Int = 0
)

enum class MessageType {
    TEXT, IMAGE, DOC, CLIP
}

enum class MessageStatus {
    OUTBOX, SENDING, SENT, DELIVERED, READ, FAILED
}

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val type: MessageType,
    val content: String,
    val attachmentId: String? = null,
    val status: MessageStatus = MessageStatus.OUTBOX,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "attachments")
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val localUri: String?,
    val remoteUrl: String?,
    val keyHex: String,
    val ivHex: String,
    val sha256: String,
    val mimeType: String,
    val sizeBytes: Long
)

@Serializable
data class ClipPayload(
    val title: String,
    val artist: String,
    val startOffsetMs: Long,
    val endOffsetMs: Long,
    val originalDurationMs: Long,
    val remoteUrl: String,
    val keyHex: String,
    val ivHex: String,
    val sha256: String
)
