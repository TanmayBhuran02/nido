package com.nido.music.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.nido.music.core.common.ThemeMode

private val NidoDarkColorScheme = darkColorScheme(
    primary = NidoAmberAccent,
    onPrimary = NidoOnPrimary,
    primaryContainer = NidoPrimaryDark,
    secondary = NidoSecondary,
    onSecondary = NidoOnSecondary,
    secondaryContainer = NidoSecondaryDark,
    tertiary = NidoTertiary,
    onTertiary = NidoOnTertiary,
    surface = NidoSurfaceDark,
    surfaceVariant = NidoSurfaceVariantDark,
    onSurface = NidoTextPrimary,
    onSurfaceVariant = NidoTextSecondary,
    outline = NidoOutlineDark,
    background = NidoCanvasFloor,
    onBackground = NidoTextPrimary,
    error = NidoError,
    onError = NidoOnError
)

private val NidoDarkGlassTints = GlassTints(
    surface = NidoGlassLevel1,
    elevated = NidoGlassLevel2,
    accent = NidoAmberAccent,
    canvasFloor = NidoCanvasFloor,
    edgeSpecular = NidoEdgeSpecular,
    textPrimary = NidoTextPrimary,
    textSecondary = NidoTextSecondary,
    textTertiary = NidoTextTertiary,
    accentGlow = NidoAmberGlow
)

private val NidoLightColorScheme = lightColorScheme(
    primary = GlassLightAmberAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF78350F),
    secondary = Color(0xFFEA580C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEDD5),
    tertiary = Color(0xFF0D9488),
    onTertiary = Color.White,
    surface = GlassLightLevel1,
    surfaceVariant = GlassLightLevel2,
    onSurface = GlassLightTextPrimary,
    onSurfaceVariant = GlassLightTextSecondary,
    outline = GlassLightEdgeSpecular,
    background = GlassLightCanvasFloor,
    onBackground = GlassLightTextPrimary,
    error = NidoError,
    onError = NidoOnError
)

private val NidoLightGlassTints = GlassTints(
    surface = GlassLightLevel1,
    elevated = GlassLightLevel2,
    accent = GlassLightAmberAccent,
    canvasFloor = GlassLightCanvasFloor,
    edgeSpecular = GlassLightEdgeSpecular,
    textPrimary = GlassLightTextPrimary,
    textSecondary = GlassLightTextSecondary,
    textTertiary = GlassLightTextTertiary,
    accentGlow = GlassLightAmberGlow
)

private val NidoOledColorScheme = darkColorScheme(
    primary = NidoAmberAccent,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFFB45309),
    secondary = NidoSecondary,
    onSecondary = Color.Black,
    surface = Color(0xFF0A0A0A),
    surfaceVariant = Color(0xFF141414),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xCCFFFFFF),
    outline = Color(0x33FFFFFF),
    background = Color.Black,
    onBackground = Color.White,
    error = NidoError,
    onError = NidoOnError
)

private val NidoOledGlassTints = GlassTints(
    surface = Color(0xFF080808).copy(alpha = 0.92f),
    elevated = Color(0xFF141414).copy(alpha = 0.96f),
    accent = NidoAmberAccent,
    canvasFloor = Color.Black,
    edgeSpecular = Color(0x33FFFFFF),
    textPrimary = Color.White,
    textSecondary = Color(0xCCFFFFFF),
    textTertiary = Color(0x70FFFFFF),
    accentGlow = NidoAmberGlow
)

private val NidoEmeraldColorScheme = darkColorScheme(
    primary = EmeraldAccent,
    onPrimary = Color(0xFF022C22),
    primaryContainer = Color(0xFF065F46),
    secondary = Color(0xFF34D399),
    onSecondary = Color(0xFF022C22),
    tertiary = Color(0xFF6EE7B7),
    onTertiary = Color(0xFF022C22),
    surface = EmeraldGlassLevel1,
    surfaceVariant = EmeraldGlassLevel2,
    onSurface = EmeraldTextPrimary,
    onSurfaceVariant = EmeraldTextSecondary,
    outline = EmeraldEdgeSpecular,
    background = EmeraldCanvasFloor,
    onBackground = EmeraldTextPrimary,
    error = NidoError,
    onError = NidoOnError
)

private val NidoEmeraldGlassTints = GlassTints(
    surface = EmeraldGlassLevel1,
    elevated = EmeraldGlassLevel2,
    accent = EmeraldAccent,
    canvasFloor = EmeraldCanvasFloor,
    edgeSpecular = EmeraldEdgeSpecular,
    textPrimary = EmeraldTextPrimary,
    textSecondary = EmeraldTextSecondary,
    textTertiary = EmeraldTextTertiary,
    accentGlow = EmeraldAccent.copy(alpha = 0.28f)
)

private val NidoCyberpunkColorScheme = darkColorScheme(
    primary = CyberpunkAccent,
    onPrimary = Color(0xFF083344),
    primaryContainer = Color(0xFF0E7490),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF083344),
    tertiary = Color(0xFF818CF8),
    onTertiary = Color(0xFF1E1B4B),
    surface = CyberpunkGlassLevel1,
    surfaceVariant = CyberpunkGlassLevel2,
    onSurface = CyberpunkTextPrimary,
    onSurfaceVariant = CyberpunkTextSecondary,
    outline = CyberpunkEdgeSpecular,
    background = CyberpunkCanvasFloor,
    onBackground = CyberpunkTextPrimary,
    error = NidoError,
    onError = NidoOnError
)

private val NidoCyberpunkGlassTints = GlassTints(
    surface = CyberpunkGlassLevel1,
    elevated = CyberpunkGlassLevel2,
    accent = CyberpunkAccent,
    canvasFloor = CyberpunkCanvasFloor,
    edgeSpecular = CyberpunkEdgeSpecular,
    textPrimary = CyberpunkTextPrimary,
    textSecondary = CyberpunkTextSecondary,
    textTertiary = CyberpunkTextTertiary,
    accentGlow = CyberpunkAccent.copy(alpha = 0.28f)
)

private val NidoSunsetColorScheme = darkColorScheme(
    primary = SunsetAccent,
    onPrimary = Color(0xFF4C0519),
    primaryContainer = Color(0xFF9F1239),
    secondary = Color(0xFFFB7185),
    onSecondary = Color(0xFF4C0519),
    tertiary = Color(0xFFFDA4AF),
    onTertiary = Color(0xFF4C0519),
    surface = SunsetGlassLevel1,
    surfaceVariant = SunsetGlassLevel2,
    onSurface = SunsetTextPrimary,
    onSurfaceVariant = SunsetTextSecondary,
    outline = SunsetEdgeSpecular,
    background = SunsetCanvasFloor,
    onBackground = SunsetTextPrimary,
    error = NidoError,
    onError = NidoOnError
)

private val NidoSunsetGlassTints = GlassTints(
    surface = SunsetGlassLevel1,
    elevated = SunsetGlassLevel2,
    accent = SunsetAccent,
    canvasFloor = SunsetCanvasFloor,
    edgeSpecular = SunsetEdgeSpecular,
    textPrimary = SunsetTextPrimary,
    textSecondary = SunsetTextSecondary,
    textTertiary = SunsetTextTertiary,
    accentGlow = SunsetAccent.copy(alpha = 0.28f)
)

private val NidoAmethystColorScheme = darkColorScheme(
    primary = AmethystAccent,
    onPrimary = Color(0xFF3B0764),
    primaryContainer = Color(0xFF6B21A8),
    secondary = Color(0xFFC084FC),
    onSecondary = Color(0xFF3B0764),
    tertiary = Color(0xFFE9D5FF),
    onTertiary = Color(0xFF3B0764),
    surface = AmethystGlassLevel1,
    surfaceVariant = AmethystGlassLevel2,
    onSurface = AmethystTextPrimary,
    onSurfaceVariant = AmethystTextSecondary,
    outline = AmethystEdgeSpecular,
    background = AmethystCanvasFloor,
    onBackground = AmethystTextPrimary,
    error = NidoError,
    onError = NidoOnError
)

private val NidoAmethystGlassTints = GlassTints(
    surface = AmethystGlassLevel1,
    elevated = AmethystGlassLevel2,
    accent = AmethystAccent,
    canvasFloor = AmethystCanvasFloor,
    edgeSpecular = AmethystEdgeSpecular,
    textPrimary = AmethystTextPrimary,
    textSecondary = AmethystTextSecondary,
    textTertiary = AmethystTextTertiary,
    accentGlow = AmethystAccent.copy(alpha = 0.28f)
)

@Composable
fun NidoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isOled: Boolean = false,
    themeMode: ThemeMode? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val activeMode = when {
        themeMode != null && themeMode != ThemeMode.SYSTEM -> themeMode
        isOled -> ThemeMode.OLED
        darkTheme -> ThemeMode.DARK
        else -> ThemeMode.LIGHT
    }

    val (colorScheme, glassTints) = when (activeMode) {
        ThemeMode.OLED -> NidoOledColorScheme to NidoOledGlassTints
        ThemeMode.LIGHT -> NidoLightColorScheme to NidoLightGlassTints
        ThemeMode.EMERALD -> NidoEmeraldColorScheme to NidoEmeraldGlassTints
        ThemeMode.CYBERPUNK -> NidoCyberpunkColorScheme to NidoCyberpunkGlassTints
        ThemeMode.SUNSET -> NidoSunsetColorScheme to NidoSunsetGlassTints
        ThemeMode.AMETHYST -> NidoAmethystColorScheme to NidoAmethystGlassTints
        else -> NidoDarkColorScheme to NidoDarkGlassTints
    }

    CompositionLocalProvider(LocalGlassTints provides glassTints) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NidoTypography,
            content = content
        )
    }
}
