package com.nido.music.feature.music.ui.player

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.nido.music.feature.music.ui.library.LibraryViewModel
import kotlinx.coroutines.launch
import java.io.File

private val AmberPrimary: Color
    @Composable
    get() = MaterialTheme.colorScheme.primary

private val CanvasFloor: Color
    @Composable
    get() = MaterialTheme.colorScheme.background

private val EdgeSpecular: Color
    @Composable
    get() = MaterialTheme.colorScheme.outline

private val TextPrimary: Color
    @Composable
    get() = MaterialTheme.colorScheme.onSurface

private val TextSecondary: Color
    @Composable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

private val TextTertiary: Color
    @Composable
    get() = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

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
    val likedSongs by viewModel.likedSongs.collectAsState()
    val sleepTimerRemaining by controller.sleepTimerRemaining.collectAsState()

    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var isControllerExpanded by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val queueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var volumeFraction by remember {
        mutableFloatStateOf(
            (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume.toFloat()).coerceIn(0f, 1f)
        )
    }

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val playerBackdrop = rememberLayerBackdrop()
    val controlSurface = MaterialTheme.colorScheme.surface
    val heroPanelSurface = MaterialTheme.colorScheme.surfaceVariant

    // Dynamic responsive sizing for artwork when controller expands
    val artworkSize by animateDpAsState(
        targetValue = if (isControllerExpanded) 130.dp else 260.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 320f),
        label = "artworkSize"
    )
    val artworkCorner by animateDpAsState(
        targetValue = if (isControllerExpanded) 20.dp else 28.dp,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 320f),
        label = "artworkCorner"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Navigation & Action Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBarHeight + 8.dp, start = 16.dp, end = 16.dp, bottom = 6.dp),
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
                        effects = { blur(16.dp.toPx()) },
                        onDrawSurface = { drawRect(controlSurface) }
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

            // Central Playing Indicator / Quality Pill
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AmberPrimary.copy(alpha = 0.12f))
                    .border(0.5.dp, AmberPrimary.copy(alpha = 0.35f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = AmberPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = if (isPlaying) "NOW PLAYING" else "PAUSED",
                        color = AmberPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Share Clip Action
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .drawBackdrop(
                        backdrop = playerBackdrop,
                        shape = { RoundedCornerShape(20.dp) },
                        effects = { blur(16.dp.toPx()) },
                        onDrawSurface = { drawRect(controlSurface) }
                    )
                    .border(1.dp, EdgeSpecular, RoundedCornerShape(20.dp))
                    .clickable {
                        currentSong?.let { onNavigateToClipTrimmer(it.id) }
                    }
                    .padding(horizontal = 14.dp),
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

        // Center-fit Visual Section above controllers
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .layerBackdrop(playerBackdrop),
            contentAlignment = Alignment.Center
        ) {
            // Ambient light orbs synced with active theme
            Box(
                modifier = Modifier
                    .size(if (isControllerExpanded) 220.dp else 320.dp)
                    .align(Alignment.Center)
                    .blur(100.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                AmberPrimary.copy(alpha = 0.32f),
                                AmberPrimary.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )

            // Dynamic Album Artwork Container (Center Fitted)
            Box(
                modifier = Modifier
                    .size(artworkSize)
                    .shadow(elevation = 20.dp, shape = RoundedCornerShape(artworkCorner), spotColor = AmberPrimary)
                    .clip(RoundedCornerShape(artworkCorner))
                    .background(heroPanelSurface)
                    .border(1.dp, EdgeSpecular, RoundedCornerShape(artworkCorner)),
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        AmberPrimary.copy(alpha = 0.25f),
                                        heroPanelSurface
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = AmberPrimary.copy(alpha = 0.6f),
                            modifier = Modifier.size(if (isControllerExpanded) 48.dp else 96.dp)
                        )
                    }
                }
            }
        }

        // Vertically Expandable Hero Controls Panel
        val controllerShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBackdrop(
                    backdrop = playerBackdrop,
                    shape = { controllerShape },
                    effects = { blur(36.dp.toPx()) },
                    onDrawSurface = { drawRect(heroPanelSurface) }
                )
                .border(1.dp, EdgeSpecular, controllerShape)
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 12.dp)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        if (dragAmount < -16) {
                            isControllerExpanded = true
                        } else if (dragAmount > 16) {
                            isControllerExpanded = false
                        }
                    }
                }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Interactive Drag Handle Bar (Gesture & Tap Driven - No ^ Icon)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isControllerExpanded = !isControllerExpanded }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 38.dp, height = 4.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Track Title & Artist with Like Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentSong?.title ?: "No Song Playing",
                            color = TextPrimary,
                            fontSize = if (isControllerExpanded) 20.sp else 19.sp,
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
                        val isLiked = likedSongs.any { it.id == song.id }
                        IconButton(onClick = { viewModel.toggleLike(song.id) }) {
                            Icon(
                                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Like",
                                tint = if (isLiked) AmberPrimary else TextSecondary,
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
                        thumbColor = AmberPrimary,
                        activeTrackColor = AmberPrimary,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
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

                    // Hero Play/Pause Button with glowing ambient shadow
                    Box(
                        modifier = Modifier
                            .size(64.dp)
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
                            modifier = Modifier.size(34.dp)
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

                // Expandable Section: Volume, Seek-10s, Quality & Next Track
                AnimatedVisibility(
                    visible = isControllerExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        // Quick Seek & Jump Row (-10s / +10s)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(controlSurface.copy(alpha = 0.5f))
                                    .border(1.dp, EdgeSpecular, RoundedCornerShape(14.dp))
                                    .clickable {
                                        controller.seekTo((position - 10000L).coerceAtLeast(0L))
                                    }
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Replay10, contentDescription = "-10s", tint = AmberPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text("-10s", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }

                            Spacer(modifier = Modifier.size(16.dp))

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(controlSurface.copy(alpha = 0.5f))
                                    .border(1.dp, EdgeSpecular, RoundedCornerShape(14.dp))
                                    .clickable {
                                        controller.seekTo((position + 10000L).coerceAtMost(duration))
                                    }
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("+10s", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Icon(Icons.Default.Forward10, contentDescription = "+10s", tint = AmberPrimary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Live Volume Slider Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeDown,
                                contentDescription = "Volume Down",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Slider(
                                value = volumeFraction,
                                onValueChange = { frac ->
                                    volumeFraction = frac
                                    val newVol = (frac * maxVolume).toInt()
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = AmberPrimary,
                                    activeTrackColor = AmberPrimary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Volume Up",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Up Next Track Preview Card
                        val currentIdx = currentQueue.indexOfFirst { it.id == currentSong?.id }
                        val nextSong = if (currentIdx in 0 until currentQueue.lastIndex) currentQueue[currentIdx + 1] else null
                        if (nextSong != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(controlSurface.copy(alpha = 0.6f))
                                    .border(1.dp, EdgeSpecular, RoundedCornerShape(16.dp))
                                    .clickable { showQueueSheet = true }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = AmberPrimary, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.size(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("UP NEXT", color = AmberPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                                        Text(nextSong.title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Text("View Queue", color = AmberPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Utilities Row: Sleep Timer and Queue
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sleep Timer
                    IconButton(onClick = { showSleepTimerDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Sleep Timer",
                            tint = if (sleepTimerRemaining != null) AmberPrimary else TextSecondary
                        )
                    }

                    // Queue button with count badge
                    IconButton(onClick = { showQueueSheet = true }) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                contentDescription = "Queue",
                                tint = if (showQueueSheet) AmberPrimary else TextSecondary
                            )
                            if (currentQueue.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(AmberPrimary, CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

        // Sleep Timer Dialog (Theme Compliant)
        if (showSleepTimerDialog) {
            AlertDialog(
                onDismissRequest = { showSleepTimerDialog = false },
                title = { Text("Sleep Timer", color = TextPrimary) },
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
                                Text("$mins minutes", color = AmberPrimary, fontWeight = FontWeight.SemiBold)
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
                                Text("Turn Off", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSleepTimerDialog = false }) {
                        Text("Close", color = TextSecondary)
                    }
                },
                containerColor = heroPanelSurface
            )
        }

        // Vertically Expandable & Theme-Compliant Queue Sheet
        if (showQueueSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQueueSheet = false },
                sheetState = queueSheetState,
                containerColor = heroPanelSurface,
                dragHandle = {
                    BottomSheetDefaults.DragHandle(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    // Queue Header
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                    ) {
                        Text(
                            text = "Current Queue",
                            color = TextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${currentQueue.size} tracks upcoming",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    if (currentQueue.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No tracks in playback queue",
                                color = TextSecondary,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(currentQueue, key = { it.id }) { qSong ->
                                val isCurrent = qSong.id == currentSong?.id
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isCurrent) AmberPrimary.copy(alpha = 0.18f)
                                            else controlSurface.copy(alpha = 0.5f)
                                        )
                                        .border(
                                            1.dp,
                                            if (isCurrent) AmberPrimary.copy(alpha = 0.5f) else EdgeSpecular,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable {
                                            controller.playSong(qSong, currentQueue)
                                            showQueueSheet = false
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Track indicator icon
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                if (isCurrent) AmberPrimary.copy(alpha = 0.25f)
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isCurrent) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                                            contentDescription = null,
                                            tint = if (isCurrent) AmberPrimary else TextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.size(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = qSong.title,
                                            color = if (isCurrent) AmberPrimary else TextPrimary,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 14.sp,
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

                                    Text(
                                        text = formatDuration(qSong.durationMs),
                                        color = if (isCurrent) AmberPrimary else TextTertiary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
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
