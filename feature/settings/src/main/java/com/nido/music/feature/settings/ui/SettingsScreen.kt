package com.nido.music.feature.settings.ui

import android.content.Intent
import android.media.audiofx.AudioEffect
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur

private val AmberPrimary = Color(0xFFF59E0B)
private val CanvasFloor = Color(0xFF0B0B0E)
private val GlassCardBg = Color(0x7314141A)
private val EdgeSpecular = Color(0x24FFFFFF)
private val TextPrimary = Color(0xFFF8FAFC)
private val TextSecondary = Color(0xA6F8FAFC)

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val folders by viewModel.folders.collectAsState()
    val rescanTriggered by viewModel.rescanTriggered.collectAsState()
    val context = LocalContext.current

    var showSleepTimerInfoDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var selectedThemeMode by remember { mutableStateOf("System Default") }

    val settingsBackdrop = rememberLayerBackdrop()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CanvasFloor)
    ) {
        // Sampled layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(settingsBackdrop)
        ) {
            // Ambient orbs
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 60.dp)
                    .blur(90.dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                AmberPrimary.copy(alpha = 0.2f),
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
                    .verticalScroll(rememberScrollState())
                    .padding(
                        top = statusBarHeight + 64.dp,
                        bottom = 120.dp,
                        start = 16.dp,
                        end = 16.dp
                    )
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Playback Group
                SettingsGlassGroup(
                    backdrop = settingsBackdrop,
                    title = "Playback"
                ) {
                    SettingsRow(
                        icon = Icons.Default.Equalizer,
                        title = "Equalizer & Audio FX",
                        subtitle = "Launch system audio DSP",
                        onClick = {
                            val eqIntent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                            }
                            if (eqIntent.resolveActivity(context.packageManager) != null) {
                                context.startActivity(eqIntent)
                            } else {
                                Toast.makeText(context, "No system equalizer app found on this device", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsRow(
                        icon = Icons.Default.Timer,
                        title = "Sleep Timer",
                        subtitle = "Quick-set 15/30/45/60 min in Player screen",
                        onClick = { showSleepTimerInfoDialog = true }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Storage Group
                SettingsGlassGroup(
                    backdrop = settingsBackdrop,
                    title = "Library & Storage (${folders.size} folders)"
                ) {
                    SettingsRow(
                        icon = Icons.Default.Refresh,
                        title = "Rescan All Folders",
                        subtitle = if (rescanTriggered) "Scan scheduled in background" else "Incremental scan by mtime",
                        onClick = { viewModel.triggerRescan() }
                    )
                    folders.forEach { f ->
                        Spacer(modifier = Modifier.height(12.dp))
                        SettingsRow(
                            icon = Icons.Default.SdCard,
                            title = f.displayName,
                            subtitle = f.treeUri,
                            onClick = { viewModel.removeFolder(f.treeUri) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Appearance Group
                SettingsGlassGroup(
                    backdrop = settingsBackdrop,
                    title = "Appearance"
                ) {
                    SettingsRow(
                        icon = Icons.Default.Palette,
                        title = "Liquid Glass Theme",
                        subtitle = selectedThemeMode,
                        onClick = { showThemeDialog = true }
                    )
                }
            }

        // Pinned Top Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(statusBarHeight + 56.dp)
                .drawBackdrop(
                    backdrop = settingsBackdrop,
                    shape = { RoundedCornerShape(0.dp) },
                    effects = {
                        blur(20.dp.toPx())
                    },
                    onDrawSurface = {
                        drawRect(Color(0xA6181820))
                    }
                )
                .border(0.5.dp, EdgeSpecular, RoundedCornerShape(0.dp))
                .padding(top = statusBarHeight, start = 20.dp, end = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "Settings",
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (showSleepTimerInfoDialog) {
            AlertDialog(
                onDismissRequest = { showSleepTimerInfoDialog = false },
                title = { Text("Sleep Timer") },
                text = {
                    Text(
                        "The sleep timer is accessible directly inside the full-screen Player view. Tap the timer icon to configure 15, 30, 45, or 60 minute auto fade-out.",
                        color = TextSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showSleepTimerInfoDialog = false }) {
                        Text("Understood", color = AmberPrimary)
                    }
                }
            )
        }

        if (showThemeDialog) {
            val themes = listOf("System Default", "Liquid Glass (Dark)", "OLED Black")
            AlertDialog(
                onDismissRequest = { showThemeDialog = false },
                title = { Text("Appearance Theme") },
                text = {
                    Column {
                        themes.forEach { theme ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedThemeMode = theme
                                        showThemeDialog = false
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                RadioButton(
                                    selected = selectedThemeMode == theme,
                                    onClick = {
                                        selectedThemeMode = theme
                                        showThemeDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = AmberPrimary)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(theme, color = TextPrimary)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showThemeDialog = false }) {
                        Text("Close", color = AmberPrimary)
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsGlassGroup(
    backdrop: Backdrop,
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            color = AmberPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(24.dp) },
                    effects = {
                        blur(20.dp.toPx())
                    },
                    onDrawSurface = {
                        drawRect(GlassCardBg)
                    }
                )
                .border(1.dp, EdgeSpecular, RoundedCornerShape(24.dp))
                .padding(16.dp)
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AmberPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}
