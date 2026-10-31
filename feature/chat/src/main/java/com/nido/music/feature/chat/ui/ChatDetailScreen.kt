package com.nido.music.feature.chat.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nido.music.feature.chat.data.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private fun getMessageSnippet(msg: MessageEntity): String {
    return when (msg.type) {
        MessageType.TEXT -> msg.content.take(50)
        MessageType.AUDIO -> "[Audio]"
        MessageType.LOCATION -> "[Location]"
        MessageType.STICKER -> "[Sticker]"
        MessageType.IMAGE -> "[Image]"
        MessageType.VIDEO -> "[Video]"
        MessageType.DOC -> "[Document]"
        MessageType.CLIP -> "[Music Clip]"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    conversationId: String,
    onNavigateBack: () -> Unit,
    onNavigateToClipTrimmer: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var inputText by remember { mutableStateOf("") }
    var replyingToMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var messageToEdit by remember { mutableStateOf<MessageEntity?>(null) }
    var messageToForward by remember { mutableStateOf<MessageEntity?>(null) }
    var messageToDelete by remember { mutableStateOf<MessageEntity?>(null) }

    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showLocationDialog by remember { mutableStateOf(false) }
    var showStickersSheet by remember { mutableStateOf(false) }
    var showMediaGallerySheet by remember { mutableStateOf(false) }

    // Audio recording state
    var isRecordingAudio by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableIntStateOf(0) }

    // Typing simulation state
    var isPeerTyping by remember { mutableStateOf(false) }

    val mockMessages = remember {
        mutableStateListOf(
            MessageEntity(
                id = "m1",
                conversationId = conversationId,
                senderId = "peer_bob",
                type = MessageType.TEXT,
                content = "Hey! Check out this chorus part:",
                status = MessageStatus.READ,
                timestamp = System.currentTimeMillis() - 600000
            ),
            MessageEntity(
                id = "m2",
                conversationId = conversationId,
                senderId = "peer_bob",
                type = MessageType.CLIP,
                content = "clip_json",
                status = MessageStatus.READ,
                timestamp = System.currentTimeMillis() - 500000
            ),
            MessageEntity(
                id = "m3",
                conversationId = conversationId,
                senderId = "me",
                type = MessageType.LOCATION,
                content = Json.encodeToString(LocationPayload(37.7749, -122.4194, "San Francisco Concert Hall")),
                status = MessageStatus.READ,
                timestamp = System.currentTimeMillis() - 400000
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

    val displayedMessages = remember(mockMessages.toList(), isSearching, searchQuery) {
        if (isSearching && searchQuery.isNotBlank()) {
            mockMessages.filter { it.content.contains(searchQuery, ignoreCase = true) }
        } else {
            mockMessages
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSearching) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search messages...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Bob", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Signal E2EE",
                                    modifier = Modifier.size(15.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = if (isPeerTyping) "typing..." else "Online",
                                fontSize = 12.sp,
                                color = if (isPeerTyping) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSearching) {
                            isSearching = false
                            searchQuery = ""
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isSearching) {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Search in chat")
                        }
                        IconButton(onClick = { showMediaGallerySheet = true }) {
                            Icon(Icons.Default.PermMedia, contentDescription = "Media Gallery")
                        }
                    } else {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column(modifier = Modifier.navigationBarsPadding()) {
                // Reply Banner
                AnimatedVisibility(visible = replyingToMessage != null) {
                    replyingToMessage?.let { target ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .height(32.dp)
                                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Replying to message", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    Text(target.content.take(60), fontSize = 12.sp, maxLines = 1)
                                }
                                IconButton(onClick = { replyingToMessage = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel reply", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }

                // Audio Recording Banner or Normal Input Bar
                Surface(tonalElevation = 3.dp) {
                    if (isRecordingAudio) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, tint = Color.Red)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Recording audio: ${recordingDurationSec}s", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            TextButton(onClick = { isRecordingAudio = false }) {
                                Text("Cancel")
                            }
                            IconButton(onClick = {
                                isRecordingAudio = false
                                mockMessages.add(
                                    MessageEntity(
                                        id = "m_${System.currentTimeMillis()}",
                                        conversationId = conversationId,
                                        senderId = "me",
                                        type = MessageType.AUDIO,
                                        content = Json.encodeToString(VoiceNotePayload(durationMs = 4500L)),
                                        status = MessageStatus.SENT
                                    )
                                )
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Voice Note", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { showAttachmentSheet = true }) {
                                Icon(Icons.Default.AttachFile, contentDescription = "Attach")
                            }
                            IconButton(onClick = { showStickersSheet = true }) {
                                Icon(Icons.Default.EmojiEmotions, contentDescription = "Stickers")
                            }
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { inputText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Signal encrypted message...") },
                                maxLines = 4,
                                shape = RoundedCornerShape(24.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            if (inputText.isBlank()) {
                                // Hold-to-record voice note
                                IconButton(
                                    onClick = {
                                        isRecordingAudio = true
                                        recordingDurationSec = 3
                                    }
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = "Record Voice Note")
                                }
                            } else {
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
                                                    status = MessageStatus.SENT,
                                                    replyToId = replyingToMessage?.id,
                                                    replyToSnippet = replyingToMessage?.let { getMessageSnippet(it) }
                                                )
                                            )
                                            inputText = ""
                                            replyingToMessage = null
                                        }
                                    }
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
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
            items(displayedMessages, key = { it.id }) { msg ->
                val isMe = msg.senderId == "me"
                MessageBubble(
                    message = msg,
                    isMe = isMe,
                    clipPayload = if (msg.type == MessageType.CLIP) sampleClip else null,
                    onPlayClip = { /* inline audio play */ },
                    onFindFullSong = { _, _ -> /* find full song */ },
                    onReply = { replyingToMessage = it },
                    onForward = { messageToForward = it },
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(it.content))
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    onStar = { target ->
                        val idx = mockMessages.indexOfFirst { it.id == target.id }
                        if (idx != -1) {
                            mockMessages[idx] = target.copy(isStarred = !target.isStarred)
                        }
                    },
                    onEdit = { messageToEdit = it },
                    onDelete = { messageToDelete = it },
                    onAddStickerToMyPack = {
                        Toast.makeText(context, "Added to My Stickers", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    // Attachment Bottom Sheet
    if (showAttachmentSheet) {
        ModalBottomSheet(onDismissRequest = { showAttachmentSheet = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Share Attachment", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    AttachmentOption(icon = Icons.Default.CameraAlt, label = "Camera") {
                        showAttachmentSheet = false
                        mockMessages.add(
                            MessageEntity(
                                id = "m_${System.currentTimeMillis()}",
                                conversationId = conversationId,
                                senderId = "me",
                                type = MessageType.IMAGE,
                                content = "camera_capture.jpg",
                                status = MessageStatus.SENT
                            )
                        )
                    }
                    AttachmentOption(icon = Icons.Default.PhotoLibrary, label = "Gallery") {
                        showAttachmentSheet = false
                        mockMessages.add(
                            MessageEntity(
                                id = "m_${System.currentTimeMillis()}",
                                conversationId = conversationId,
                                senderId = "me",
                                type = MessageType.IMAGE,
                                content = "gallery_photo.jpg",
                                status = MessageStatus.SENT
                            )
                        )
                    }
                    AttachmentOption(icon = Icons.Default.Videocam, label = "Video") {
                        showAttachmentSheet = false
                        mockMessages.add(
                            MessageEntity(
                                id = "m_${System.currentTimeMillis()}",
                                conversationId = conversationId,
                                senderId = "me",
                                type = MessageType.VIDEO,
                                content = Json.encodeToString(VideoPayload(durationMs = 12000L, width = 1080, height = 1920)),
                                status = MessageStatus.SENT
                            )
                        )
                    }
                    AttachmentOption(icon = Icons.Default.MusicNote, label = "Clip") {
                        showAttachmentSheet = false
                        onNavigateToClipTrimmer()
                    }
                    AttachmentOption(icon = Icons.Default.LocationOn, label = "Location") {
                        showAttachmentSheet = false
                        showLocationDialog = true
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Location Picker Dialog (Pin vs Live)
    if (showLocationDialog) {
        AlertDialog(
            onDismissRequest = { showLocationDialog = false },
            title = { Text("Share Location (E2EE)") },
            text = { Text("Coordinates are encrypted inside payload; never exposed plaintext to server.") },
            confirmButton = {
                Button(onClick = {
                    showLocationDialog = false
                    mockMessages.add(
                        MessageEntity(
                            id = "m_${System.currentTimeMillis()}",
                            conversationId = conversationId,
                            senderId = "me",
                            type = MessageType.LOCATION,
                            content = Json.encodeToString(LocationPayload(latitude = 37.7858, longitude = -122.4064, name = "Current Pin")),
                            status = MessageStatus.SENT
                        )
                    )
                }) {
                    Text("Share One-Time Pin")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showLocationDialog = false
                    mockMessages.add(
                        MessageEntity(
                            id = "m_${System.currentTimeMillis()}",
                            conversationId = conversationId,
                            senderId = "me",
                            type = MessageType.LOCATION,
                            content = Json.encodeToString(LocationPayload(latitude = 37.7858, longitude = -122.4064, isLive = true, durationSeconds = 3600L)),
                            status = MessageStatus.SENT
                        )
                    )
                }) {
                    Text("Share Live (1h)")
                }
            }
        )
    }

    // Stickers Bottom Sheet
    if (showStickersSheet) {
        ModalBottomSheet(onDismissRequest = { showStickersSheet = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sticker Packs", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    TextButton(onClick = {
                        Toast.makeText(context, "Import WhatsApp .wastickers or Sticker.ly pack", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import Pack")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    listOf("🎵 Music Vibes", "🎧 Audiophile", "⚡ Beats", "⭐ Favorites").forEach { packName ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .size(72.dp)
                                .clickable {
                                    showStickersSheet = false
                                    mockMessages.add(
                                        MessageEntity(
                                            id = "m_${System.currentTimeMillis()}",
                                            conversationId = conversationId,
                                            senderId = "me",
                                            type = MessageType.STICKER,
                                            content = Json.encodeToString(StickerPayload("nido_default", "music_note_1")),
                                            status = MessageStatus.SENT
                                        )
                                    )
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(packName.take(4), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Forward Dialog
    messageToForward?.let { msg ->
        AlertDialog(
            onDismissRequest = { messageToForward = null },
            title = { Text("Forward Message") },
            text = { Text("Re-encrypting message per recipient. Server never sees plaintext.") },
            confirmButton = {
                Button(onClick = {
                    messageToForward = null
                    Toast.makeText(context, "Forwarded to selected chats", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Forward")
                }
            },
            dismissButton = {
                TextButton(onClick = { messageToForward = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Dialog
    messageToEdit?.let { msg ->
        var editedText by remember { mutableStateOf(msg.content) }
        AlertDialog(
            onDismissRequest = { messageToEdit = null },
            title = { Text("Edit Message") },
            text = {
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    val idx = mockMessages.indexOfFirst { it.id == msg.id }
                    if (idx != -1) {
                        mockMessages[idx] = msg.copy(content = editedText, isEdited = true)
                    }
                    messageToEdit = null
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { messageToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Dialog
    messageToDelete?.let { msg ->
        val isSenderMe = msg.senderId == "me"
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = { Text("Delete Message") },
            text = {
                Text(
                    if (isSenderMe) "Do you want to delete this message for yourself or everyone?"
                    else "Do you want to delete this message for yourself?"
                )
            },
            confirmButton = {
                if (isSenderMe) {
                    Button(onClick = {
                        val idx = mockMessages.indexOfFirst { it.id == msg.id }
                        if (idx != -1) {
                            mockMessages[idx] = msg.copy(isDeleted = true, content = "This message was deleted")
                        }
                        messageToDelete = null
                    }) {
                        Text("Delete for Everyone")
                    }
                } else {
                    Button(onClick = {
                        mockMessages.removeAll { it.id == msg.id }
                        messageToDelete = null
                    }) {
                        Text("Delete for Me")
                    }
                }
            },
            dismissButton = {
                if (isSenderMe) {
                    OutlinedButton(onClick = {
                        mockMessages.removeAll { it.id == msg.id }
                        messageToDelete = null
                    }) {
                        Text("Delete for Me")
                    }
                } else {
                    TextButton(onClick = { messageToDelete = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    // Media Gallery Modal Sheet
    if (showMediaGallerySheet) {
        ModalBottomSheet(onDismissRequest = { showMediaGallerySheet = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Conversation Gallery", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Tab(selected = true, onClick = {}, text = { Text("Media (12)") })
                    Tab(selected = false, onClick = {}, text = { Text("Clips (4)") })
                    Tab(selected = false, onClick = {}, text = { Text("Docs (2)") })
                    Tab(selected = false, onClick = {}, text = { Text("Links (8)") })
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("End-to-End Encrypted files stored securely on device.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun AttachmentOption(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, fontSize = 12.sp)
    }
}
