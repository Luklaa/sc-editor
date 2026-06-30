package dev.donutquine.editor.renderer.impl.swf.objects

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asDesktopBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import dev.donutquine.swf.shapes.ShapeDrawBitmapCommand
import org.jetbrains.skia.*

@Composable
fun ScShape(
    command: ShapeDrawBitmapCommand,
    texture: ImageBitmap,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas

            val skiaBitmap = texture.asDesktopBitmap()
            val skiaImage = Image.makeFromBitmap(skiaBitmap)

            val paint = Paint().apply {
                // ИСПРАВЛЕНО: Создаем шейдер через метод самого изображения
                shader = skiaImage.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP)
            }

            val vertexCount = command.vertexCount
            val positions = FloatArray(vertexCount * 2)
            val texCoords = FloatArray(vertexCount * 2)

            for (i in 0 until vertexCount) {
                positions[i * 2] = command.getX(i)
                positions[i * 2 + 1] = command.getY(i)
                texCoords[i * 2] = command.getU(i) * texture.width
                texCoords[i * 2 + 1] = command.getV(i) * texture.height
            }

            val isStrip = (command.triangleCount and 0x8000) != 0
            val mode = if (isStrip) VertexMode.TRIANGLE_STRIP else VertexMode.TRIANGLE_FAN
            val indices = getIndices(command, isStrip)

            // ИСПРАВЛЕНО: Передаем параметры массивами напрямую в Canvas
            nativeCanvas.drawVertices(
                vertexMode = mode,
                positions = positions,
                colors = null,
                texCoords = texCoords,
                indices = indices,
                blendMode = BlendMode.MODULATE,
                paint = paint
            )
        }
    }
}

private fun getIndices(command: ShapeDrawBitmapCommand, isStrip: Boolean): ShortArray {
    val triangleCount = command.triangleCount
    val indices = ShortArray(triangleCount * 3)
    var k = 0

    if (isStrip) {
        for (i in 0 until triangleCount) {
            if (i % 2 == 0) {
                indices[k++] = i.toShort()
                indices[k++] = (i + 1).toShort()
                indices[k++] = (i + 2).toShort()
            } else {
                indices[k++] = (i + 1).toShort()
                indices[k++] = i.toShort()
                indices[k++] = (i + 2).toShort()
            }
        }
    } else {
        for (i in 0 until triangleCount) {
            indices[k++] = 0
            indices[k++] = (i + 1).toShort()
            indices[k++] = (i + 2).toShort()
        }
    }
    return indices
}
