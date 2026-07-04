package dev.donutquine.editor.renderer.impl.swf.objects

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asDesktopBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import org.jetbrains.skia.BlendMode as SkiaBlendMode
import org.jetbrains.skia.ColorFilter
import org.jetbrains.skia.ColorMatrix
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Paint
import org.jetbrains.skia.VertexMode
import ui.ScBlendMode
import ui.ScColorTransformItem
import java.util.Collections
import java.util.WeakHashMap

private val skiaImageCache = Collections.synchronizedMap(WeakHashMap<ImageBitmap, Image>())

// ColorTransform (mult/add по каждому каналу, см. dev.donutquine.swf.ColorTransform) как
// 4x5 color matrix для Skia. ВАЖНО: в отличие от Android ColorMatrix (там offset 0..255),
// у Skia ColorMatrix.makeMatrix весь матрикс, включая offset-колонку, работает в диапазоне
// 0..1 — поэтому *Addition (0..255 в исходных данных) здесь делим на 255f.
private fun ScColorTransformItem.toSkiaColorMatrix(): ColorMatrix {
    val rm = redMultiplier / 255f
    val gm = greenMultiplier / 255f
    val bm = blueMultiplier / 255f
    val am = alpha / 255f
    val ra = redAddition / 255f
    val ga = greenAddition / 255f
    val ba = blueAddition / 255f
    return ColorMatrix(
        rm, 0f, 0f, 0f, ra,
        0f, gm, 0f, 0f, ga,
        0f, 0f, bm, 0f, ba,
        0f, 0f, 0f, am, 0f
    )
}

private fun ScBlendMode.toSkiaBlendMode(): SkiaBlendMode = when (this) {
    ScBlendMode.NORMAL -> SkiaBlendMode.SRC_OVER
    ScBlendMode.MULTIPLY -> SkiaBlendMode.MULTIPLY
    ScBlendMode.SCREEN -> SkiaBlendMode.SCREEN
    ScBlendMode.ADDITIVE -> SkiaBlendMode.PLUS
}

actual fun DrawScope.drawTexturedMesh(
    texture: ImageBitmap,
    positions: FloatArray,
    texCoords: FloatArray,
    indices: ShortArray,
    colorTransform: ScColorTransformItem,
    blendMode: ScBlendMode
) {
    drawIntoCanvas { canvas ->
        val skiaImage = skiaImageCache.getOrPut(texture) {
            Image.makeFromBitmap(texture.asDesktopBitmap())
        }

        val paint = Paint().apply {
            shader = skiaImage.makeShader(
                FilterTileMode.CLAMP,
                FilterTileMode.CLAMP,
                FilterMipmap(FilterMode.NEAREST, MipmapMode.NONE)
            )
            // RGB-тонирование + alpha (ColorTransform целиком) через матрицу цвета поверх
            // текстуры, а не просто глобальная альфа — иначе объекты, перекрашенные через
            // colorTransform (например один и тот же blur-текстура для разных цветных
            // свечений), рисовались бы в исходном "сыром" цвете текстуры.
            colorFilter = ColorFilter.makeMatrix(colorTransform.toSkiaColorMatrix())
            // Режим смешивания ребёнка (см. ScBlendMode) — отвечает за эффекты вроде
            // свечения (обычно ADDITIVE/SCREEN поверх фона), а не просто alpha-blend.
            this.blendMode = blendMode.toSkiaBlendMode()
        }

        canvas.nativeCanvas.drawVertices(
            vertexMode = VertexMode.TRIANGLES,
            positions = positions,
            colors = null,
            texCoords = texCoords,
            indices = indices,
            blendMode = SkiaBlendMode.MODULATE,
            paint = paint
        )
    }
}