package ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.pointer.isTertiaryPressed


fun Modifier.checkerboard(
    cellSize: Dp = 10.dp,
    colorLight: Color = Color(0xFFBFBFBF),
    colorDark: Color = Color(0xFF8F8F8F)
): Modifier = drawBehind {
    val cellPx = cellSize.toPx()
    if (cellPx <= 0f) return@drawBehind
    var row = 0
    var y = 0f
    while (y < size.height) {
        var col = 0
        var x = 0f
        while (x < size.width) {
            val color = if ((row + col) % 2 == 0) colorLight else colorDark
            drawRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(minOf(cellPx, size.width - x), minOf(cellPx, size.height - y))
            )
            col++
            x += cellPx
        }
        row++
        y += cellPx
    }
}

@Composable
fun GlassViewport(
    loadedImage: ImageBitmap? = null,
    infoLabel: String? = null,
    modifier: Modifier = Modifier,
    cameraResetKey: Any? = null,
    content: (@Composable () -> Unit)? = null
) {
    val camera = rememberViewportCameraState(cameraResetKey)

    Box(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).blur(20.dp).background(Color.White.copy(alpha = 0.45f)))
        Box(
            modifier = Modifier.fillMaxSize().border(1.2.dp, androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color.White.copy(0.8f), Color.White.copy(0.15f))), RoundedCornerShape(24.dp)).padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (loadedImage != null || content != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                var dragging = false
                                var lastPosition = Offset.Zero
                                while (true) {
                                    val event = awaitPointerEvent()
                                    when (event.type) {
                                        PointerEventType.Press -> {
                                            // Используем свойство БЕЗ скобок и аргументов
                                            if (event.buttons.isTertiaryPressed) {
                                                dragging = true
                                                lastPosition = event.changes.first().position
                                            }
                                        }
                                        PointerEventType.Move -> {
                                            if (dragging) {
                                                val change = event.changes.first()
                                                val delta = change.position - lastPosition
                                                lastPosition = change.position
                                                camera.panX += delta.x
                                                camera.panY += delta.y
                                                change.consume()
                                            }
                                        }
                                        PointerEventType.Release -> {
                                            // Используем свойство БЕЗ скобок и аргументов
                                            if (!event.buttons.isTertiaryPressed) dragging = false
                                        }
                                        else -> Unit
                                    }
                                }
                            }
                        }
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .checkerboard()
                            .graphicsLayer(
                                scaleX = camera.zoom,
                                scaleY = camera.zoom,
                                translationX = camera.panX,
                                translationY = camera.panY
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (content != null) {
                            content()
                        } else if (loadedImage != null) {
                            Image(
                                bitmap = loadedImage,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                if (infoLabel != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(24.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = infoLabel,
//                            text = "hello\nThis is a placeholder",
                            color = Color.White,
                            fontSize = 11.sp
                        )
                    }
                }
            } else {
                Text(
                    text = "Hello!\n\nThis is SC Editor, an app to view Supercell graphic files format.",
                    color = Color(0xFF94A3B8),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}