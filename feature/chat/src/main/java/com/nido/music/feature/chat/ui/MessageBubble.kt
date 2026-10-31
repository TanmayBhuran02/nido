package com.nido.music.feature.chat.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nido.music.feature.chat.data.*
import kotlinx.serialization.json.Json

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    isMe: Boolean,
    clipPayload: ClipPayload? = null,
    onPlayClip: (ClipPayload) -> Unit = {},
    onFindFullSong: (String, String) -> Unit = { _, _ -> },
    onReply: (MessageEntity) -> Unit = {},
    onForward: (MessageEntity) -> Unit = {},
    onCopy: (MessageEntity) -> Unit = {},
    onStar: (MessageEntity) -> Unit = {},
    onEdit: (MessageEntity) -> Unit = {},
    onDelete: (MessageEntity) -> Unit = {},
    onAddStickerToMyPack: (String) -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { showMenu = true }
                )
        ) {
            Column(modifier = Modifier.padding(10.dp)) {

                // Forwarded tag
                if (message.isForwarded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Icon(
                            Icons.Default.Redo,
                            contentDescription = "Forwarded",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Forwarded",
                            fontSize = 11.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                // Reply Quote Preview
                if (!message.replyToSnippet.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(modifier = Modifier.padding(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(28.dp)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = message.replyToSnippet,
                                fontSize = 12.sp,
                                maxLines = 2,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Deleted Message Display
                if (message.isDeleted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Block,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "This message was deleted",
                            fontStyle = FontStyle.Italic,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    // Content rendering according to MessageType
                    when (message.type) {
                        MessageType.CLIP -> {
                            if (clipPayload != null) {
                                ClipBubbleContent(
                                    clip = clipPayload,
                                    onPlayClip = { onPlayClip(clipPayload) },
                                    onFindFullSong = { onFindFullSong(clipPayload.title, clipPayload.artist) }
                                )
                            } else {
                                Text("Music Clip", fontSize = 14.sp)
                            }
                        }
                        MessageType.LOCATION -> {
                            LocationBubbleContent(contentJson = message.content)
                        }
                        MessageType.AUDIO -> {
                            VoiceNoteBubbleContent(contentJson = message.content)
                        }
                        MessageType.VIDEO -> {
                            VideoBubbleContent(contentJson = message.content)
                        }
                        MessageType.STICKER -> {
                            StickerBubbleContent(
                                contentJson = message.content,
                                onAddToMyPack = onAddStickerToMyPack
                            )
                        }
                        MessageType.IMAGE -> {
                            ImageBubbleContent(content = message.content)
                        }
                        else -> {
                            Text(
                                text = message.content,
                                fontSize = 15.sp,
                                color = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Bottom timestamp, edited label, star icon, and delivery status
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isStarred) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Starred",
                            modifier = Modifier.size(12.dp),
                            tint = Color(0xFFFFB300)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (message.isEdited && !message.isDeleted) {
                        Text(
                            text = "edited ",
                            fontSize = 10.sp,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    Text(
                        text = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                            .format(java.util.Date(message.timestamp)),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    if (isMe && !message.isDeleted) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (message.status) {
                                MessageStatus.OUTBOX -> "⏱"
                                MessageStatus.SENDING -> "⏳"
                                MessageStatus.SENT -> "✓"
                                MessageStatus.DELIVERED -> "✓✓"
                                MessageStatus.READ -> "✓✓"
                                MessageStatus.FAILED -> "⚠️"
                            },
                            fontSize = 10.sp,
                            color = if (message.status == MessageStatus.READ) Color(0xFF00E676) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Message Options Menu
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Reply") },
                    leadingIcon = { Icon(Icons.Default.Reply, contentDescription = null) },
                    onClick = { showMenu = false; onReply(message) }
                )
                DropdownMenuItem(
                    text = { Text("Forward") },
                    leadingIcon = { Icon(Icons.Default.Redo, contentDescription = null) },
                    onClick = { showMenu = false; onForward(message) }
                )
                DropdownMenuItem(
                    text = { Text("Copy") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    onClick = { showMenu = false; onCopy(message) }
                )
                DropdownMenuItem(
                    text = { Text(if (message.isStarred) "Unstar" else "Star") },
                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                    onClick = { showMenu = false; onStar(message) }
                )
                if (isMe && !message.isDeleted && (System.currentTimeMillis() - message.timestamp < 15 * 60 * 1000)) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = { showMenu = false; onEdit(message) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = { showMenu = false; onDelete(message) }
                )
            }
        }
    }
}

@Composable
fun LocationBubbleContent(contentJson: String) {
    val location = remember(contentJson) {
        try {
            Json.decodeFromString<LocationPayload>(contentJson)
        } catch (e: Exception) {
            LocationPayload(0.0, 0.0, "Pin Location")
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = "Location",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (location.isLive) "Live Location (Active)" else (location.name ?: "Pinned Location"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "%.5f, %.5f".format(location.latitude, location.longitude),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { /* Open OSM / MapLibre preview */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Map Preview", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun VoiceNoteBubbleContent(contentJson: String) {
    var isPlaying by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1.0f) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { isPlaying = !isPlaying },
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Voice note play/pause",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Synthetic waveform visualizer bars
        Row(
            modifier = Modifier
                .weight(1f)
                .height(28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            val heights = listOf(8.dp, 16.dp, 24.dp, 12.dp, 20.dp, 14.dp, 26.dp, 18.dp, 10.dp, 22.dp)
            heights.forEach { h ->
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(h)
                        .background(
                            color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(1.5.dp)
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Speed toggle button (1x -> 1.5x -> 2x)
        FilledTonalButton(
            onClick = {
                speed = when (speed) {
                    1.0f -> 1.5f
                    1.5f -> 2.0f
                    else -> 1.0f
                }
            },
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
            modifier = Modifier.height(28.dp)
        ) {
            Text("${speed}x", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun VideoBubbleContent(contentJson: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.Black,
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            IconButton(
                onClick = { /* Play video */ },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.8f), CircleShape)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play Video", tint = Color.Black)
            }
            Text(
                text = "Video",
                fontSize = 11.sp,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
fun StickerBubbleContent(
    contentJson: String,
    onAddToMyPack: (String) -> Unit
) {
    var showStickerOptions by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(140.dp)
            .combinedClickable(
                onClick = {},
                onLongClick = { showStickerOptions = true }
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("🎵 Sticker", fontSize = 24.sp)
            }
        }

        DropdownMenu(
            expanded = showStickerOptions,
            onDismissRequest = { showStickerOptions = false }
        ) {
            DropdownMenuItem(
                text = { Text("Add to my stickers") },
                leadingIcon = { Icon(Icons.Default.AddReaction, contentDescription = null) },
                onClick = {
                    showStickerOptions = false
                    onAddToMyPack(contentJson)
                }
            )
        }
    }
}

@Composable
fun ImageBubbleContent(content: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Image, contentDescription = "Image attachment", modifier = Modifier.size(48.dp))
        }
    }
}

@Composable
fun ClipBubbleContent(
    clip: ClipPayload,
    onPlayClip: () -> Unit,
    onFindFullSong: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onPlayClip,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Clip",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = clip.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1
                )
                Text(
                    text = "from: ${clip.artist}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Clip: ${(clip.endOffsetMs - clip.startOffsetMs) / 1000}s",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onFindFullSong,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors()
        ) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Find full song", fontSize = 12.sp)
        }
    }
}
