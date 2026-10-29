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
    primary = NidoPrimary,
    onPrimary = NidoOnPrimary,
    primaryContainer = NidoPrimaryDark,
    secondary = NidoSecondary,
    onSecondary = NidoOnSecondary,
    secondaryContainer = NidoSecondaryDark,
    tertiary = NidoTertiary,
    onTertiary = NidoOnTertiary,
    surface = NidoSurfaceDark,
    surfaceVariant = NidoSurfaceVariantDark,
    onSurface = NidoOnSurfaceDark,
    onSurfaceVariant = NidoOnSurfaceVariantDark,
    outline = NidoOutlineDark,
    background = NidoBackgroundDark,
    error = NidoError,
    onError = NidoOnError
)

private val NidoLightColorScheme = lightColorScheme(
    primary = NidoPrimaryDark,
    onPrimary = NidoOnSecondary,
    primaryContainer = NidoPrimaryLight,
    secondary = NidoSecondary,
    onSecondary = NidoOnSecondary,
    secondaryContainer = NidoSecondaryDark,
    tertiary = NidoTertiary,
    onTertiary = NidoOnTertiary,
    surface = NidoSurfaceLight,
    surfaceVariant = NidoSurfaceVariantLight,
    onSurface = NidoOnSurfaceLight,
    onSurfaceVariant = NidoOnSurfaceVariantLight,
    outline = NidoOutlineLight,
    background = NidoBackgroundLight,
    error = NidoError,
    onError = NidoOnError
)

@Composable
fun NidoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Dynamic color available on Android 12+
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> NidoDarkColorScheme
        else -> NidoLightColorScheme
    }

    val glassTints = if (darkTheme) {
        GlassTints(
            surface = NidoGlassLevel1,
            elevated = NidoGlassLevel2,
            accent = NidoAmberAccent
        )
    } else {
        GlassTints(
            surface = Color.White.copy(alpha = 0.45f),
            elevated = Color.White.copy(alpha = 0.65f),
            accent = NidoAmberAccent
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
