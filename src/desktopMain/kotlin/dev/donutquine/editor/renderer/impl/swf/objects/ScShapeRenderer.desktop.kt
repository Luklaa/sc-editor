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
import java.util.Collections
import java.util.WeakHashMap

// Кэш Skia Image по ImageBitmap текстуры. Без кэша Image.makeFromBitmap() (копирование
// пикселей битмапы в GPU-совместимый Image) вызывался бы заново на КАЖДУЮ команду отрисовки
// КАЖДЫЙ кадр — при Shape с большим числом команд это и давало просадку FPS. Текстуры
// сами по себе не меняются между кадрами, поэтому Image можно переиспользовать.
private val skiaImageCache = Collections.synchronizedMap(WeakHashMap<ImageBitmap, Image>())

actual fun DrawScope.drawTexturedMesh(
    texture: ImageBitmap,
    positions: FloatArray,
    texCoords: FloatArray,
    indices: ShortArray
) {
    drawIntoCanvas { canvas ->
        val skiaImage = skiaImageCache.getOrPut(texture) {
            Image.makeFromBitmap(texture.asDesktopBitmap())
        }

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
