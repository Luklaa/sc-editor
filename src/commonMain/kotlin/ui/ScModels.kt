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

// Аффинная матрица трансформации ребёнка мувиклипа на конкретном кадре
// (дублирует dev.donutquine.swf.Matrix2x3, но как чистые данные для Compose-стейта).
data class ScMatrixItem(
    val a: Float = 1f,
    val b: Float = 0f,
    val c: Float = 0f,
    val d: Float = 1f,
    val x: Float = 0f,
    val y: Float = 0f
)

// ColorTransform ребёнка мувиклипа (см. dev.donutquine.swf.ColorTransform).
// Мультипликаторы/добавки RGB пока не применяются при рендере (см. TODO в
// ScMovieClipRenderer.kt), используется только alpha.
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

// Ребёнок мувиклипа — ссылка по id на другой DisplayObject (Shape/MovieClip/TextField),
// см. dev.donutquine.swf.movieclips.MovieClipChild.
data class ScMovieClipChildItem(
    val id: Int,
    val blend: Int = 0,
    val name: String? = null
)

// Элемент кадра — какой child (по индексу в mcChildren), с какой матрицей/цветом рисовать
// на этом кадре. См. dev.donutquine.swf.movieclips.MovieClipFrameElement.
data class ScMovieClipFrameElementItem(
    val childIndex: Int,
    val matrixIndex: Int,
    val colorTransformIndex: Int
)

data class ScMovieClipFrameItem(
    val label: String? = null,
    val elements: List<ScMovieClipFrameElementItem> = emptyList()
)

// Прямоугольник scaling grid (9-slice) мувиклипа — см. dev.donutquine.math.Rect.
// Задан в тех же координатах, что и вершины вложенных Shape (twip->float, как в оригинале).
data class ScRectItem(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val midX: Float get() = left + width / 2f
    val midY: Float get() = top + height / 2f
}

// Модификаторы мувиклипа (dev.donutquine.swf.movieclips.MovieClipModifierOriginal) — это
// НЕ отрисовываемые объекты, а служебные маркеры прямо в списке children мувиклипа
// (Tag.MODIFIER_STATE_2/3/4), реализующие Flash-подобный stencil-маскинг:
//   MASK_BEGIN    — следующие children (до MASKED_BEGIN) образуют форму маски (сами невидимы)
//   MASKED_BEGIN  — следующие children (до MASK_END) рисуются, но обрезаются по форме маски
//   MASK_END      — маска снята, дальше рисуем как обычно
// См. ScMovieClipMaskRenderer.kt.
enum class ScMovieClipModifierType { MASK_BEGIN, MASKED_BEGIN, MASK_END }

data class ScMovieClipModifierItem(
    val id: Int,
    val type: ScMovieClipModifierType
)

data class ScObjectItem(
    val id: Int,
    val name: String,
    val type: String,
    val shapeCommands: List<ShapeDrawBitmapCommand> = emptyList(),
    // Поля ниже заполняются только для type == "MovieClip".
    val fps: Int = 0,
    val matrixBankIndex: Int = 0,
    val mcChildren: List<ScMovieClipChildItem> = emptyList(),
    val mcFrames: List<ScMovieClipFrameItem> = emptyList(),
    // 9-slice grid этого мувиклипа (если задан) — применяется к его ПРЯМЫМ children
    // типа Shape при рендере (см. ScMovieClipRenderer.kt). null = обычное растяжение.
    val scalingGrid: ScRectItem? = null
)

data class OpenedTab(
    val name: String,
    val path: String,
    val containerVersion: Int,
    val textures: List<ScTextureItem>,
    val objects: List<ScObjectItem>,
    val activeObjectIndex: Int = -1,    // Индекс выбранного объекта (-1 = ничего)
    val activeTextureIndex: Int = 0,     // Индекс выбранной текстуры
    val statusText: String,
    val matrixBanks: List<ScMatrixBankItem> = emptyList(),
    // Маркеры маскинга (см. ScMovieClipModifierType выше) — отдельно от objects, потому
    // что это не отрисовываемые DisplayObject'ы, а служебные записи в тех же id-пространствах
    // children мувиклипов.
    val modifiers: List<ScMovieClipModifierItem> = emptyList(),
    // Что сейчас должно быть показано во вьюпорте: "OBJECT" (выбранный объект из списка
    // Objects) или "TEXTURE" (выбранное полотно текстуры из вкладки Textures). Раньше
    // выбор текстуры визуально ничего не менял, если до этого был выбран объект — вьюпорт
    // жёстко приоритезировал object-рендер. Теперь это явный переключатель, который
    // проставляют оба обработчика выбора (onObjectSelected -> "OBJECT",
    // onTextureSelected -> "TEXTURE").
    val viewMode: String = "OBJECT"
) {
    // Быстрый поиск DisplayObject по id для рекурсивного рендера детей мувиклипа
    // (MovieClipChild.id -> ScObjectItem). Строится один раз при открытии файла.
    val objectsById: Map<Int, ScObjectItem> by lazy { objects.associateBy { it.id } }

    // Быстрый поиск маркера маскинга по id (MovieClipChild.id -> тип модификатора).
    val modifiersById: Map<Int, ScMovieClipModifierType> by lazy { modifiers.associate { it.id to it.type } }
}
