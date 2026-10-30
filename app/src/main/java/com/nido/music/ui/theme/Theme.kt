package com.nido.music.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

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

private val NidoLightColorScheme = lightColorScheme(
    primary = NidoAmberAccent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF78350F),
    secondary = NidoSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEDD5),
    tertiary = NidoTertiary,
    onTertiary = Color.White,
    surface = Color.White.copy(alpha = 0.82f),
    surfaceVariant = Color(0xFFE2E8F0),
    onSurface = Color(0xFF0F172A),
    onSurfaceVariant = Color(0xBF0F172A),
    outline = Color(0x1F000000),
    background = Color(0xFFF3F4F6),
    onBackground = Color(0xFF0F172A),
    error = NidoError,
    onError = NidoOnError
)

private val NidoOledColorScheme = darkColorScheme(
    primary = NidoAmberAccent,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFFB45309),
    secondary = NidoSecondary,
    onSecondary = Color.Black,
    surface = Color(0xFF050505),
    surfaceVariant = Color(0xFF111111),
    onSurface = Color.White,
    onSurfaceVariant = Color(0xB3FFFFFF),
    outline = Color(0x33FFFFFF),
    background = Color.Black,
    onBackground = Color.White,
    error = NidoError,
    onError = NidoOnError
)

@Composable
fun NidoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isOled: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        isOled -> NidoOledColorScheme
        darkTheme -> NidoDarkColorScheme
        else -> NidoLightColorScheme
    }

    val glassTints = when {
        isOled -> GlassTints(
            surface = Color(0xFF000000).copy(alpha = 0.88f),
            elevated = Color(0xFF101010).copy(alpha = 0.96f),
            accent = NidoAmberAccent,
            canvasFloor = Color.Black,
            edgeSpecular = Color(0x33FFFFFF),
            textPrimary = Color.White,
            textSecondary = Color(0xB3FFFFFF),
            textTertiary = Color(0x66FFFFFF)
        )
        !darkTheme -> GlassTints(
            surface = Color.White.copy(alpha = 0.70f),
            elevated = Color.White.copy(alpha = 0.88f),
            accent = NidoAmberAccent,
            canvasFloor = Color(0xFFF3F4F6),
            edgeSpecular = Color(0x1F000000),
            textPrimary = Color(0xFF0F172A),
            textSecondary = Color(0xBF0F172A),
            textTertiary = Color(0x660F172A)
        )
        else -> GlassTints(
            surface = NidoGlassLevel1,
            elevated = NidoGlassLevel2,
            accent = NidoAmberAccent,
            canvasFloor = NidoCanvasFloor,
            edgeSpecular = NidoEdgeSpecular,
            textPrimary = NidoTextPrimary,
            textSecondary = NidoTextSecondary,
            textTertiary = NidoTextTertiary
        )
    }

    CompositionLocalProvider(LocalGlassTints provides glassTints) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = NidoTypography,
            content = content
        )
    }
}
