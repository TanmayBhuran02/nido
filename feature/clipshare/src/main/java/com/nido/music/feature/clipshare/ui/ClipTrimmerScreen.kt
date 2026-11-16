package com.nido.music.feature.clipshare.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

data class ClipContact(
    val name: String,
    val phoneNumber: String,
    val hasNido: Boolean = false,
    val username: String? = null
)

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipTrimmerScreen(
    songId: Long,
    onNavigateBack: () -> Unit = {},
    onShareToChat: ((peerId: String, peerTitle: String, clipTitle: String, artist: String, startMs: Long, endMs: Long, filePath: String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: ClipTrimmerViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(songId) {
        viewModel.loadSong(songId)
    }

    val song by viewModel.song.collectAsState()
    val startMs by viewModel.startMs.collectAsState()
    val endMs by viewModel.endMs.collectAsState()
    val isPlayingPreview by viewModel.isPlayingPreview.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val exportSuccess by viewModel.exportSuccess.collectAsState()

    var showShareSheet by remember { mutableStateOf(false) }
    var pendingShareAfterExport by remember { mutableStateOf(false) }

    var hasContactsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasContactsPermission = granted
    }

    val contactsList = remember(hasContactsPermission) {
        queryClipContacts(context)
    }

    LaunchedEffect(exportSuccess) {
        if (exportSuccess != null && pendingShareAfterExport) {
            pendingShareAfterExport = false
            showShareSheet = true
        }
    }

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

                Spacer(modifier = Modifier.weight(1f))

                // Action button: Share To
                Button(
                    onClick = {
                        if (exportSuccess != null) {
                            showShareSheet = true
                        } else {
                            pendingShareAfterExport = true
                            viewModel.exportClip()
                        }
                    },
                    enabled = !isExporting,
                    colors = ButtonDefaults.buttonColors(containerColor = AmberPrimary),
                    shape = CircleShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .navigationBarsPadding()
                ) {
                    if (isExporting) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.size(10.dp))
                            Text(
                                text = "Preparing Clip...",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = "Share To",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
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

        // Share Contacts Bottom Sheet
        if (showShareSheet && exportSuccess != null) {
            ShareClipBottomSheet(
                songTitle = song?.title ?: "Clip",
                artistName = song?.artist ?: "Unknown Artist",
                startMs = startMs,
                endMs = endMs,
                clipDurationSec = ((endMs - startMs) / 1000).toInt().coerceAtLeast(1),
                filePath = exportSuccess!!,
                contacts = contactsList,
                hasContactsPermission = hasContactsPermission,
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                },
                onShareToChat = onShareToChat,
                onDismissRequest = { showShareSheet = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareClipBottomSheet(
    songTitle: String,
    artistName: String,
    startMs: Long,
    endMs: Long,
    clipDurationSec: Int,
    filePath: String,
    contacts: List<ClipContact>,
    hasContactsPermission: Boolean,
    onRequestPermission: () -> Unit,
    onShareToChat: ((peerId: String, peerTitle: String, clipTitle: String, artist: String, startMs: Long, endMs: Long, filePath: String) -> Unit)? = null,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val nidoContacts = remember(contacts) {
        contacts.filter { it.hasNido }
    }

    val filteredContacts = remember(nidoContacts, searchQuery) {
        if (searchQuery.isBlank()) nidoContacts
        else nidoContacts.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.phoneNumber.contains(searchQuery) ||
            (it.username?.contains(searchQuery, ignoreCase = true) == true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Share to Nido Contacts",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$songTitle • ${clipDurationSec}s",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search Nido contacts or @username...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Custom search handle option if query doesn't match
            val cleanQuery = searchQuery.trim().removePrefix("@")
            if (cleanQuery.isNotEmpty() && filteredContacts.none { it.username?.equals("@$cleanQuery", ignoreCase = true) == true }) {
                Surface(
                    onClick = {
                        if (onShareToChat != null) {
                            onShareToChat("peer_${cleanQuery.lowercase()}", "@$cleanQuery", songTitle, artistName, startMs, endMs, filePath)
                        } else {
                            shareClipToContact(context, filePath, ClipContact("@$cleanQuery", "", true, "@$cleanQuery"), songTitle)
                        }
                        onDismissRequest()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Share to @$cleanQuery",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Send clip in Nido chat",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Contacts List
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredContacts, key = { it.name + it.phoneNumber }) { contact ->
                    Surface(
                        onClick = {
                            if (onShareToChat != null) {
                                val cleanPeerId = if (contact.username != null) {
                                    "peer_${contact.username.removePrefix("@").lowercase().replace(" ", "_")}"
                                } else {
                                    "peer_${contact.name.lowercase().replace(" ", "_")}"
                                }
                                val peerTitle = contact.username ?: contact.name
                                onShareToChat(cleanPeerId, peerTitle, songTitle, artistName, startMs, endMs, filePath)
                            } else {
                                shareClipToContact(context, filePath, contact, songTitle)
                            }
                            onDismissRequest()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = contact.name.take(2).uppercase(),
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }

                            Spacer(modifier = Modifier.size(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = contact.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (contact.hasNido) {
                                        Spacer(modifier = Modifier.size(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                                .padding(horizontal = 5.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Nido",
                                                color = MaterialTheme.colorScheme.primary,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = contact.username ?: contact.phoneNumber,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                if (filteredContacts.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No contacts found",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

private fun shareClipToContact(context: Context, filePath: String, contact: ClipContact, songTitle: String) {
    try {
        val file = File(filePath)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Listen to this audio clip of '$songTitle' on Nido!")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share with ${contact.name}"))
    } catch (_: Exception) {}
}

private fun shareClipSystemChooser(context: Context, filePath: String, songTitle: String) {
    try {
        val file = File(filePath)
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Listen to this audio clip of '$songTitle' on Nido!")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share Clip"))
    } catch (_: Exception) {}
}

fun queryClipContacts(context: Context): List<ClipContact> {
    val contacts = mutableListOf<ClipContact>()

    // Core Nido users & known profiles
    contacts.add(ClipContact("User 1", "+1 555-0101", hasNido = true, username = "@user1"))
    contacts.add(ClipContact("User 2", "+1 555-0102", hasNido = true, username = "@user2"))
    contacts.add(ClipContact("Bob Marley", "+1 555-0103", hasNido = true, username = "@bob"))
    contacts.add(ClipContact("Alice Cooper", "+1 555-0104", hasNido = true, username = "@alice"))
    contacts.add(ClipContact("Elena Rostova", "+1 555-0105", hasNido = true, username = "@elena"))
    contacts.add(ClipContact("Nido Support", "+1 555-0100", hasNido = true, username = "@nido_support"))

    val hasPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_CONTACTS
    ) == PackageManager.PERMISSION_GRANTED

    if (hasPermission) {
        try {
            val cr = context.contentResolver
            val cursor = cr.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )
            val seen = mutableSetOf<String>()
            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = it.getString(nameIdx) ?: continue
                    val rawNumber = it.getString(numIdx) ?: continue
                    val clean = rawNumber.replace("[^0-9+]".toRegex(), "")
                    if (clean.isNotEmpty() && seen.add(clean)) {
                        val onNido = clean.endsWith("777") ||
                                name.contains("Nido", ignoreCase = true) ||
                                name.contains("User 1", ignoreCase = true) ||
                                name.contains("User 2", ignoreCase = true)
                        if (onNido) {
                            contacts.add(
                                ClipContact(
                                    name = name,
                                    phoneNumber = rawNumber,
                                    hasNido = true,
                                    username = "@${name.lowercase().replace(" ", "_")}"
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    return contacts.filter { it.hasNido }.distinctBy { it.username ?: it.name }
}

private fun formatDuration(ms: Long): String {
    val totalSecs = (ms / 1000).coerceAtLeast(0)
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return String.format("%d:%02d", mins, secs)
}
