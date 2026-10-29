package com.nido.music.ui.glass

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.nido.music.ui.theme.GlassLevel
import com.nido.music.ui.theme.GlassLevelConfig
import com.nido.music.ui.theme.NidoEdgeSpecular

@Composable
fun GlassCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    level: GlassLevelConfig = GlassLevel.Level1,
    shape: Shape = RoundedCornerShape(24.dp),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    blur(level.blurRadius.toPx())
                },
                onDrawSurface = {
                    drawRect(level.surfaceColor)
                }
            )
            .border(1.dp, NidoEdgeSpecular, shape)
    ) {
        content()
    }
}
