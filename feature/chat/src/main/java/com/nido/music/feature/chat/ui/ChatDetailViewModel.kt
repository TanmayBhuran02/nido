package com.nido.music.feature.chat.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nido.music.feature.chat.data.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject

@HiltViewModel
class ChatDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
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

    private var mediaPlayer: MediaPlayer? = null
    private var progressJob: Job? = null

    private val _playingClipId = MutableStateFlow<String?>(null)
    val playingClipId: StateFlow<String?> = _playingClipId.asStateFlow()

    private val _clipPlaybackProgress = MutableStateFlow(0f)
    val clipPlaybackProgress: StateFlow<Float> = _clipPlaybackProgress.asStateFlow()

    fun togglePlayClip(messageId: String, clip: ClipPayload) {
        if (_playingClipId.value == messageId) {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                    progressJob?.cancel()
                    _playingClipId.value = null
                    return
                }
            }
        }

        stopAudioPlayback()

        viewModelScope.launch(Dispatchers.IO) {
            var localFileToPlay: File? = null

            // 1. If localUri exists on this device (e.g. sender)
            if (!clip.localUri.isNullOrBlank()) {
                val candidate = File(clip.localUri)
                if (candidate.exists() && candidate.length() > 0) {
                    localFileToPlay = candidate
                }
            }

            // 2. If receiver or localUri not on this phone, fetch remoteUrl
            if (localFileToPlay == null && clip.remoteUrl.isNotBlank()) {
                val resolvedUrl = if (clip.remoteUrl.startsWith("http")) {
                    clip.remoteUrl
                } else {
                    "http://192.168.0.109:8000${clip.remoteUrl}"
                }

                val clipId = clip.remoteUrl.substringAfterLast("/")
                val cacheDir = File(context.cacheDir, "shared_clips").apply { mkdirs() }
                val targetFile = File(cacheDir, clipId)

                if (targetFile.exists() && targetFile.length() > 0) {
                    localFileToPlay = targetFile
                } else {
                    try {
                        val client = okhttp3.OkHttpClient.Builder()
                            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                            .build()
                        val request = okhttp3.Request.Builder().url(resolvedUrl).build()
                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            val body = response.body
                            if (body != null) {
                                val tempFile = File(cacheDir, "${clipId}.tmp")
                                tempFile.outputStream().use { out ->
                                    body.byteStream().copyTo(out)
                                }
                                tempFile.renameTo(targetFile)
                                if (targetFile.exists() && targetFile.length() > 0) {
                                    localFileToPlay = targetFile
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            if (localFileToPlay == null || !localFileToPlay.exists()) {
                return@launch
            }

            withContext(Dispatchers.Main) {
                try {
                    val player = MediaPlayer().apply {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                        )
                        setDataSource(localFileToPlay.absolutePath)
                        setOnPreparedListener { mp ->
                            mp.start()
                            _playingClipId.value = messageId
                            startProgressTracker(mp, messageId)
                        }
                        setOnCompletionListener {
                            stopAudioPlayback()
                        }
                        setOnErrorListener { _, _, _ ->
                            stopAudioPlayback()
                            true
                        }
                        prepareAsync()
                    }
                    mediaPlayer = player
                } catch (e: Exception) {
                    e.printStackTrace()
                    stopAudioPlayback()
                }
            }
        }
    }

    private fun startProgressTracker(player: MediaPlayer, messageId: String) {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            val duration = player.duration.coerceAtLeast(1).toFloat()
            while (player.isPlaying && _playingClipId.value == messageId) {
                val current = player.currentPosition.toFloat()
                _clipPlaybackProgress.value = (current / duration).coerceIn(0f, 1f)
                delay(50)
            }
        }
    }

    fun stopAudioPlayback() {
        progressJob?.cancel()
        progressJob = null
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.reset()
                it.release()
            } catch (e: Exception) {
                // ignore
            }
        }
        mediaPlayer = null
        _playingClipId.value = null
        _clipPlaybackProgress.value = 0f
    }

    fun renameConversation(newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch {
            repository.renameConversation(conversationId, newTitle.trim())
            loadConversation()
        }
    }

    override fun onCleared() {
        stopAudioPlayback()
        super.onCleared()
    }

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
            val peerId = conv?.peerId?.lowercase() ?: ""
            // Only simulate replies for local demo mock bots
            if (peerId != "peer_bob" && peerId != "peer_alice" && peerId != "peer_support") {
                return@launch
            }
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
