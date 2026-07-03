package dev.donutquine.editor.renderer.impl.swf.objects

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.donutquine.swf.shapes.ShapeDrawBitmapCommand
import ui.ScBlendMode
import ui.ScColorTransformItem
import ui.ScTextureItem

expect fun DrawScope.drawTexturedMesh(
    texture: ImageBitmap,
    positions: FloatArray,
    texCoords: FloatArray,
    indices: ShortArray,
    colorTransform: ScColorTransformItem = ScColorTransformItem(),
    blendMode: ScBlendMode = ScBlendMode.NORMAL
)

@Composable
fun ScShapeView(
    commands: List<ShapeDrawBitmapCommand>,
    textures: List<ScTextureItem>,
    useStrip: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (commands.isEmpty()) return@Canvas

        val scale = 1f
        val offsetX = size.width / 2f
        val offsetY = size.height / 2f

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

            val indices = ShortArray(triangleCount * 3)
            if (useStrip) {
                for (t in 0 until triangleCount) {
                    indices[t * 3] = t.toShort()
                    indices[t * 3 + 1] = (t + 1).toShort()
                    indices[t * 3 + 2] = (t + 2).toShort()
                }
            } else {
                for (t in 0 until triangleCount) {
                    indices[t * 3] = 0
                    indices[t * 3 + 1] = (t + 1).toShort()
                    indices[t * 3 + 2] = (t + 2).toShort()
                }
            }

            drawTexturedMesh(bitmap, positions, texCoords, indices)
        }
    }
}