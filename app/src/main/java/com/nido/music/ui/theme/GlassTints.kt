package com.nido.music.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class GlassTints(
    val surface: Color,
    val elevated: Color,
    val accent: Color
)

val LocalGlassTints = compositionLocalOf {
    GlassTints(
        surface = NidoGlassLevel1,
        elevated = NidoGlassLevel2,
        accent = NidoAmberAccent
    )
}

data class GlassLevelConfig(
    val surfaceColor: Color,
    val blurRadius: Dp,
    val lensInner: Dp,
    val lensOuter: Dp,
    val useVibrancy: Boolean
)

object GlassLevel {
    val Level1 = GlassLevelConfig(
        surfaceColor = NidoGlassLevel1,
        blurRadius = 12.dp,
        lensInner = 8.dp,
        lensOuter = 16.dp,
        useVibrancy = true
    )
    val Level2 = GlassLevelConfig(
        surfaceColor = NidoGlassLevel2,
        blurRadius = 20.dp,
        lensInner = 12.dp,
        lensOuter = 24.dp,
        useVibrancy = true
    )
    val Level3 = GlassLevelConfig(
        surfaceColor = NidoGlassLevel3,
        blurRadius = 28.dp,
        lensInner = 16.dp,
        lensOuter = 32.dp,
        useVibrancy = true
    )
}

