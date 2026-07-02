package dev.donutquine.editor.renderer.impl.swf.objects

import android.graphics.BitmapShader
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import ui.ScBlendMode
import ui.ScColorTransformItem

// ColorTransform (mult/add по каждому каналу, см. dev.donutquine.swf.ColorTransform) как
// 4x5 color matrix для android.graphics.ColorMatrix. В отличие от Skia (см. desktop actual),
// у Android ColorMatrix offset-колонка (redAddition и т.п.) в диапазоне 0..255 как есть,
// делить на 255 не нужно.
private fun ScColorTransformItem.toAndroidColorMatrix(): ColorMatrix {
    val rm = redMultiplier / 255f
    val gm = greenMultiplier / 255f
    val bm = blueMultiplier / 255f
    val am = alpha / 255f
    return ColorMatrix(
        floatArrayOf(
            rm, 0f, 0f, 0f, redAddition.toFloat(),
            0f, gm, 0f, 0f, greenAddition.toFloat(),
            0f, 0f, bm, 0f, blueAddition.toFloat(),
            0f, 0f, 0f, am, 0f
        )
    )
}

// null для NORMAL — обычный SrcOver и без Xfermode работает так же, но так меньше
// объектов создаётся на самый частый случай.
private fun ScBlendMode.toPorterDuffXfermode(): PorterDuffXfermode? = when (this) {
    ScBlendMode.NORMAL -> null
    ScBlendMode.MULTIPLY -> PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
    ScBlendMode.SCREEN -> PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    ScBlendMode.ADDITIVE -> PorterDuffXfermode(PorterDuff.Mode.ADD)
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
        val androidBitmap = texture.asAndroidBitmap()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(androidBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            // RGB-тонирование + alpha (ColorTransform целиком), а не просто глобальная
            // альфа — см. комментарий в desktop actual.
            colorFilter = ColorMatrixColorFilter(colorTransform.toAndroidColorMatrix())
            xfermode = blendMode.toPorterDuffXfermode()
        }

        canvas.nativeCanvas.drawVertices(
            android.graphics.Canvas.VertexMode.TRIANGLES,
            positions.size,
            positions,
            0,
            texCoords,
            0,
            null,
            0,
            indices,
            0,
            indices.size,
            paint
        )
    }
}