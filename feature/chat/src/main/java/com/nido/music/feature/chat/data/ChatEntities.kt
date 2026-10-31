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
    val lastSeenAt: Long = 0L,
    val avatarUrl: String? = null
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val peerId: String,
    val title: String,
    val lastMessageSnippet: String,
    val lastMessageTimestamp: Long,
    val unreadCount: Int = 0,
    val isGroup: Boolean = false,
    val avatarUrl: String? = null
)

enum class MessageType {
    TEXT, IMAGE, VIDEO, AUDIO, LOCATION, STICKER, DOC, CLIP
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
    val replyToId: String? = null,
    val replyToSnippet: String? = null,
    val isDeleted: Boolean = false,
    val isEdited: Boolean = false,
    val isStarred: Boolean = false,
    val isForwarded: Boolean = false,
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

@Serializable
data class LocationPayload(
    val latitude: Double,
    val longitude: Double,
    val name: String? = null,
    val isLive: Boolean = false,
    val durationSeconds: Long = 0L,
    val expiresAt: Long = 0L
)

@Serializable
data class VoiceNotePayload(
    val durationMs: Long,
    val waveform: List<Float> = emptyList(),
    val localUri: String? = null,
    val remoteUrl: String? = null
)

@Serializable
data class VideoPayload(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val thumbnailB64: String? = null,
    val localUri: String? = null,
    val remoteUrl: String? = null
)

@Serializable
data class StickerPayload(
    val packId: String,
    val stickerId: String,
    val emojis: List<String> = emptyList(),
    val webpLocalUri: String? = null,
    val webpRemoteUrl: String? = null
)

@Entity(tableName = "sticker_packs")
data class StickerPackEntity(
    @PrimaryKey val id: String,
    val name: String,
    val publisher: String,
    val trayImageUri: String? = null,
    val animated: Boolean = false,
    val isFavorite: Boolean = false
)

@Entity(tableName = "stickers")
data class StickerEntity(
    @PrimaryKey val id: String,
    val packId: String,
    val imageUri: String,
    val emojis: String = "", // Comma-separated
    val isFavorite: Boolean = false,
    val lastUsedTimestamp: Long = 0L
)
