package com.nido.music.feature.music.ui.player

import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.nido.music.feature.music.ui.library.LibraryViewModel

private val AmberPrimary = Color(0xFFF59E0B)
private val CanvasFloor = Color(0xFF0B0B0E)
private val GlassCardLevel3 = Color(0xD9101018)
private val EdgeSpecular = Color(0x24FFFFFF)
private val TextPrimary = Color(0xFFF8FAFC)
private val TextSecondary = Color(0xA6F8FAFC)
private val TextTertiary = Color(0x61F8FAFC)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToClipTrimmer: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val controller = viewModel.playbackController
    val currentSong by controller.currentSong.collectAsState()
    val currentQueue by controller.currentQueue.collectAsState()
    val isPlaying by controller.isPlaying.collectAsState()
    val position by controller.currentPosition.collectAsState()
    val duration by controller.duration.collectAsState()
    val shuffleEnabled by controller.shuffleEnabled.collectAsState()
    val repeatMode by controller.repeatMode.collectAsState()
    val sleepTimerRemaining by controller.sleepTimerRemaining.collectAsState()

    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val playerBackdrop = rememberLayerBackdrop()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasFloor)
    ) {
        // Sampled layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(playerBackdrop)
        ) {
            // Ambient light orbs
            Box(
                modifier = Modifier
                    .size(360.dp)
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .blur(100.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                AmberPrimary.copy(alpha = 0.35f),
                                Color(0xFFD97706).copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )

            // Album Artwork Container
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = statusBarHeight + 64.dp)
                    .size(260.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF181820))
                    .border(1.dp, EdgeSpecular, RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                val coverPath = currentSong?.coverArtPath
                if (coverPath != null && File(coverPath).exists()) {
                    AsyncImage(
                        model = File(coverPath),
                        contentDescription = "Album Art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = AmberPrimary.copy(alpha = 0.4f),
                        modifier = Modifier.size(96.dp)
                    )
                }
            }
        }

        // Top Navigation & Action Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBarHeight + 8.dp, start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Back button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .drawBackdrop(
                        backdrop = playerBackdrop,
                        shape = { RoundedCornerShape(22.dp) },
                        effects = {
                            blur(16.dp.toPx())
                        },
                        onDrawSurface = {
                            drawRect(Color(0x551E1E26))
                        }
                    )
                    .border(1.dp, EdgeSpecular, RoundedCornerShape(22.dp))
                    .clickable { onNavigateBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Share Clip Action
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .drawBackdrop(
                        backdrop = playerBackdrop,
                        shape = { RoundedCornerShape(20.dp) },
                        effects = {
                            blur(16.dp.toPx())
                        },
                        onDrawSurface = {
                            drawRect(Color(0x551E1E26))
                        }
                    )
                    .border(1.dp, EdgeSpecular, RoundedCornerShape(20.dp))
                    .clickable {
                        currentSong?.let { onNavigateToClipTrimmer(it.id) }
                    }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ContentCut,
                        contentDescription = "Trim Clip",
                        tint = AmberPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = "Share Clip",
                        color = AmberPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Hero Controls Panel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .drawBackdrop(
                    backdrop = playerBackdrop,
                    shape = { RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp) },
                    effects = {
                        blur(36.dp.toPx())
                    },
                    onDrawSurface = {
                        drawRect(GlassCardLevel3)
                    }
                )
                .border(1.dp, EdgeSpecular, RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp))
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Track Title & Artist with Like Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSong?.title ?: "No Song Playing",
                            color = TextPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = currentSong?.artist ?: "Select music from your library",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    currentSong?.let { song ->
                        IconButton(onClick = { viewModel.toggleLike(song.id) }) {
                            Icon(
                                imageVector = if (song.liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (song.liked) AmberPrimary else TextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Scrubber Slider
                val progressFraction = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
                Slider(
                    value = progressFraction,
                    onValueChange = { fraction ->
                        controller.seekTo((fraction * duration).toLong())
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = AmberPrimary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatDuration(position), color = TextTertiary, fontSize = 12.sp)
                    Text(text = formatDuration(duration), color = TextTertiary, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Playback Controls Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { controller.toggleShuffle() }) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (shuffleEnabled) AmberPrimary else TextSecondary
                        )
                    }
                    IconButton(onClick = { controller.skipPrevious() }) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Hero Play/Pause Button
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .shadow(20.dp, CircleShape, spotColor = AmberPrimary)
                            .clip(CircleShape)
                            .background(AmberPrimary)
                            .clickable { controller.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = CanvasFloor,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(onClick = { controller.skipNext() }) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    IconButton(onClick = { controller.toggleRepeat() }) {
                        Icon(
                            imageVector = when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                                else -> Icons.Default.Repeat
                            },
                            contentDescription = "Repeat",
                            tint = if (repeatMode != Player.REPEAT_MODE_OFF) AmberPrimary else TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Utilities Row: Queue & Sleep Timer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = { showSleepTimerDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Sleep Timer",
                            tint = if (sleepTimerRemaining != null) AmberPrimary else TextSecondary
                        )
                    }
                    IconButton(onClick = { showQueueSheet = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Queue",
                            tint = if (showQueueSheet) AmberPrimary else TextSecondary
                        )
                    }
                }
            }
        }

        // Sleep Timer Dialog
        if (showSleepTimerDialog) {
            AlertDialog(
                onDismissRequest = { showSleepTimerDialog = false },
                title = { Text("Sleep Timer") },
                text = {
                    Column {
                        listOf(15, 30, 45, 60).forEach { mins ->
                            TextButton(
                                onClick = {
                                    controller.startSleepTimer(mins)
                                    showSleepTimerDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("$mins minutes", color = AmberPrimary)
                            }
                        }
                        if (sleepTimerRemaining != null) {
                            TextButton(
                                onClick = {
                                    controller.cancelSleepTimer()
                                    showSleepTimerDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Turn Off", color = Color.Red)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSleepTimerDialog = false }) {
                        Text("Close")
                    }
                }
            )
        }

        // Queue Bottom Sheet
        if (showQueueSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQueueSheet = false },
                containerColor = Color(0xFF14141A)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Current Queue (${currentQueue.size} songs)",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    if (currentQueue.isEmpty()) {
                        Text(
                            text = "No upcoming tracks in queue",
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().height(320.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(currentQueue, key = { it.id }) { qSong ->
                                val isCurrent = qSong.id == currentSong?.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isCurrent) AmberPrimary.copy(alpha = 0.15f)
                                            else Color.White.copy(alpha = 0.03f)
                                        )
                                        .clickable {
                                            controller.playSong(qSong, currentQueue)
                                            showQueueSheet = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = if (isCurrent) AmberPrimary else TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.size(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = qSong.title,
                                            color = if (isCurrent) AmberPrimary else TextPrimary,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = qSong.artist,
                                            color = TextSecondary,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSecs = (ms / 1000).coerceAtLeast(0)
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%d:%02d", mins, secs)
}
