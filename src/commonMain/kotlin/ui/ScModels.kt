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

data class ScObjectItem(
    val id: Int,
    val name: String,
    val type: String,
    val shapeCommands: List<ShapeDrawBitmapCommand> = emptyList()
)

data class OpenedTab(
    val name: String,
    val path: String,
    val containerVersion: Int,
    val textures: List<ScTextureItem>,
    val objects: List<ScObjectItem>,
    val activeObjectIndex: Int = -1,    // Индекс выбранного объекта (-1 = ничего)
    val activeTextureIndex: Int = 0,     // Индекс выбранной текстуры
    val statusText: String
)
