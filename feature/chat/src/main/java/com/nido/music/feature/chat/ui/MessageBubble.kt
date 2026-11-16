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
    isClipPlaying: Boolean = false,
    clipProgress: Float = 0f,
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
                    val effectiveType = remember(message) {
                        if (message.type == MessageType.TEXT && message.content.contains("\"packId\"") && message.content.contains("\"stickerId\"")) {
                            MessageType.STICKER
                        } else {
                            message.type
                        }
                    }
                    when (effectiveType) {
                        MessageType.CLIP -> {
                            if (clipPayload != null) {
                                ClipBubbleContent(
                                    clip = clipPayload,
                                    isPlaying = isClipPlaying,
                                    progress = clipProgress,
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
                                contentJson = message.content
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
                if (message.type == MessageType.TEXT && !message.isDeleted) {
                    DropdownMenuItem(
                        text = { Text("Copy") },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = { showMenu = false; onCopy(message) }
                    )
                }
                if (message.type == MessageType.STICKER && !message.isDeleted) {
                    DropdownMenuItem(
                        text = { Text("Add to my stickers") },
                        leadingIcon = { Icon(Icons.Default.AddReaction, contentDescription = null) },
                        onClick = { showMenu = false; onAddStickerToMyPack(message.content) }
                    )
                }
                DropdownMenuItem(
                    text = { Text(if (message.isStarred) "Unstar" else "Star") },
                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                    onClick = { showMenu = false; onStar(message) }
                )
                if (isMe && !message.isDeleted && message.type == MessageType.TEXT && (System.currentTimeMillis() - message.timestamp < 15 * 60 * 1000)) {
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
            val loc = Json.decodeFromString<LocationPayload>(contentJson)
            if (loc.latitude < -90.0 || loc.latitude > 90.0 || loc.longitude < -180.0 || loc.longitude > 180.0) {
                null
            } else {
                loc
            }
        } catch (e: Exception) {
            null
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            if (location != null) {
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
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOff,
                        contentDescription = "Invalid Location",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Invalid location coordinates",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun VoiceNoteBubbleContent(contentJson: String) {
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0.0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            val duration = 12000L
            val start = System.currentTimeMillis() - (progress * duration).toLong()
            while (isPlaying) {
                val elapsed = System.currentTimeMillis() - start
                val p = (elapsed.toFloat() / duration).coerceIn(0f, 1f)
                progress = p
                if (p >= 1f) {
                    isPlaying = false
                    progress = 0f
                    break
                }
                kotlinx.coroutines.delay(50)
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
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
                contentDescription = "Play/pause",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val currentSecs = (progress * 12).toInt()
                Text(
                    text = String.format("%d:%02d", currentSecs / 60, currentSecs % 60),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "0:12",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
    contentJson: String
) {
    val sticker = remember(contentJson) {
        try {
            Json.decodeFromString<StickerPayload>(contentJson)
        } catch (e: Exception) {
            null
        }
    }

    val displayEmoji = remember(sticker, contentJson) {
        if (sticker != null) {
            when (sticker.stickerId) {
                "guitar" -> "🎸"
                "headphones" -> "🎧"
                "keyboard" -> "🎹"
                "drums" -> "🥁"
                "sax" -> "🎷"
                "mic" -> "🎤"
                "lightning" -> "⚡"
                "fire" -> "🔥"
                "vinyl" -> "💿"
                "score" -> "🎼"
                "trumpet" -> "🎺"
                "music_note_1" -> "🎵"
                else -> sticker.emojis.firstOrNull() ?: if (sticker.stickerId.length <= 4) sticker.stickerId else "🎵"
            }
        } else {
            if (contentJson.length <= 4) contentJson else "🎵"
        }
    }

    Box(
        modifier = Modifier
            .size(110.dp)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayEmoji,
            fontSize = 64.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
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
    isPlaying: Boolean,
    progress: Float,
    onPlayClip: () -> Unit,
    onFindFullSong: () -> Unit = {}
) {
    val clipDurationMs = remember(clip) {
        val d = clip.endOffsetMs - clip.startOffsetMs
        if (d > 0) d else 15000L
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play / Pause Button
        IconButton(
            onClick = onPlayClip,
            modifier = Modifier
                .size(42.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause Clip" else "Play Clip",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Progress line + duration
        Column(modifier = Modifier.weight(1f)) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val currentSecs = (progress * clipDurationMs / 1000).toInt()
                val totalSecs = (clipDurationMs / 1000).toInt()
                Text(
                    text = String.format("%d:%02d", currentSecs / 60, currentSecs % 60),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = String.format("%d:%02d", totalSecs / 60, totalSecs % 60),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
