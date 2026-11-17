package com.nido.music.feature.chat.data

import android.content.Context
import com.nido.music.core.crypto.SignalProtocolManager
import com.nido.music.core.network.relay.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val chatDao: ChatDao,
    private val signalProtocolManager: SignalProtocolManager,
    private val relayApi: ChatRelayApi,
    private val authManager: ChatAuthManager
) {
    private val repoScope = CoroutineScope(Dispatchers.IO)
    private val deletedContactsPrefs = context.getSharedPreferences("nido_deleted_contacts", Context.MODE_PRIVATE)
    private val _deletedContactIdsFlow = MutableStateFlow<Set<String>>(loadDeletedContactIds())
    val deletedContactIdsFlow: StateFlow<Set<String>> = _deletedContactIdsFlow.asStateFlow()

    @Volatile
    var activeConversationId: String? = null

    private fun loadDeletedContactIds(): Set<String> {
        return deletedContactsPrefs.getStringSet("deleted_ids", emptySet()) ?: emptySet()
    }

    init {
        repoScope.launch {
            removeDummyChats()
            authManager.autoRegisterIfGuest()
            startPeriodicSync()
        }
    }

    private suspend fun removeDummyChats() {
        chatDao.deleteConversation("conv_bob")
        chatDao.deleteConversation("conv_alice")
        chatDao.deleteConversation("conv_support")
    }

    fun getConversations(): Flow<List<ConversationEntity>> = chatDao.getConversations()

    suspend fun getConversation(id: String): ConversationEntity? = chatDao.getConversation(id)

    fun getMessages(conversationId: String): Flow<List<MessageEntity>> = chatDao.getMessages(conversationId)

    suspend fun createOrGetConversation(peerId: String, title: String, isGroup: Boolean = false): String {
        val existing = chatDao.getConversations().first().firstOrNull { it.peerId == peerId }
        if (existing != null) {
            return existing.id
        }
        val id = "conv_${UUID.randomUUID().toString().take(8)}"
        val conv = ConversationEntity(
            id = id,
            peerId = peerId,
            title = title,
            lastMessageSnippet = "Chat started",
            lastMessageTimestamp = System.currentTimeMillis(),
            unreadCount = 0,
            isGroup = isGroup
        )
        chatDao.upsertConversation(conv)
        return id
    }

    suspend fun sendMessage(
        conversationId: String,
        text: String,
        type: MessageType = MessageType.TEXT,
        replyToId: String? = null,
        replyToSnippet: String? = null
    ): MessageEntity {
        val conversation = chatDao.getConversation(conversationId)
        val peerId = conversation?.peerId ?: "peer_unknown"

        // Encrypt with Signal Protocol
        val encryptedEnvelope = signalProtocolManager.encrypt(peerId, text.toByteArray(Charsets.UTF_8))
        val decryptedPlaintext = signalProtocolManager.decrypt(peerId, encryptedEnvelope.ciphertext)
        val verifiedText = String(decryptedPlaintext, Charsets.UTF_8)

        val msgId = "msg_${UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        val message = MessageEntity(
            id = msgId,
            conversationId = conversationId,
            senderId = "me",
            type = type,
            content = verifiedText,
            status = MessageStatus.OUTBOX,
            replyToId = replyToId,
            replyToSnippet = replyToSnippet,
            timestamp = now
        )

        chatDao.insertMessage(message)

        val snippet = when (type) {
            MessageType.TEXT -> text.take(50)
            MessageType.AUDIO -> "[Voice Note]"
            MessageType.CLIP -> "[Music Clip]"
            MessageType.LOCATION -> "[Location]"
            MessageType.STICKER -> "[Sticker]"
            MessageType.IMAGE -> "[Image]"
            MessageType.VIDEO -> "[Video]"
            MessageType.DOC -> "[Document]"
        }

        conversation?.let {
            chatDao.upsertConversation(
                it.copy(
                    lastMessageSnippet = snippet,
                    lastMessageTimestamp = now
                )
            )
        }

        // Relay to backend if it's a real user
        val isMock = peerId == "peer_bob" || peerId == "peer_alice" || peerId == "peer_support"
        if (isMock) {
            repoScope.launch {
                chatDao.updateMessageStatus(msgId, MessageStatus.SENT)
                delay(700)
                chatDao.updateMessageStatus(msgId, MessageStatus.DELIVERED)
                delay(1200)
                chatDao.updateMessageStatus(msgId, MessageStatus.READ)
            }
        } else {
            repoScope.launch {
                try {
                    val auth = authManager.autoRegisterIfGuest()
                    if (auth.isLoggedIn && !auth.accessToken.isNullOrEmpty()) {
                        var targetUserId = peerId.removePrefix("peer_")
                        if (!targetUserId.contains("-") && targetUserId.length < 32) {
                            try {
                                val userContact = relayApi.getUserByUsername(targetUserId)
                                targetUserId = userContact.user_id
                            } catch (e: Exception) {
                                // proceed with targetUserId as given
                            }
                        }

                        relayApi.sendMessage(
                            auth = "Bearer ${auth.accessToken}",
                            req = SendMessageRequest(
                                recipient_user_id = targetUserId,
                                recipient_device_id = 1,
                                ciphertext = encryptedEnvelope.ciphertext,
                                message_type = when (type) {
                                    MessageType.TEXT -> 1
                                    MessageType.CLIP -> 2
                                    MessageType.AUDIO -> 3
                                    MessageType.LOCATION -> 4
                                    MessageType.STICKER -> 5
                                    MessageType.IMAGE -> 6
                                    MessageType.VIDEO -> 7
                                    MessageType.DOC -> 8
                                },
                                reply_to_id = replyToId,
                                client_msg_id = msgId
                            )
                        )
                        chatDao.updateMessageStatus(msgId, MessageStatus.SENT)
                    }
                } catch (e: Exception) {
                    // Handled gracefully; local message saved in OUTBOX for retry
                }
            }
        }

        return message
    }

    private fun startPeriodicSync() {
        repoScope.launch {
            while (true) {
                try {
                    flushOutbox()
                    syncPendingMessages()
                    syncReceipts()
                } catch (e: Exception) {
                    // Ignore transient errors
                }
                delay(2000)
            }
        }
    }

    suspend fun flushOutbox() {
        val pending = chatDao.getPendingOutboxMessages()
        if (pending.isEmpty()) return

        val auth = authManager.authState.value
        if (!auth.isLoggedIn || auth.accessToken.isNullOrEmpty()) return

        for (msg in pending) {
            val conv = chatDao.getConversation(msg.conversationId) ?: continue
            val peerId = conv.peerId
            val isMock = peerId == "peer_bob" || peerId == "peer_alice" || peerId == "peer_support"
            if (isMock) {
                chatDao.updateMessageStatus(msg.id, MessageStatus.SENT)
                continue
            }

            var targetUserId = peerId.removePrefix("peer_")
            if (!targetUserId.contains("-") && targetUserId.length < 32) {
                try {
                    val userContact = relayApi.getUserByUsername(targetUserId)
                    targetUserId = userContact.user_id
                } catch (e: Exception) {
                    // proceed with targetUserId as given
                }
            }

            try {
                val encryptedEnvelope = signalProtocolManager.encrypt(peerId, msg.content.toByteArray(Charsets.UTF_8))
                relayApi.sendMessage(
                    auth = "Bearer ${auth.accessToken}",
                    req = SendMessageRequest(
                        recipient_user_id = targetUserId,
                        recipient_device_id = 1,
                        ciphertext = encryptedEnvelope.ciphertext,
                        message_type = when (msg.type) {
                            MessageType.TEXT -> 1
                            MessageType.CLIP -> 2
                            MessageType.AUDIO -> 3
                            MessageType.LOCATION -> 4
                            MessageType.STICKER -> 5
                            MessageType.IMAGE -> 6
                            MessageType.VIDEO -> 7
                            MessageType.DOC -> 8
                        },
                        reply_to_id = msg.replyToId,
                        client_msg_id = msg.id
                    )
                )
                chatDao.updateMessageStatus(msg.id, MessageStatus.SENT)
            } catch (e: Exception) {
                break // Server down, pause until next sync cycle
            }
        }
    }

    suspend fun syncPendingMessages() {
        val auth = authManager.authState.value
        if (!auth.isLoggedIn || auth.accessToken.isNullOrEmpty()) return

        val pending = try {
            relayApi.getPendingMessages("Bearer ${auth.accessToken}")
        } catch (e: Exception) {
            emptyList()
        }

        for (item in pending) {
            try {
                val alternateKeys = mutableListOf<String>()
                item.sender_username?.let {
                    alternateKeys.add(it)
                    alternateKeys.add("peer_$it")
                }
                auth.username?.let {
                    alternateKeys.add(it)
                    alternateKeys.add("peer_$it")
                }

                val decryptedBytes = try {
                    signalProtocolManager.decrypt(
                        senderUserId = item.sender_user_id,
                        ciphertextB64 = item.ciphertext,
                        alternatePeerIds = alternateKeys
                    )
                } catch (e: Exception) {
                    "[Encrypted message]".toByteArray(Charsets.UTF_8)
                }
                val plaintext = String(decryptedBytes, Charsets.UTF_8)

                val senderName = item.sender_username ?: item.sender_user_id
                val peerId = "peer_$senderName"
                val convTitle = if (item.sender_username != null) "@${item.sender_username}" else "User ${item.sender_user_id.take(6)}"
                val convId = createOrGetConversation(peerId, convTitle)

                val detectedType = when (item.message_type) {
                    2 -> MessageType.CLIP
                    3 -> MessageType.AUDIO
                    4 -> MessageType.LOCATION
                    5 -> MessageType.STICKER
                    6 -> MessageType.IMAGE
                    7 -> MessageType.VIDEO
                    8 -> MessageType.DOC
                    else -> {
                        if (plaintext.contains("\"packId\"") && plaintext.contains("\"stickerId\"")) {
                            MessageType.STICKER
                        } else if (plaintext.contains("\"latitude\"") && plaintext.contains("\"longitude\"")) {
                            MessageType.LOCATION
                        } else if (plaintext.contains("\"startOffsetMs\"") && plaintext.contains("\"endOffsetMs\"")) {
                            MessageType.CLIP
                        } else {
                            MessageType.TEXT
                        }
                    }
                }

                val isOpen = (convId == activeConversationId)
                val initialStatus = if (isOpen) MessageStatus.READ else MessageStatus.DELIVERED

                val msg = MessageEntity(
                    id = item.id,
                    conversationId = convId,
                    senderId = peerId,
                    type = detectedType,
                    content = plaintext,
                    status = initialStatus,
                    replyToId = item.reply_to_id,
                    timestamp = System.currentTimeMillis()
                )
                chatDao.insertMessage(msg)

                val snippet = when (detectedType) {
                    MessageType.TEXT -> plaintext.take(50)
                    MessageType.AUDIO -> "[Voice Note]"
                    MessageType.CLIP -> "[Music Clip]"
                    MessageType.LOCATION -> "[Location]"
                    MessageType.STICKER -> "[Sticker]"
                    MessageType.IMAGE -> "[Image]"
                    MessageType.VIDEO -> "[Video]"
                    MessageType.DOC -> "[Document]"
                }

                val conv = chatDao.getConversation(convId)
                conv?.let {
                    chatDao.upsertConversation(
                        it.copy(
                            lastMessageSnippet = snippet,
                            lastMessageTimestamp = System.currentTimeMillis(),
                            unreadCount = if (isOpen) it.unreadCount else it.unreadCount + 1
                        )
                    )
                }

                if (isOpen) {
                    try {
                        relayApi.sendReadReceipts(
                            auth = "Bearer ${auth.accessToken}",
                            req = MarkReadRequest(message_ids = listOf(item.id), peer_user_id = item.sender_user_id)
                        )
                    } catch (_: Exception) {}
                }

                relayApi.ackMessage("Bearer ${auth.accessToken}", item.id)
            } catch (e: Exception) {
                // Ignore transient errors
            }
        }
    }

    suspend fun simulatePeerResponse(
        conversationId: String,
        replyText: String,
        onTypingChange: (Boolean) -> Unit
    ) {
        val conversation = chatDao.getConversation(conversationId) ?: return
        val peerId = conversation.peerId

        // Typing indicator ON
        onTypingChange(true)
        delay(1200)
        onTypingChange(false)

        // Encrypt and decrypt through Signal Protocol
        val envelope = signalProtocolManager.encrypt("me", replyText.toByteArray(Charsets.UTF_8))
        val decrypted = String(signalProtocolManager.decrypt("me", envelope.ciphertext), Charsets.UTF_8)

        val peerMsgId = "msg_peer_${UUID.randomUUID()}"
        val now = System.currentTimeMillis()
        val peerMsg = MessageEntity(
            id = peerMsgId,
            conversationId = conversationId,
            senderId = peerId,
            type = MessageType.TEXT,
            content = decrypted,
            status = MessageStatus.READ,
            timestamp = now
        )

        chatDao.insertMessage(peerMsg)
        chatDao.upsertConversation(
            conversation.copy(
                lastMessageSnippet = decrypted.take(50),
                lastMessageTimestamp = now
            )
        )
    }

    suspend fun renameConversation(conversationId: String, newTitle: String) {
        chatDao.updateConversationTitle(conversationId, newTitle)
    }

    suspend fun editMessage(messageId: String, newContent: String) {
        chatDao.editMessage(messageId, newContent)
    }

    suspend fun deleteMessage(messageId: String, forEveryone: Boolean) {
        if (forEveryone) {
            chatDao.deleteForEveryone(messageId)
        } else {
            chatDao.deleteForMe(messageId)
        }
    }

    suspend fun updateStarred(messageId: String, isStarred: Boolean) {
        chatDao.updateStarred(messageId, isStarred)
    }

    fun searchMessages(conversationId: String, query: String): Flow<List<MessageEntity>> {
        return chatDao.searchMessages(conversationId, query)
    }

    fun getMediaGallery(conversationId: String): Flow<List<MessageEntity>> {
        return chatDao.getMediaGalleryMessages(conversationId)
    }

    suspend fun deleteConversation(conversationId: String) {
        chatDao.deleteConversation(conversationId)
    }

    suspend fun clearMessages(conversationId: String) {
        chatDao.deleteMessagesForConversation(conversationId)
    }

    fun getContacts(): Flow<List<ContactEntity>> {
        return chatDao.getContacts()
    }

    suspend fun upsertContact(contact: ContactEntity) {
        chatDao.upsertContact(contact)
    }

    suspend fun markContactDeleted(contactId: String, phoneNumber: String? = null) {
        val current = _deletedContactIdsFlow.value.toMutableSet()
        current.add(contactId)
        if (!phoneNumber.isNullOrBlank()) {
            current.add(phoneNumber.replace("[^0-9+]".toRegex(), ""))
        }
        deletedContactsPrefs.edit().putStringSet("deleted_ids", current).apply()
        _deletedContactIdsFlow.value = current
        chatDao.deleteContact(contactId)
    }

    suspend fun deleteContact(contactId: String) {
        markContactDeleted(contactId, null)
    }

    fun getTotalUnreadCount(): Flow<Int> = chatDao.getTotalUnreadCount()

    suspend fun markConversationAsRead(convId: String) {
        val unreadIds = chatDao.getUnreadIncomingMessageIds(convId)
        chatDao.markConversationRead(convId)
        chatDao.markMessagesAsRead(convId)
        if (unreadIds.isNotEmpty()) {
            val auth = authManager.authState.value
            if (auth.isLoggedIn && !auth.accessToken.isNullOrEmpty()) {
                val conv = chatDao.getConversation(convId)
                val peerUserId = conv?.peerId?.removePrefix("peer_")
                try {
                    relayApi.sendReadReceipts(
                        auth = "Bearer ${auth.accessToken}",
                        req = MarkReadRequest(message_ids = unreadIds, peer_user_id = peerUserId)
                    )
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun syncReceipts() {
        val auth = authManager.authState.value
        if (!auth.isLoggedIn || auth.accessToken.isNullOrEmpty()) return
        try {
            val receipts = relayApi.getPendingReceipts("Bearer ${auth.accessToken}")
            if (receipts.isNotEmpty()) {
                val ackIds = mutableListOf<String>()
                for (r in receipts) {
                    val status = when (r.status.uppercase()) {
                        "READ" -> MessageStatus.READ
                        "DELIVERED" -> MessageStatus.DELIVERED
                        else -> null
                    }
                    if (status != null) {
                        chatDao.updateMessageStatus(r.message_id, status)
                    }
                    ackIds.add(r.message_id)
                }
                relayApi.ackReceipts("Bearer ${auth.accessToken}", AckReceiptsRequest(ackIds))
            }
        } catch (_: Exception) {}
    }

    suspend fun uploadClipFile(file: java.io.File): String {
        return try {
            val mediaType = "audio/mp4".toMediaType()
            val reqBody = file.asRequestBody(mediaType)
            val part = MultipartBody.Part.createFormData("file", file.name, reqBody)
            val res = relayApi.uploadClip(part)
            "http://192.168.0.109:8000${res.url}"
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
}
