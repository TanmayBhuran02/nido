package com.nido.music.feature.chat.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nido.music.feature.chat.data.ChatRepository
import com.nido.music.feature.chat.data.ConversationEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConversationsViewModel @Inject constructor(
    private val repository: ChatRepository
) : ViewModel() {

    val conversations: StateFlow<List<ConversationEntity>> = repository.getConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createConversation(peerId: String, title: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val id = repository.createOrGetConversation(peerId, title, isGroup = false)
            onCreated(id)
        }
    }

    fun createGroup(name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val peerId = "group_${name.lowercase().replace(" ", "_")}"
            val id = repository.createOrGetConversation(peerId, name, isGroup = true)
            onCreated(id)
        }
    }
}
