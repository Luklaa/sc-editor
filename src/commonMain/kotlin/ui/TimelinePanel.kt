package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun GlassTimelinePanel(
    frameCount: Int,
    currentFrame: Int,
    isPlaying: Boolean,
    onFrameChange: (Int) -> Unit,
    onTogglePlaying: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lastFrameIndex = (frameCount - 1).coerceAtLeast(0)

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

            Box(
                modifier = Modifier
                    .size(60.dp, 35.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.08f))
                    .border(1.dp, Color.Black.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                    .clickable(enabled = frameCount > 1) { onTogglePlaying() },
                contentAlignment = Alignment.Center
            ) {
                Text(text = if (isPlaying) "Stop" else "Play", color = Color(0xFF1E293B), fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Slider(
                value = currentFrame.toFloat(),
                onValueChange = { onFrameChange(it.toInt()) },
                valueRange = 0f..lastFrameIndex.toFloat(),
                steps = (lastFrameIndex - 1).coerceAtLeast(0),
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = "Кадр: ${currentFrame + 1} / $frameCount",
                color = Color(0xFF1E293B),
                fontSize = 12.sp
            )
        }
    }
}
