package com.nido.music.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nido.music.feature.chat.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class ChatDetailViewModel @Inject constructor(
    private val repository: ChatRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val conversationId: String = savedStateHandle["conversationId"] ?: "conv_bob"

    private val _conversation = MutableStateFlow<ConversationEntity?>(null)
    val conversation: StateFlow<ConversationEntity?> = _conversation.asStateFlow()

    val messages: StateFlow<List<MessageEntity>> = repository.getMessages(conversationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isPeerTyping = MutableStateFlow(false)
    val isPeerTyping: StateFlow<Boolean> = _isPeerTyping.asStateFlow()

    init {
        loadConversation()
    }

    private fun loadConversation() {
        viewModelScope.launch {
            _conversation.value = repository.getConversation(conversationId)
        }
    }

    fun sendMessage(
        text: String,
        replyToId: String? = null,
        replyToSnippet: String? = null
    ) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.sendMessage(
                conversationId = conversationId,
                text = text,
                type = MessageType.TEXT,
                replyToId = replyToId,
                replyToSnippet = replyToSnippet
            )
            triggerPeerAutoReply(text)
        }
    }

    fun sendVoiceNote(durationMs: Long) {
        viewModelScope.launch {
            val payload = Json.encodeToString(VoiceNotePayload(durationMs = durationMs))
            repository.sendMessage(
                conversationId = conversationId,
                text = payload,
                type = MessageType.AUDIO
            )
            triggerPeerAutoReply("audio")
        }
    }

    fun sendClip(clip: ClipPayload) {
        viewModelScope.launch {
            val payload = Json.encodeToString(clip)
            repository.sendMessage(
                conversationId = conversationId,
                text = payload,
                type = MessageType.CLIP
            )
            triggerPeerAutoReply("clip: ${clip.title}")
        }
    }

    fun sendLocation(lat: Double, lng: Double, name: String) {
        viewModelScope.launch {
            val payload = Json.encodeToString(LocationPayload(latitude = lat, longitude = lng, name = name))
            repository.sendMessage(
                conversationId = conversationId,
                text = payload,
                type = MessageType.LOCATION
            )
            triggerPeerAutoReply("location")
        }
    }

    fun sendSticker(stickerName: String) {
        viewModelScope.launch {
            repository.sendMessage(
                conversationId = conversationId,
                text = stickerName,
                type = MessageType.STICKER
            )
            triggerPeerAutoReply("sticker")
        }
    }

    fun editMessage(messageId: String, newContent: String) {
        viewModelScope.launch {
            repository.editMessage(messageId, newContent)
        }
    }

    fun deleteMessage(messageId: String, forEveryone: Boolean) {
        viewModelScope.launch {
            repository.deleteMessage(messageId, forEveryone)
        }
    }

    fun toggleStarred(messageId: String, currentStarred: Boolean) {
        viewModelScope.launch {
            repository.updateStarred(messageId, !currentStarred)
        }
    }

    private fun triggerPeerAutoReply(trigger: String) {
        viewModelScope.launch {
            val conv = _conversation.value ?: repository.getConversation(conversationId)
            val peerTitle = conv?.title?.lowercase() ?: ""

            val reply = when {
                peerTitle.contains("bob") -> {
                    when {
                        trigger.contains("clip") -> "Man, that clip has great vibes! Added to my queue 🎧"
                        trigger == "audio" -> "Crisp voice note! Sounds great on my headphones."
                        trigger == "location" -> "See you there! Bringing the sound system."
                        else -> listOf(
                            "One good thing about music, when it hits you feel no pain 🎶",
                            "Loving this conversation! End-to-end encrypted and crystal clear.",
                            "Yeah man! What track are we spinning next?",
                            "That bassline is unforgettable."
                        ).random()
                    }
                }
                peerTitle.contains("alice") -> {
                    when {
                        trigger.contains("clip") -> "Oh wicked! I love that guitar solo 🎸"
                        trigger == "audio" -> "Got your voice memo, listening right now!"
                        else -> listOf(
                            "Rock on! Let's build a shared playlist.",
                            "Sounds awesome, let's catch up later today!",
                            "Awesome! Have you heard their newest release?"
                        ).random()
                    }
                }
                peerTitle.contains("support") -> {
                    "Nido Support: Your Signal Protocol Double Ratchet session is securely verified."
                }
                else -> {
                    listOf(
                        "Got your encrypted message! 🎵",
                        "Nice! Playing it now in Nido.",
                        "Thanks for sharing! Chatting over E2EE is super smooth."
                    ).random()
                }
            }

            repository.simulatePeerResponse(
                conversationId = conversationId,
                replyText = reply,
                onTypingChange = { _isPeerTyping.value = it }
            )
            loadConversation()
        }
    }
}
