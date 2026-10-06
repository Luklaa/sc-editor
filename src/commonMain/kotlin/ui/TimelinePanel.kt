package ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luklaaa.sceditor.ui.AppThemeColors

@Composable
fun GlassTimelinePanel(
    frameCount: Int,
    currentFrame: Int,
    isPlaying: Boolean,
    onFrameChange: (Int) -> Unit,
    onTogglePlaying: () -> Unit,
    modifier: Modifier = Modifier,
    // Показывать панель и у объекта с одним кадром (по умолчанию она скрыта).
    alwaysShow: Boolean = false
) {
    val lastFrameIndex = (frameCount - 1).coerceAtLeast(0)

    if (frameCount > 1 || alwaysShow) {
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
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .size(35.dp, 35.dp)
                            .clip(RoundedCornerShape(30.dp))
                            .background(
                                AppThemeColors.colors.accent.copy(alpha = 0.25f)
                            )
                            .border(
                                1.dp,
                                AppThemeColors.colors.accent.copy(alpha = 0.75f),
                                RoundedCornerShape(30.dp)
                            )
                            .border(
                                1.dp,
                                Color.Black.copy(alpha = 0.12f),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(enabled = frameCount > 1) {
                                onTogglePlaying()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Pause, contentDescription = "Pause", tint = MaterialTheme.colorScheme.onSurface)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(35.dp, 35.dp)
                            .clip(RoundedCornerShape(30.dp))
                            .background(Color.Black.copy(alpha = 0.08f))
                            .border(1.dp, AppThemeColors.colors.accent.copy(alpha = 0.3f), RoundedCornerShape(30.dp))
                            .clickable(enabled = frameCount > 1) {
                                onTogglePlaying()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "Frame: ${currentFrame + 1} / $frameCount",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.width(8.dp))

                Slider(
                    value = currentFrame.toFloat().coerceAtMost(lastFrameIndex.toFloat()),
                    onValueChange = { onFrameChange(it.toInt()) },
                    enabled = frameCount > 1,
                    valueRange = 0f..(
                            if (frameCount > 1) lastFrameIndex.toFloat() else 1f
                            ),
                    steps = (frameCount - 2).coerceAtLeast(0),
                    colors = SliderDefaults.colors(
                        thumbColor = AppThemeColors.colors.accent.copy(alpha = 1f),
                        activeTrackColor = AppThemeColors.colors.accent.copy(alpha = 0.75f),
                        inactiveTrackColor = AppThemeColors.colors.accent.copy(
                            alpha = 0.2f
                        )
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
