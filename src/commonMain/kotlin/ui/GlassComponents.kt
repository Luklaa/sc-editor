package ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Blur дорогой: в выдвижном меню (оно поверх плотной подложки) его отключаем. */
val LocalGlassBlur = compositionLocalOf { true }

@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    alpha: Float = 0.45f,
    cornerRadius: Int = 16,
    topStart: Dp = cornerRadius.dp,
    topEnd: Dp = cornerRadius.dp,
    bottomStart: Dp = cornerRadius.dp,
    bottomEnd: Dp = cornerRadius.dp,
    contentPadding: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(topStart = topStart, topEnd = topEnd, bottomStart = bottomStart, bottomEnd = bottomEnd)
    val blurEnabled = LocalGlassBlur.current
    Box(modifier = modifier) {
        Box(
            modifier = Modifier.fillMaxSize().clip(shape)
                .then(if (blurEnabled) Modifier.blur(radius = 20.dp) else Modifier)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = alpha))
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(width = 1.2.dp, brush = Brush.linearGradient(colors = listOf(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), MaterialTheme.colorScheme.surface.copy(alpha = 0.15f))), shape = shape)
                .padding(contentPadding)
        ) {
            content()
        }
    }
}