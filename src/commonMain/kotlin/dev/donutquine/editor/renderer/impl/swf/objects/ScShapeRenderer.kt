package dev.donutquine.editor.renderer.impl.swf.objects

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.donutquine.swf.shapes.ShapeDrawBitmapCommand
import ui.ScTextureItem

// Платформенная часть: непосредственный вызов отрисовки текстурированной треугольной
// сетки (positions/texCoords уже в пикселях канвы, indices — плоский буфер, по 3 индекса
// на треугольник, как в оригинальном Triangulator.TRIANGLE_FAN/TRIANGLE_STRIP).
// ВАЖНО: раз indices уже явно перечисляют треугольники тройками, режим отрисовки должен
// быть "TRIANGLES" в терминах платформенного API — а не "FAN"/"STRIP". Если применить
// veernный/полосовой режим ПОВЕРХ уже готового плоского индекс-буфера, отрисовщик собьёт
// связность и нарисует не те треугольники (это и было багом в предыдущей версии стаба).
expect fun DrawScope.drawTexturedMesh(
    texture: ImageBitmap,
    positions: FloatArray,
    texCoords: FloatArray,
    indices: ShortArray
)

/**
 * Отрисовывает Shape целиком: все его ShapeDrawBitmapCommand, вписанные единой рамкой
 * (общий bounding box по всем командам сразу) в доступную область канвы, с сохранением
 * пропорций и центрированием. Каждая команда может ссылаться на свою текстуру.
 */
@Composable
fun ScShapeView(
    commands: List<ShapeDrawBitmapCommand>,
    textures: List<ScTextureItem>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (commands.isEmpty()) return@Canvas

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (command in commands) {
            val vertexCount = command.vertexCount
            for (i in 0 until vertexCount) {
                val x = command.getX(i)
                val y = command.getY(i)
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }

        if (minX > maxX || minY > maxY) return@Canvas

        val shapeWidth = (maxX - minX).coerceAtLeast(1f)
        val shapeHeight = (maxY - minY).coerceAtLeast(1f)

        val paddingPx = 24f
        val availableWidth = size.width - paddingPx * 2f
        val availableHeight = size.height - paddingPx * 2f
        if (availableWidth <= 0f || availableHeight <= 0f) return@Canvas

        val scale = minOf(availableWidth / shapeWidth, availableHeight / shapeHeight)
        val offsetX = (size.width - shapeWidth * scale) / 2f - minX * scale
        val offsetY = (size.height - shapeHeight * scale) / 2f - minY * scale

        for (command in commands) {
            val textureItem = textures.getOrNull(command.textureIndex) ?: continue
            val bitmap = textureItem.bitmap ?: continue

            val vertexCount = command.vertexCount
            val triangleCount = command.triangleCount
            if (vertexCount < 3 || triangleCount <= 0) continue

            val positions = FloatArray(vertexCount * 2)
            val texCoords = FloatArray(vertexCount * 2)
            for (i in 0 until vertexCount) {
                positions[i * 2] = command.getX(i) * scale + offsetX
                positions[i * 2 + 1] = command.getY(i) * scale + offsetY
                texCoords[i * 2] = command.getU(i) * bitmap.width
                texCoords[i * 2 + 1] = command.getV(i) * bitmap.height
            }

            // Веерная триангуляция от вершины 0 — так эти фигуры уже упорядочены
            // в файле (см. Triangulator.TRIANGLE_FAN в оригинальном Java-рендерере).
            val indices = ShortArray(triangleCount * 3)
            for (t in 0 until triangleCount) {
                indices[t * 3] = 0
                indices[t * 3 + 1] = (t + 1).toShort()
                indices[t * 3 + 2] = (t + 2).toShort()
            }

            drawTexturedMesh(bitmap, positions, texCoords, indices)
        }
    }
}
