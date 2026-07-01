package dev.donutquine.editor.renderer.impl.swf.objects

import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

actual fun DrawScope.drawTexturedMesh(
    texture: ImageBitmap,
    positions: FloatArray,
    texCoords: FloatArray,
    indices: ShortArray,
    alpha: Float
) {
    drawIntoCanvas { canvas ->
        val androidBitmap = texture.asAndroidBitmap()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(androidBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            this.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
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
