package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    alpha: Float = 0.45f,
    cornerRadius: Int = 16,
    contentPadding: Dp = 12.dp, // Настраиваемый отступ
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(cornerRadius.dp)).blur(radius = 20.dp).background(Color.White.copy(alpha = alpha)))
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(width = 1.2.dp, brush = Brush.linearGradient(colors = listOf(Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.15f))), shape = RoundedCornerShape(cornerRadius.dp))
                .padding(contentPadding) // Применяем отступ
        ) {
            content()
        }
    }
}
