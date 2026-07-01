package dev.donutquine.editor.renderer.impl.swf.objects

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asDesktopBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import org.jetbrains.skia.BlendMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.VertexMode

actual fun DrawScope.drawTexturedMesh(
    texture: ImageBitmap,
    positions: FloatArray,
    texCoords: FloatArray,
    indices: ShortArray
) {
    drawIntoCanvas { canvas ->
        val skiaBitmap = texture.asDesktopBitmap()
        val skiaImage = Image.makeFromBitmap(skiaBitmap)

        val paint = Paint().apply {
            shader = skiaImage.makeShader(FilterTileMode.CLAMP, FilterTileMode.CLAMP)
        }

        // ИСПРАВЛЕНО: indices уже явно перечисляют треугольники тройками индексов
        // (плоский буфер из ScShapeView, аналог GL_TRIANGLES с индекс-буфером из
        // оригинального Triangulator). Раньше здесь стоял TRIANGLE_FAN/TRIANGLE_STRIP,
        // что заставляло Skia ПОВТОРНО применять веерную/полосовую связность поверх уже
        // готовых индексов — сетка триангулировалась неправильно.
        canvas.nativeCanvas.drawVertices(
            vertexMode = VertexMode.TRIANGLES,
            positions = positions,
            colors = null,
            texCoords = texCoords,
            indices = indices,
            blendMode = BlendMode.MODULATE,
            paint = paint
        )
    }
}
