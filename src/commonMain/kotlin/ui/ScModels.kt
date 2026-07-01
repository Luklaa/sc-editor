package ui

import androidx.compose.ui.graphics.ImageBitmap
import dev.donutquine.swf.shapes.ShapeDrawBitmapCommand

data class ScTextureItem(
    val index: Int,
    val width: Int,
    val height: Int,
    val format: String,
    val bitmap: ImageBitmap? = null
)

data class ScMatrixItem(
    val a: Float = 1f,
    val b: Float = 0f,
    val c: Float = 0f,
    val d: Float = 1f,
    val x: Float = 0f,
    val y: Float = 0f
)

data class ScColorTransformItem(
    val redMultiplier: Int = 255,
    val greenMultiplier: Int = 255,
    val blueMultiplier: Int = 255,
    val alpha: Int = 255,
    val redAddition: Int = 0,
    val greenAddition: Int = 0,
    val blueAddition: Int = 0
)

data class ScMatrixBankItem(
    val matrices: List<ScMatrixItem> = emptyList(),
    val colorTransforms: List<ScColorTransformItem> = emptyList()
)

data class ScMovieClipChildItem(
    val id: Int,
    val blend: Int = 0,
    val name: String? = null
)

data class ScMovieClipFrameElementItem(
    val childIndex: Int,
    val matrixIndex: Int,
    val colorTransformIndex: Int
)

data class ScMovieClipFrameItem(
    val label: String? = null,
    val elements: List<ScMovieClipFrameElementItem> = emptyList()
)

data class ScObjectItem(
    val id: Int,
    val name: String,
    val type: String,
    val shapeCommands: List<ShapeDrawBitmapCommand> = emptyList(),

    val fps: Int = 0,
    val matrixBankIndex: Int = 0,
    val mcChildren: List<ScMovieClipChildItem> = emptyList(),
    val mcFrames: List<ScMovieClipFrameItem> = emptyList()
)

data class OpenedTab(
    val name: String,
    val path: String,
    val containerVersion: Int,
    val textures: List<ScTextureItem>,
    val objects: List<ScObjectItem>,
    val activeObjectIndex: Int = -1,
    val activeTextureIndex: Int = 0,
    val statusText: String,
    val matrixBanks: List<ScMatrixBankItem> = emptyList(),
    val viewMode: String = "OBJECT"
) {
    val objectsById: Map<Int, ScObjectItem> by lazy { objects.associateBy { it.id } }
}
