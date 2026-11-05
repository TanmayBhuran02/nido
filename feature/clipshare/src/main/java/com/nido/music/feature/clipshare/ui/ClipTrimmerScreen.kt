package com.nido.music.feature.clipshare.ui

import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
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
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

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
fun ClipTrimmerScreen(
    songId: Long,
    onNavigateBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ClipTrimmerViewModel = hiltViewModel()
) {
    LaunchedEffect(songId) {
        viewModel.loadSong(songId)
    }

    val song by viewModel.song.collectAsState()
    val startMs by viewModel.startMs.collectAsState()
    val endMs by viewModel.endMs.collectAsState()
    val isPlayingPreview by viewModel.isPlayingPreview.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val exportSuccess by viewModel.exportSuccess.collectAsState()

    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Ambient background glow
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.Center)
                .blur(90.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            AmberPrimary.copy(alpha = 0.25f),
                            Color(0xFFE07B3C).copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = statusBarHeight + 64.dp, start = 20.dp, end = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Waveform Trimmer Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(GlassCardBg)
                    .border(1.dp, EdgeSpecular, RoundedCornerShape(32.dp))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            onClick = { viewModel.togglePreview() },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(AmberPrimary.copy(alpha = 0.15f))
                                .border(1.dp, AmberPrimary, CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isPlayingPreview) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlayingPreview) "Pause" else "Play",
                                tint = AmberPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = song?.title ?: "Loading...",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${song?.artist ?: ""} • Max 30s Segment",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Trimmer Sliders with dual Start and End controls
                val totalDuration = (song?.durationMs ?: 60000L).coerceAtLeast(2000L)
                val clipLengthSec = ((endMs - startMs) / 1000L).coerceAtLeast(0L)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(GlassCardBg.copy(alpha = 0.6f))
                        .border(1.dp, EdgeSpecular, RoundedCornerShape(24.dp))
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Trim Range",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(AmberPrimary.copy(alpha = 0.18f))
                                .border(1.dp, AmberPrimary.copy(alpha = 0.4f), CircleShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Duration: ${clipLengthSec}s",
                                color = AmberPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Dual Range Slider (clipping from BOTH start and end)
                    RangeSlider(
                        value = (startMs.toFloat())..(endMs.toFloat()),
                        onValueChange = { range ->
                            val newStart = range.start.toLong().coerceIn(0L, totalDuration - 1000L)
                            val newEnd = range.endInclusive.toLong().coerceIn(newStart + 1000L, totalDuration)
                            viewModel.updateRange(newStart, newEnd)
                        },
                        valueRange = 0f..totalDuration.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = AmberPrimary,
                            activeTrackColor = AmberPrimary,
                            inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Direct Start & End Fine-Adjustment Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Start point card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, EdgeSpecular, RoundedCornerShape(16.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "Start: ${formatDuration(startMs)}",
                                    color = AmberPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = {
                                            val newStart = (startMs - 1000L).coerceAtLeast(0L)
                                            viewModel.updateRange(newStart, endMs)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary.copy(alpha = 0.12f)),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f).height(30.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("-1s", color = AmberPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = {
                                            val newStart = (startMs + 1000L).coerceAtMost(endMs - 1000L)
                                            viewModel.updateRange(newStart, endMs)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary.copy(alpha = 0.12f)),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f).height(30.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("+1s", color = AmberPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // End point card
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, EdgeSpecular, RoundedCornerShape(16.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "End: ${formatDuration(endMs)}",
                                    color = AmberPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = {
                                            val newEnd = (endMs - 1000L).coerceAtLeast(startMs + 1000L)
                                            viewModel.updateRange(startMs, newEnd)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary.copy(alpha = 0.12f)),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f).height(30.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("-1s", color = AmberPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = {
                                            val newEnd = (endMs + 1000L).coerceAtMost(totalDuration)
                                            viewModel.updateRange(startMs, newEnd)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary.copy(alpha = 0.12f)),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.weight(1f).height(30.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("+1s", color = AmberPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (exportSuccess != null) {
                    val context = LocalContext.current
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Clip saved successfully!",
                            color = AmberPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                try {
                                    val file = File(exportSuccess!!)
                                    val uri = FileProvider.getUriForFile(
                                        context,
                                        "${context.packageName}.fileprovider",
                                        file
                                    )
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "audio/*"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share Clip"))
                                } catch (_: Exception) {}
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary.copy(alpha = 0.2f)),
                            shape = CircleShape
                        ) {
                            Text("Share via App...", color = AmberPrimary, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Action button
                Button(
                    onClick = { viewModel.exportClip() },
                    enabled = !isExporting,
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .navigationBarsPadding()
                ) {
                    if (isExporting) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Export Audio Clip",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

        // Top Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(statusBarHeight + 56.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
                .border(0.5.dp, EdgeSpecular, RoundedCornerShape(0.dp))
                .padding(top = statusBarHeight, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Clip Trimmer",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
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
