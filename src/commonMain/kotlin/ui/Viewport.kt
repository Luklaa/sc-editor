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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.roundToInt
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
import androidx.compose.ui.input.pointer.isAltPressed
import androidx.compose.ui.input.pointer.isShiftPressed

// Чувствительность пана обычным/Shift+колесом (px за один "тик" колеса). Alt+колесо (zoom)
// использует отдельный множитель 1.1f прямо в обработчике — см. GlassViewport.
private const val WHEEL_PAN_SENSITIVITY = 40f


fun Modifier.checkerboard(
    cellSize: Dp = 10.dp,
    colorLight: Color = Color(0xFFBFBFBF),
    colorDark: Color = Color(0xFF8F8F8F),
    offsetX: Float = 0f,
    offsetY: Float = 0f
): Modifier = drawWithCache {
    val cellPx = cellSize.toPx().roundToInt().coerceAtLeast(1)
    val tileSize = cellPx * 2
    val tileBitmap = ImageBitmap(tileSize, tileSize)
    val tileCanvas = androidx.compose.ui.graphics.Canvas(tileBitmap)
    val lightPaint = androidx.compose.ui.graphics.Paint().apply { color = colorLight }
    val darkPaint = androidx.compose.ui.graphics.Paint().apply { color = colorDark }
    tileCanvas.drawRect(androidx.compose.ui.geometry.Rect(0f, 0f, cellPx.toFloat(), cellPx.toFloat()), lightPaint)
    tileCanvas.drawRect(androidx.compose.ui.geometry.Rect(cellPx.toFloat(), 0f, tileSize.toFloat(), cellPx.toFloat()), darkPaint)
    tileCanvas.drawRect(androidx.compose.ui.geometry.Rect(0f, cellPx.toFloat(), cellPx.toFloat(), tileSize.toFloat()), darkPaint)
    tileCanvas.drawRect(androidx.compose.ui.geometry.Rect(cellPx.toFloat(), cellPx.toFloat(), tileSize.toFloat(), tileSize.toFloat()), lightPaint)

    val shader = androidx.compose.ui.graphics.ImageShader(
        tileBitmap,
        androidx.compose.ui.graphics.TileMode.Repeated,
        androidx.compose.ui.graphics.TileMode.Repeated
    )
    val brush = object : androidx.compose.ui.graphics.ShaderBrush() {
        override fun createShader(size: Size) = shader
    }

    onDrawBehind {
        val tileSizeF = tileSize.toFloat()
        val marginPx = tileSizeF * 4f
        val shiftX = offsetX.mod(tileSizeF)
        val shiftY = offsetY.mod(tileSizeF)
        translate(left = shiftX, top = shiftY) {
            drawRect(
                brush = brush,
                topLeft = Offset(-marginPx, -marginPx),
                size = Size(size.width + marginPx * 2f, size.height + marginPx * 2f)
            )
        }
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
                        .pointerInput(camera) {
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
                                        PointerEventType.Scroll -> {
                                            val change = event.changes.first()
                                            val delta = change.scrollDelta.y
                                            when {
                                                event.keyboardModifiers.isAltPressed -> {
                                                    val factor = if (delta < 0) 1.1f else 1f / 1.1f
                                                    val newZoom = (camera.zoom * factor).coerceIn(0.1f, 20f)
                                                    // Зум "к курсору": пересчитываем pan так, чтобы точка контента
                                                    // ПОД курсором осталась на том же месте экрана — иначе
                                                    // graphicsLayer масштабирует вокруг центра Box (transformOrigin
                                                    // по умолчанию), и при пане в сторону зум визуально "тянет"
                                                    // к центру текстуры/сцены, а не к тому, куда сейчас смотрит камера.
                                                    val ratio = newZoom / camera.zoom
                                                    val cursor = change.position
                                                    val center = Offset(size.width / 2f, size.height / 2f)
                                                    camera.panX = (cursor.x - center.x) * (1f - ratio) + ratio * camera.panX
                                                    camera.panY = (cursor.y - center.y) * (1f - ratio) + ratio * camera.panY
                                                    camera.zoom = newZoom
                                                }
                                                event.keyboardModifiers.isShiftPressed -> {
                                                    // Shift + колесо — горизонтальный пан.
                                                    camera.panX -= delta * WHEEL_PAN_SENSITIVITY
                                                }
                                                else -> {
                                                    // Просто колесо — вертикальный пан.
                                                    camera.panY -= delta * WHEEL_PAN_SENSITIVITY
                                                }
                                            }
                                            change.consume()
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
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .checkerboard(offsetX = camera.panX, offsetY = camera.panY)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
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