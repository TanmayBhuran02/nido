package com.nido.music.feature.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nido.music.feature.chat.data.ChatAuthManager
import com.nido.music.feature.chat.data.ChatRepository
import com.nido.music.feature.chat.data.ClipPayload
import com.nido.music.feature.chat.data.ConversationEntity
import com.nido.music.feature.chat.data.MessageType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val repository: ChatRepository,
    private val authManager: ChatAuthManager
) : ViewModel() {

    val authState: StateFlow<ChatAuthManager.AuthState> = authManager.authState

    val conversations: StateFlow<List<ConversationEntity>> = repository.getConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalUnreadCount: StateFlow<Int> = repository.getTotalUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val deletedContactIds: StateFlow<Set<String>> = repository.deletedContactIdsFlow

    init {
        viewModelScope.launch {
            if (authManager.authState.value.isLoggedIn) {
                authManager.fetchProfile()
            }
        }
    }

    fun updateProfile(
        phone: String,
        email: String? = null,
        username: String? = null,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val result = authManager.updateProfile(phone = phone, email = email, username = username)
            if (result.isSuccess) {
                onComplete(true, null)
            } else {
                onComplete(false, result.exceptionOrNull()?.message)
            }
        }
    }

    fun markConversationAsRead(convId: String) {
        viewModelScope.launch {
            repository.markConversationAsRead(convId)
        }
    }

    fun setUsername(newUsername: String, inviteCode: String? = null, onComplete: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = authManager.registerUser(newUsername, inviteCode = inviteCode?.takeIf { it.isNotBlank() })
            if (result.isSuccess) {
                onComplete(true, null)
            } else {
                onComplete(false, result.exceptionOrNull()?.message)
            }
        }
    }

    fun registerAccount(
        username: String,
        password: String,
        email: String?,
        phone: String,
        inviteCode: String? = null,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val result = authManager.registerUser(
                desiredUsername = username,
                password = password,
                email = email,
                phone = phone,
                inviteCode = inviteCode?.takeIf { it.isNotBlank() }
            )
            if (result.isSuccess) {
                onComplete(true, null)
            } else {
                onComplete(false, result.exceptionOrNull()?.message)
            }
        }
    }

    fun loginAccount(
        username: String,
        password: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        viewModelScope.launch {
            val result = authManager.loginUser(
                desiredUsername = username,
                password = password
            )
            if (result.isSuccess) {
                onComplete(true, null)
            } else {
                onComplete(false, result.exceptionOrNull()?.message)
            }
        }
    }

    fun logout() {
        authManager.logout()
    }

    fun hasCompletedOnboarding(): Boolean = authManager.hasCompletedOnboarding()

    fun skipOnboarding() {
        authManager.setOnboardingCompleted()
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            repository.deleteConversation(conversationId)
        }
    }

    fun deleteContact(contactId: String, phoneNumber: String? = null) {
        viewModelScope.launch {
            repository.markContactDeleted(contactId, phoneNumber)
        }
    }

    fun createConversation(peerId: String, title: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val id = repository.createOrGetConversation(peerId, title, isGroup = false)
            onCreated(id)
        }
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameConversation(conversationId, newTitle)
        }
    }

    fun createGroup(name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val peerId = "group_${name.lowercase().replace(" ", "_")}"
            val id = repository.createOrGetConversation(peerId, name, isGroup = true)
            onCreated(id)
        }
    }

    fun shareClipToPeer(
        peerId: String,
        peerTitle: String,
        clipTitle: String,
        artist: String,
        startMs: Long,
        endMs: Long,
        filePath: String? = null,
        onComplete: (String) -> Unit
    ) {
        viewModelScope.launch {
            var remoteUrl = ""
            if (!filePath.isNullOrBlank()) {
                val file = java.io.File(filePath)
                if (file.exists()) {
                    remoteUrl = repository.uploadClipFile(file)
                }
            }
            val convId = repository.createOrGetConversation(peerId, peerTitle, isGroup = false)
            val clipPayload = ClipPayload(
                title = clipTitle,
                artist = artist,
                startOffsetMs = startMs,
                endOffsetMs = endMs,
                originalDurationMs = endMs - startMs,
                remoteUrl = remoteUrl,
                keyHex = "",
                ivHex = "",
                sha256 = "",
                localUri = filePath
            )
            val jsonText = Json.encodeToString(clipPayload)
            repository.sendMessage(
                conversationId = convId,
                text = jsonText,
                type = MessageType.CLIP
            )
            onComplete(convId)
        }
    }
}
