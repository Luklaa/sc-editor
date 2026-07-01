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
    indices: ShortArray
) {
    drawIntoCanvas { canvas ->
        val androidBitmap = texture.asAndroidBitmap()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(androidBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }

        // android.graphics.Canvas.drawVertices ждёт ровно тот же плоский индекс-буфер
        // (по 3 индекса на треугольник) — режим TRIANGLES соответствует Triangulator
        // из оригинального рендерера (не FAN/STRIP).
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
