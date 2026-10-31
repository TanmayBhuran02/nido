package com.nido.music.feature.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nido.music.feature.chat.data.ConversationEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onNavigateToChat: (String) -> Unit
) {
    var showCreateGroupDialog by remember { mutableStateOf(false) }

    val conversations = remember {
        mutableStateListOf(
            ConversationEntity(
                id = "conv_1",
                peerId = "peer_bob",
                title = "Bob",
                lastMessageSnippet = "Check out this song clip!",
                lastMessageTimestamp = System.currentTimeMillis() - 1000 * 60 * 5,
                unreadCount = 1,
                isGroup = false
            ),
            ConversationEntity(
                id = "conv_group_1",
                peerId = "group_music_squad",
                title = "Nido Music Squad 🎧",
                lastMessageSnippet = "Alice: Loved the new indie track",
                lastMessageTimestamp = System.currentTimeMillis() - 1000 * 60 * 25,
                unreadCount = 3,
                isGroup = true
            ),
            ConversationEntity(
                id = "conv_2",
                peerId = "peer_alice",
                title = "Alice",
                lastMessageSnippet = "Sounds good, see you tomorrow",
                lastMessageTimestamp = System.currentTimeMillis() - 1000 * 60 * 60,
                unreadCount = 0,
                isGroup = false
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Encrypted Chats", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Signal Protocol",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateGroupDialog = true }) {
                        Icon(Icons.Default.Group, contentDescription = "Create Group")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* New conversation */ },
                modifier = Modifier.padding(bottom = 80.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Chat")
            }
        }
    ) { padding ->
        if (conversations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No Encrypted Conversations Yet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "All messages and media are end-to-end encrypted with Signal Protocol & Sender Keys.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 88.dp)
            ) {
                items(conversations) { conv ->
                    ConversationItem(conversation = conv, onClick = { onNavigateToChat(conv.id) })
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }

    if (showCreateGroupDialog) {
        var groupName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateGroupDialog = false },
            title = { Text("Create Encrypted Group") },
            text = {
                Column {
                    Text("Groups use Signal Sender Keys with ratcheted forward secrecy and key rotation on membership change.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Group Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (groupName.isNotBlank()) {
                            conversations.add(
                                0,
                                ConversationEntity(
                                    id = "grp_${System.currentTimeMillis()}",
                                    peerId = "group_${groupName.lowercase().replace(" ", "_")}",
                                    title = groupName,
                                    lastMessageSnippet = "Group created",
                                    lastMessageTimestamp = System.currentTimeMillis(),
                                    unreadCount = 0,
                                    isGroup = true
                                )
                            )
                            showCreateGroupDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateGroupDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ConversationItem(
    conversation: ConversationEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
            color = if (conversation.isGroup) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    if (conversation.isGroup) Icons.Default.Group else Icons.Default.Person,
                    contentDescription = null,
                    tint = if (conversation.isGroup) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = conversation.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                if (conversation.isGroup) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text("GROUP", fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = conversation.lastMessageSnippet,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }

        if (conversation.unreadCount > 0) {
            Badge {
                Text(conversation.unreadCount.toString())
            }
        }
    }
}
