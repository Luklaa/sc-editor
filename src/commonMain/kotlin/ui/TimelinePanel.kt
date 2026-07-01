package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.donutquine.editor.renderer.impl.swf.objects.MovieClipController

@Composable
fun GlassTimelinePanel(
    controller: MovieClipController, // Добавляем этот параметр
    modifier: Modifier = Modifier
) {
    var currentFrame by remember { mutableStateOf(0f) }
    var isPlaying by remember { mutableStateOf(false) }

    GlassBox(
        modifier = modifier.height(70.dp),
        alpha = 0.5f,
        cornerRadius = 14,
        contentPadding = 8.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)
        ) {
            // Кнопка Play/Stop [1]
            Box(
                modifier = Modifier
                    .size(60.dp, 35.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.08f))
                    .border(1.dp, Color.Black.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .clickable { isPlaying = !isPlaying },
                contentAlignment = Alignment.Center
            ) {
                Text(text = if (isPlaying) "Stop" else "Play", color = Color(0xFF1E293B), fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Ползунок кадров (Слайдер) [1]
            Slider(
                value = currentFrame,
                onValueChange = { currentFrame = it },
                valueRange = 0f..100f,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Счетчик кадров [1]
            Text(
                text = "Кадр: ${currentFrame.toInt()} / 100",
                color = Color(0xFF1E293B),
                fontSize = 12.sp
            )
        }
    }
}
