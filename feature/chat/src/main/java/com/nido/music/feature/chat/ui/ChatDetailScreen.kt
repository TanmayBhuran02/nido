package com.nido.music.feature.chat.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nido.music.feature.chat.data.ClipPayload
import com.nido.music.feature.chat.data.MessageEntity
import com.nido.music.feature.chat.data.MessageStatus
import com.nido.music.feature.chat.data.MessageType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    conversationId: String,
    onNavigateBack: () -> Unit,
    onNavigateToClipTrimmer: () -> Unit = {}
) {
    var inputText by remember { mutableStateOf("") }
    val mockMessages = remember {
        mutableStateListOf(
            MessageEntity(
                id = "m1",
                conversationId = conversationId,
                senderId = "peer_bob",
                type = MessageType.TEXT,
                content = "Hey! Check out this chorus part:",
                status = MessageStatus.READ
            ),
            MessageEntity(
                id = "m2",
                conversationId = conversationId,
                senderId = "peer_bob",
                type = MessageType.CLIP,
                content = "clip_json",
                status = MessageStatus.READ
            )
        )
    }

    val sampleClip = remember {
        ClipPayload(
            title = "Midnight City",
            artist = "M83",
            startOffsetMs = 30000,
            endOffsetMs = 60000,
            originalDurationMs = 240000,
            remoteUrl = "",
            keyHex = "",
            ivHex = "",
            sha256 = ""
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Bob")
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "E2EE",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onNavigateToClipTrimmer) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach / Share Clip")
                    }
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Signal encrypted message...") },
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                mockMessages.add(
                                    MessageEntity(
                                        id = "m_${System.currentTimeMillis()}",
                                        conversationId = conversationId,
                                        senderId = "me",
                                        type = MessageType.TEXT,
                                        content = inputText,
                                        status = MessageStatus.SENT
                                    )
                                )
                                inputText = ""
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(mockMessages) { msg ->
                val isMe = msg.senderId == "me"
                MessageBubble(
                    message = msg,
                    isMe = isMe,
                    clipPayload = if (msg.type == MessageType.CLIP) sampleClip else null,
                    onPlayClip = { /* inline audio play */ },
                    onFindFullSong = { _, _ -> /* trigger global provider query */ }
                )
            }
        }
    }
}
