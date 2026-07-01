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
