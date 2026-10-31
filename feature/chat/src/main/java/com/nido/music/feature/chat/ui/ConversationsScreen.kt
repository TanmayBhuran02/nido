package com.nido.music.feature.chat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nido.music.feature.chat.data.ConversationEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onNavigateToChat: (String) -> Unit
) {
    // Sample conversations list
    val mockConversations = remember {
        listOf(
            ConversationEntity(
                id = "conv_1",
                peerId = "peer_bob",
                title = "Bob",
                lastMessageSnippet = "Check out this song clip!",
                lastMessageTimestamp = System.currentTimeMillis() - 1000 * 60 * 5,
                unreadCount = 1
            ),
            ConversationEntity(
                id = "conv_2",
                peerId = "peer_alice",
                title = "Alice",
                lastMessageSnippet = "Sounds good, see you tomorrow",
                lastMessageTimestamp = System.currentTimeMillis() - 1000 * 60 * 60,
                unreadCount = 0
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Encrypted Chats", fontWeight = FontWeight.Bold) }
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            items(mockConversations) { conv ->
                ConversationItem(conversation = conv, onClick = { onNavigateToChat(conv.id) })
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
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
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(text = conversation.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
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
