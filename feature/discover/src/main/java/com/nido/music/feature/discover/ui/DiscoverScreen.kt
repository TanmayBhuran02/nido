package com.nido.music.feature.discover.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.nido.music.core.network.model.SearchResult

private val AmberPrimary: Color
    @Composable
    get() = MaterialTheme.colorScheme.primary

private val CanvasFloor: Color
    @Composable
    get() = MaterialTheme.colorScheme.background

private val GlassCardBg: Color
    @Composable
    get() = MaterialTheme.colorScheme.surface

private val EdgeSpecular: Color
    @Composable
    get() = MaterialTheme.colorScheme.outline

private val TextPrimary: Color
    @Composable
    get() = MaterialTheme.colorScheme.onSurface

private val TextSecondary: Color
    @Composable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

@Composable
fun DiscoverScreen(
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel()
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var searchInput by remember { mutableStateOf("") }
    val results by viewModel.results.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val playingTrackId by viewModel.playingTrackId.collectAsState()
    val downloadingTrackIds by viewModel.downloadingTrackIds.collectAsState()
    val downloadSuccessTrackIds by viewModel.downloadSuccessTrackIds.collectAsState()

    val discoverBackdrop = rememberLayerBackdrop()
    val cardSurface = MaterialTheme.colorScheme.surface
    val elevatedSurface = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Underlying background layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(discoverBackdrop)
        ) {
            // Ambient orbs
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .align(Alignment.TopStart)
                    .padding(top = 60.dp)
                    .blur(90.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                AmberPrimary.copy(alpha = 0.2f),
                                Color(0xFF5BA68A).copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        ),
                        CircleShape
                    )
            )
        }

        Column(
            modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 140.dp)
            ) {
                Spacer(modifier = Modifier.height(statusBarHeight + 70.dp))

                // Search Bar Input
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    TextField(
                        value = searchInput,
                        onValueChange = { searchInput = it },
                        placeholder = { Text("Search Creative Commons music...", color = TextSecondary) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = AmberPrimary
                            )
                        },
                        trailingIcon = {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = AmberPrimary,
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { viewModel.search(searchInput) }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = GlassCardBg,
                            unfocusedContainerColor = GlassCardBg,
                            focusedIndicatorColor = AmberPrimary,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(26.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, EdgeSpecular, RoundedCornerShape(26.dp))
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (results.isEmpty() && !isLoading) {
                    Spacer(modifier = Modifier.weight(1f))
                    // Empty State Glass Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .drawBackdrop(
                                backdrop = discoverBackdrop,
                                shape = { RoundedCornerShape(32.dp) },
                                effects = {
                                    blur(24.dp.toPx())
                                },
                                onDrawSurface = {
                                    drawRect(cardSurface)
                                }
                            )
                            .border(1.dp, EdgeSpecular, RoundedCornerShape(32.dp))
                            .padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(AmberPrimary.copy(alpha = 0.15f))
                                    .border(1.dp, AmberPrimary.copy(alpha = 0.35f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = AmberPrimary,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Discover Open Catalogs",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Explore legal music from Jamendo, Internet Archive, and Audius with instant 30s previews.",
                                color = TextSecondary,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1.2f))
                } else {
                    // Search Results List
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(results, key = { it.id }) { track ->
                            val isPreviewPlaying = playingTrackId == track.id
                            val isDownloading = downloadingTrackIds.contains(track.id)
                            val isDownloaded = downloadSuccessTrackIds.contains(track.id)
                            DiscoverTrackRow(
                                track = track,
                                isPlaying = isPreviewPlaying,
                                isDownloading = isDownloading,
                                isDownloaded = isDownloaded,
                                onTogglePreview = { viewModel.togglePreview(track) },
                                onDownload = { viewModel.downloadTrack(track) }
                            )
                        }
                    }
                }
            }

        // Frosted Glass Top Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(statusBarHeight + 56.dp)
                .drawBackdrop(
                    backdrop = discoverBackdrop,
                    shape = { RoundedCornerShape(0.dp) },
                    effects = {
                        blur(20.dp.toPx())
                    },
                    onDrawSurface = {
                        drawRect(elevatedSurface)
                    }
                )
                .border(0.5.dp, EdgeSpecular, RoundedCornerShape(0.dp))
                .padding(top = statusBarHeight, start = 20.dp, end = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "Discover",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DiscoverTrackRow(
    track: SearchResult,
    isPlaying: Boolean,
    isDownloading: Boolean = false,
    isDownloaded: Boolean = false,
    onTogglePreview: () -> Unit,
    onDownload: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.03f))
            .clickable { onTogglePreview() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AmberPrimary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = AmberPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${track.artist} • ${track.provider}",
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(AmberPrimary.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = track.license ?: "CC",
                color = AmberPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.size(4.dp))
        IconButton(
            onClick = onDownload,
            enabled = !isDownloading && !isDownloaded
        ) {
            if (isDownloading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = AmberPrimary
                )
            } else if (isDownloaded) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Downloaded",
                    tint = AmberPrimary,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = "Download",
                    tint = AmberPrimary.copy(alpha = 0.8f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        IconButton(onClick = onTogglePreview) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Stop" else "Preview",
                tint = AmberPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
