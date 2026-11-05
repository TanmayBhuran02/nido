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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val folders by viewModel.folders.collectAsState()
    val rescanTriggered by viewModel.rescanTriggered.collectAsState()
    val currentThemeMode by viewModel.themeMode.collectAsState()
    val selectedThemeMode = currentThemeMode.title
    val context = LocalContext.current

    var showSleepTimerInfoDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    val isLight = currentThemeMode == com.nido.music.core.common.ThemeMode.LIGHT
    val isOled = currentThemeMode == com.nido.music.core.common.ThemeMode.OLED

    val colorScheme = androidx.compose.material3.MaterialTheme.colorScheme
    val currentCanvasFloor = colorScheme.background
    val currentGlassCardBg = colorScheme.surface
    val currentEdgeSpecular = colorScheme.outline
    val currentTextPrimary = colorScheme.onSurface
    val currentTextSecondary = colorScheme.onSurfaceVariant
    val currentHeaderColor = colorScheme.primary

    val settingsBackdrop = rememberLayerBackdrop()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(currentCanvasFloor)
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
                    title = "Playback",
                    titleColor = currentHeaderColor,
                    glassCardBg = currentGlassCardBg,
                    edgeSpecular = currentEdgeSpecular
                ) {
                    SettingsRow(
                        icon = Icons.Default.Equalizer,
                        title = "Equalizer & Audio FX",
                        subtitle = "Launch system audio DSP",
                        textPrimary = currentTextPrimary,
                        textSecondary = currentTextSecondary,
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
                        textPrimary = currentTextPrimary,
                        textSecondary = currentTextSecondary,
                        onClick = { showSleepTimerInfoDialog = true }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Storage Group
                SettingsGlassGroup(
                    backdrop = settingsBackdrop,
                    title = "Library & Storage (${folders.size} folders)",
                    titleColor = currentHeaderColor,
                    glassCardBg = currentGlassCardBg,
                    edgeSpecular = currentEdgeSpecular
                ) {
                    SettingsRow(
                        icon = Icons.Default.Refresh,
                        title = "Rescan All Folders",
                        subtitle = if (rescanTriggered) "Scan scheduled in background" else "Incremental scan by mtime",
                        textPrimary = currentTextPrimary,
                        textSecondary = currentTextSecondary,
                        onClick = { viewModel.triggerRescan() }
                    )
                    folders.forEach { f ->
                        Spacer(modifier = Modifier.height(12.dp))
                        SettingsRow(
                            icon = Icons.Default.SdCard,
                            title = f.displayName,
                            subtitle = f.treeUri,
                            textPrimary = currentTextPrimary,
                            textSecondary = currentTextSecondary,
                            onClick = { viewModel.removeFolder(f.treeUri) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Appearance Group
                SettingsGlassGroup(
                    backdrop = settingsBackdrop,
                    title = "Appearance",
                    titleColor = currentHeaderColor,
                    glassCardBg = currentGlassCardBg,
                    edgeSpecular = currentEdgeSpecular
                ) {
                    SettingsRow(
                        icon = Icons.Default.Palette,
                        title = "Liquid Glass Theme",
                        subtitle = selectedThemeMode,
                        textPrimary = currentTextPrimary,
                        textSecondary = currentTextSecondary,
                        onClick = { showThemeDialog = true }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Privacy & Security (E2EE) Group
                SettingsGlassGroup(
                    backdrop = settingsBackdrop,
                    title = "Privacy & Security (E2EE)",
                    titleColor = currentHeaderColor,
                    glassCardBg = currentGlassCardBg,
                    edgeSpecular = currentEdgeSpecular
                ) {
                    var readReceipts by remember { mutableStateOf(true) }
                    var lastSeenPrivacy by remember { mutableStateOf(true) }
                    var showPrivacyNotice by remember { mutableStateOf(false) }

                    SettingsRow(
                        icon = Icons.Default.Lock,
                        title = "Zero-Plaintext Policy",
                        subtitle = "Server stores only ciphertext",
                        textPrimary = currentTextPrimary,
                        textSecondary = currentTextSecondary,
                        onClick = { showPrivacyNotice = true }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsRow(
                        icon = Icons.Default.Check,
                        title = "Read Receipts",
                        subtitle = if (readReceipts) "Enabled (double blue tick)" else "Disabled",
                        textPrimary = currentTextPrimary,
                        textSecondary = currentTextSecondary,
                        onClick = {
                            readReceipts = !readReceipts
                            Toast.makeText(context, "Read receipts: ${if (readReceipts) "On" else "Off"}", Toast.LENGTH_SHORT).show()
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsRow(
                        icon = Icons.Default.Visibility,
                        title = "Last Seen & Online Status",
                        subtitle = if (lastSeenPrivacy) "Visible to contacts" else "Nobody",
                        textPrimary = currentTextPrimary,
                        textSecondary = currentTextSecondary,
                        onClick = {
                            lastSeenPrivacy = !lastSeenPrivacy
                            Toast.makeText(context, "Last seen: ${if (lastSeenPrivacy) "Contacts" else "Nobody"}", Toast.LENGTH_SHORT).show()
                        }
                    )

                    if (showPrivacyNotice) {
                        AlertDialog(
                            onDismissRequest = { showPrivacyNotice = false },
                            title = { Text("Zero-Plaintext E2EE Policy") },
                            text = { Text("All 1:1 messages, group messages (Sender Keys), locations, stickers, audio clips, and files are encrypted client-side. The Nido relay server has zero access to encryption keys or plaintext.") },
                            confirmButton = {
                                TextButton(onClick = { showPrivacyNotice = false }) { Text("Understood") }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Legal & Licensing
                SettingsGlassGroup(
                    backdrop = settingsBackdrop,
                    title = "Legal & Compliance",
                    titleColor = currentHeaderColor,
                    glassCardBg = currentGlassCardBg,
                    edgeSpecular = currentEdgeSpecular
                ) {
                    var showLegalNotice by remember { mutableStateOf(false) }
                    SettingsRow(
                        icon = Icons.Default.Info,
                        title = "Content Rights & Clip Sharing Notice",
                        subtitle = "Fair use and copyright policy",
                        textPrimary = currentTextPrimary,
                        textSecondary = currentTextSecondary,
                        onClick = { showLegalNotice = true }
                    )

                    if (showLegalNotice) {
                        AlertDialog(
                            onDismissRequest = { showLegalNotice = false },
                            title = { Text("Content Rights Policy") },
                            text = { Text("Downloads, audio trimming, and clip sharing must utilize content you own or have explicit rights/licenses to distribute. Discover tracks feature open Creative Commons and Jamendo licenses.") },
                            confirmButton = {
                                TextButton(onClick = { showLegalNotice = false }) { Text("Close") }
                            }
                        )
                    }
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
                        drawRect(if (isLight) Color.White.copy(alpha = 0.88f) else Color(0xA6181820))
                    }
                )
                .border(0.5.dp, currentEdgeSpecular, RoundedCornerShape(0.dp))
                .padding(top = statusBarHeight, start = 20.dp, end = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "Settings",
                color = currentTextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (showSleepTimerInfoDialog) {
            AlertDialog(
                onDismissRequest = { showSleepTimerInfoDialog = false },
                title = { Text("Sleep Timer", color = currentTextPrimary) },
                text = {
                    Text(
                        "The sleep timer is accessible directly inside the full-screen Player view. Tap the timer icon to configure 15, 30, 45, or 60 minute auto fade-out.",
                        color = currentTextSecondary
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
            val themes = com.nido.music.core.common.ThemeMode.entries
            AlertDialog(
                onDismissRequest = { showThemeDialog = false },
                title = { Text("Appearance Theme", color = currentTextPrimary) },
                text = {
                    Column {
                        themes.forEach { theme ->
                            val themeAccent = when (theme) {
                                com.nido.music.core.common.ThemeMode.SYSTEM -> currentHeaderColor
                                com.nido.music.core.common.ThemeMode.DARK -> Color(0xFFF59E0B)
                                com.nido.music.core.common.ThemeMode.LIGHT -> Color(0xFFD97706)
                                com.nido.music.core.common.ThemeMode.OLED -> Color(0xFFFFFFFF)
                                com.nido.music.core.common.ThemeMode.EMERALD -> Color(0xFF10B981)
                                com.nido.music.core.common.ThemeMode.CYBERPUNK -> Color(0xFF06B6D4)
                                com.nido.music.core.common.ThemeMode.SUNSET -> Color(0xFFF43F5E)
                                com.nido.music.core.common.ThemeMode.AMETHYST -> Color(0xFFA855F7)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setThemeMode(theme.title)
                                        showThemeDialog = false
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                RadioButton(
                                    selected = selectedThemeMode == theme.title,
                                    onClick = {
                                        viewModel.setThemeMode(theme.title)
                                        showThemeDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(selectedColor = currentHeaderColor)
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(themeAccent, CircleShape)
                                )
                                Spacer(modifier = Modifier.size(10.dp))
                                Text(theme.title, color = currentTextPrimary)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showThemeDialog = false }) {
                        Text("Close", color = currentHeaderColor)
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
    titleColor: Color = AmberPrimary,
    glassCardBg: Color = GlassCardBg,
    edgeSpecular: Color = EdgeSpecular,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = title,
            color = titleColor,
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
                        drawRect(glassCardBg)
                    }
                )
                .border(1.dp, edgeSpecular, RoundedCornerShape(24.dp))
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
    textPrimary: Color = TextPrimary,
    textSecondary: Color = TextSecondary,
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
                color = textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                color = textSecondary,
                fontSize = 12.sp
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}
