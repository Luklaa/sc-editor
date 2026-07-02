package dev.donutquine.editor.renderer.impl.swf.objects

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlinx.coroutines.delay
import ui.ScColorTransformItem
import ui.ScMatrixBankItem
import ui.ScMatrixItem
import ui.ScMovieClipModifierType
import ui.ScObjectItem
import ui.ScRectItem
import ui.ScTextureItem

/**
 * Состояние проигрывателя мувиклипа (кадр + play/stop), которым управляет
 * GlassTimelinePanel и который используется ScMovieClipView для рендера.
 *
 * timeSeconds — единый "источник времени" для ВСЕГО дерева: вложенные мувиклипы
 * анимируются по своим fps/frames.size от этого же значения, а не от значения
 * currentFrame родителя (у них может быть другое число кадров и другой fps).
 */
class MovieClipController(
    val frameCount: Int,
    val fps: Int
) {
    var currentFrame by mutableStateOf(0)
        private set
    var isPlaying by mutableStateOf(false)

    val timeSeconds: Float
        get() = if (fps > 0) currentFrame / fps.toFloat() else 0f

    fun setFrame(frame: Int) {
        if (frameCount <= 0) return
        currentFrame = ((frame % frameCount) + frameCount) % frameCount
    }

    fun togglePlaying() {
        isPlaying = !isPlaying
    }
}

@Composable
fun rememberMovieClipController(movieClip: ScObjectItem): MovieClipController {
    // key по id мувиклипа: при переключении на другой объект в списке — контроллер
    // (и текущий кадр/play-состояние) должен создаваться заново, а не тащить за собой
    // состояние предыдущего мувиклипа.
    val controller = remember(movieClip.id) {
        MovieClipController(frameCount = movieClip.mcFrames.size, fps = movieClip.fps.coerceAtLeast(1))
    }

    LaunchedEffect(controller, controller.isPlaying) {
        if (!controller.isPlaying || controller.frameCount <= 1) return@LaunchedEffect
        val frameDelayMs = (1000 / controller.fps.coerceAtLeast(1)).coerceAtLeast(1).toLong()
        while (true) {
            delay(frameDelayMs)
            controller.setFrame(controller.currentFrame + 1)
        }
    }

    return controller
}

internal val IDENTITY_MATRIX = ScMatrixItem()

// Композиция двух аффинных матриц: результат.apply(p) == parent.apply(child.apply(p)).
// См. dev.donutquine.swf.Matrix2x3#applyX/applyY:
//   applyX(x,y) = x*a + y*c + this.x
//   applyY(x,y) = y*d + x*b + this.y
internal fun composeMatrix(parent: ScMatrixItem, child: ScMatrixItem): ScMatrixItem {
    return ScMatrixItem(
        a = parent.a * child.a + parent.c * child.b,
        c = parent.a * child.c + parent.c * child.d,
        b = parent.b * child.a + parent.d * child.b,
        d = parent.b * child.c + parent.d * child.d,
        x = parent.a * child.x + parent.c * child.y + parent.x,
        y = parent.b * child.x + parent.d * child.y + parent.y
    )
}

internal fun ScMatrixItem.applyX(px: Float, py: Float) = px * a + py * c + x
internal fun ScMatrixItem.applyY(px: Float, py: Float) = py * d + px * b + y

// Простой счётчик id масок, общий на весь вызов ScMovieClipView (см. Pass 1 ниже).
private class MaskIdAllocator {
    private var next = 0
    fun next(): Int = next++
}

// Один "плоский" draw call — уже полностью посчитанные позиции вершин в системе
// координат корневого мувиклипа (до масштабирования под канву), готовые к отрисовке.
// maskGroupId != null означает "рисовать только внутри формы маски с этим id"
// (см. maskGeometryById в ScMovieClipView и комментарий у ScMovieClipModifierType).
private class MovieClipDrawCall(
    val textureItem: ScTextureItem,
    val positions: FloatArray,
    val texCoords: FloatArray,
    val indices: ShortArray,
    val alpha: Float,
    val maskGroupId: Int?
)

// Рекурсивно обходит дерево MovieClip -> children -> (Shape | MovieClip | TextField),
// на каждом уровне накапливая аффинную трансформацию и alpha, и на листьях (Shape)
// генерирует draw call'ы с уже применённой трансформацией к вершинам.
// depth — защита от случайной цикличности ссылок (a содержит b, который содержит a).
//
// scalingGrid/ownMatrix — контекст 9-slice, унаследованный от РОДИТЕЛЬСКОГО MovieClip:
// см. оригинал MovieClip.createMovieClip -> DisplayObjectFactory.createFromOriginal(...,
// original.getScalingGrid(), ...) — каждый ПРЯМОЙ ребёнок-Shape мувиклипа со scalingGrid
// рендерится как Shape9Slice именно с сеткой родителя, а не своей собственной (у Shape
// своей сетки и нет). ownMatrix — это "свой" (несоставной) matrix этого узла из
// matrixBank родителя, нужен только для сдвига сетки в локальные координаты Shape.
//
// maskGroupId — id маски (см. MovieClipMaskRenderer.kt), которая СЕЙЧАС в силе для этого
// узла (унаследован от родителя; см. обработку MASK_BEGIN/MASKED_BEGIN/MASK_END в ветке
// "MovieClip" ниже — она может сменить его для СВОИХ children).
private fun collectDrawCalls(
    objectId: Int,
    matrix: ScMatrixItem,
    ownMatrix: ScMatrixItem,
    scalingGrid: ScRectItem?,
    alpha: Float,
    maskGroupId: Int?,
    objectsById: Map<Int, ScObjectItem>,
    matrixBanks: List<ScMatrixBankItem>,
    modifiersById: Map<Int, ScMovieClipModifierType>,
    maskGeometryById: MutableMap<Int, MutableList<FloatArray>>,
    maskIdAllocator: MaskIdAllocator,
    textures: List<ScTextureItem>,
    useStrip: Boolean,
    timeSeconds: Float,
    depth: Int,
    output: MutableList<MovieClipDrawCall>
) {
    if (depth > 16 || alpha <= 0f) return
    val obj = objectsById[objectId] ?: return

    when (obj.type) {
        "Shape" -> {
            val useNineSlice = scalingGrid != null
            val shapeBounds = if (useNineSlice) computeShapeLocalBounds(obj.shapeCommands) else null
            val safeArea = if (useNineSlice) scalingGrid!!.movedBy(-ownMatrix.x, -ownMatrix.y) else null
            val (scaledWidth, scaledHeight) = if (useNineSlice) computeNineSliceScale(scalingGrid!!, matrix) else 1f to 1f

            for (command in obj.shapeCommands) {
                val textureItem = textures.getOrNull(command.textureIndex) ?: continue
                if (textureItem.bitmap == null) continue

                val vertexCount = command.vertexCount
                val triangleCount = command.triangleCount
                if (vertexCount < 3 || triangleCount <= 0) continue

                val positions = FloatArray(vertexCount * 2)
                val texCoords = FloatArray(vertexCount * 2)
                for (i in 0 until vertexCount) {
                    val rawX = command.getX(i)
                    val rawY = command.getY(i)
                    val (localX, localY) = if (useNineSlice) {
                        nineSliceLocalXY(rawX, rawY, safeArea!!, shapeBounds!!, scaledWidth, scaledHeight)
                    } else {
                        rawX to rawY
                    }
                    positions[i * 2] = matrix.applyX(localX, localY)
                    positions[i * 2 + 1] = matrix.applyY(localX, localY)
                    texCoords[i * 2] = command.getU(i) * textureItem.bitmap.width
                    texCoords[i * 2 + 1] = command.getV(i) * textureItem.bitmap.height
                }

                // Тот же режим триангуляции, что и в ScShapeView (см. комментарий там):
                // зависит от версии контейнера, а не от того, MovieClip это или Shape.
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

                output.add(MovieClipDrawCall(textureItem, positions, texCoords, indices, alpha, maskGroupId))
            }
        }

        "MovieClip" -> {
            if (obj.mcFrames.isEmpty()) return
            // Вложенный мувиклип крутит СВОЙ таймлайн независимо от родителя, по общему
            // "часовому поясу" timeSeconds — как и в оригинале (каждый MovieClip играет
            // на собственном fps/frames.size).
            val fps = obj.fps.coerceAtLeast(1)
            val frameIndex = if (obj.mcFrames.size <= 1) 0
                else (timeSeconds * fps).toInt().mod(obj.mcFrames.size)
            val frame = obj.mcFrames[frameIndex]
            val bank = matrixBanks.getOrNull(obj.matrixBankIndex)

            // Локальный стейт stencil-маскинга для СПИСКА children этого мувиклипа (см.
            // ScMovieClipModifierType в ScModels.kt и Tag.MODIFIER_STATE_2/3/4 в оригинале).
            // currentMaskGroupId стартует с унаследованного от родителя значения (если этот
            // мувиклип сам был вызван внутри чужой маски) и может быть переопределён своим
            // MASK_BEGIN/MASK_END — упрощение: вложенные маски не пересекаются, а замещают
            // друг друга (самая внутренняя выигрывает), полноценный стек стенсила как в
            // оригинальном GL-рендерере (old_cdoe EditorStage.java) здесь не реализован.
            var currentMaskGroupId = maskGroupId
            var collectingMask = false

            for (element in frame.elements) {
                val child = obj.mcChildren.getOrNull(element.childIndex) ?: continue
                // См. оригинал (MovieClip.createMovieClip):
                // displayObject.setVisibleRecursive((blend & 64) == 0) — бит 64 в blend
                // помечает ребёнка скрытым на всех кадрах, где он используется.
                if (child.blend and 64 != 0) continue

                val modifierType = modifiersById[child.id]
                if (modifierType != null) {
                    // Модификатор — не отрисовываемый объект, а служебный маркер прямо в
                    // списке children, переключающий режим для ВСЕХ следующих элементов.
                    when (modifierType) {
                        ScMovieClipModifierType.MASK_BEGIN -> {
                            val newId = maskIdAllocator.next()
                            maskGeometryById[newId] = mutableListOf()
                            currentMaskGroupId = newId
                            collectingMask = true
                        }
                        ScMovieClipModifierType.MASKED_BEGIN -> {
                            collectingMask = false
                        }
                        ScMovieClipModifierType.MASK_END -> {
                            currentMaskGroupId = maskGroupId
                            collectingMask = false
                        }
                    }
                    continue
                }

                val childMatrix = bank?.matrices?.getOrNull(element.matrixIndex) ?: IDENTITY_MATRIX
                val childFullMatrix = composeMatrix(matrix, childMatrix)

                if (collectingMask) {
                    // Это часть ФОРМЫ маски — сама по себе невидима, только даёт геометрию
                    // для clip-контура (см. Pass 2 в ScMovieClipView).
                    val bucket = maskGeometryById[currentMaskGroupId] ?: continue
                    collectMaskTriangles(
                        objectId = child.id,
                        matrix = childFullMatrix,
                        objectsById = objectsById,
                        matrixBanks = matrixBanks,
                        modifiersById = modifiersById,
                        useStrip = useStrip,
                        timeSeconds = timeSeconds,
                        depth = depth + 1,
                        output = bucket
                    )
                    continue
                }

                val childColor: ScColorTransformItem? = bank?.colorTransforms?.getOrNull(element.colorTransformIndex)
                val childAlpha = alpha * ((childColor?.alpha ?: 255) / 255f)

                collectDrawCalls(
                    objectId = child.id,
                    matrix = childFullMatrix,
                    ownMatrix = childMatrix,
                    // 9-slice сетка ЭТОГО мувиклипа (obj) действует на его прямых детей —
                    // см. комментарий над функцией.
                    scalingGrid = obj.scalingGrid,
                    alpha = childAlpha,
                    maskGroupId = currentMaskGroupId,
                    objectsById = objectsById,
                    matrixBanks = matrixBanks,
                    modifiersById = modifiersById,
                    maskGeometryById = maskGeometryById,
                    maskIdAllocator = maskIdAllocator,
                    textures = textures,
                    useStrip = useStrip,
                    timeSeconds = timeSeconds,
                    depth = depth + 1,
                    output = output
                )
            }
        }

        // TextField и прочее — пока не рендерим (нет геометрии для отображения текста).
        else -> return
    }
}

/**
 * Отрисовывает MovieClip целиком: рекурсивно проходит по frame elements текущего кадра
 * (и кадров вложенных мувиклипов — см. collectDrawCalls), собирает все Shape-меши со
 * своими накопленными трансформациями и рисует единым "коллажем", вписанным в канву
 * с сохранением пропорций (аналогично ScShapeView, но по общему bounding box'у всех
 * вложенных мешей, а не одного Shape).
 *
 * Маски (MASK_BEGIN/MASKED_BEGIN/MASK_END) рисуются через Compose clipPath: Pass 1
 * (collectDrawCalls) собирает видимые draw call'ы и ОТДЕЛЬНО геометрию масок
 * (maskGeometryById), Pass 2 группирует draw call'ы по maskGroupId и оборачивает
 * каждую группу в clipPath с соответствующим контуром.
 */
@Composable
fun ScMovieClipView(
    movieClip: ScObjectItem,
    objectsById: Map<Int, ScObjectItem>,
    matrixBanks: List<ScMatrixBankItem>,
    modifiersById: Map<Int, ScMovieClipModifierType>,
    textures: List<ScTextureItem>,
    useStrip: Boolean,
    timeSeconds: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val drawCalls = mutableListOf<MovieClipDrawCall>()
        val maskGeometryById = mutableMapOf<Int, MutableList<FloatArray>>()
        val maskIdAllocator = MaskIdAllocator()

        collectDrawCalls(
            objectId = movieClip.id,
            matrix = IDENTITY_MATRIX,
            ownMatrix = IDENTITY_MATRIX,
            scalingGrid = null,
            alpha = 1f,
            maskGroupId = null,
            objectsById = objectsById,
            matrixBanks = matrixBanks,
            modifiersById = modifiersById,
            maskGeometryById = maskGeometryById,
            maskIdAllocator = maskIdAllocator,
            textures = textures,
            useStrip = useStrip,
            timeSeconds = timeSeconds,
            depth = 0,
            output = drawCalls
        )

        if (drawCalls.isEmpty()) return@Canvas

        // Bounding box считаем только по ВИДИМЫМ draw call'ам (маски в него не входят) —
        // иначе гигантская "маска-заглушка", которая сама не видна, могла бы испортить
        // авто-вписывание всего мувиклипа в канву.
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (call in drawCalls) {
            var i = 0
            while (i < call.positions.size) {
                val px = call.positions[i]
                val py = call.positions[i + 1]
                if (px < minX) minX = px
                if (px > maxX) maxX = px
                if (py < minY) minY = py
                if (py > maxY) maxY = py
                i += 2
            }
        }

        if (minX > maxX || minY > maxY) return@Canvas

        val contentWidth = (maxX - minX).coerceAtLeast(1f)
        val contentHeight = (maxY - minY).coerceAtLeast(1f)

        val paddingPx = 24f
        val availableWidth = size.width - paddingPx * 2f
        val availableHeight = size.height - paddingPx * 2f
        if (availableWidth <= 0f || availableHeight <= 0f) return@Canvas

        val scale = minOf(availableWidth / contentWidth, availableHeight / contentHeight)
        val offsetX = (size.width - contentWidth * scale) / 2f - minX * scale
        val offsetY = (size.height - contentHeight * scale) / 2f - minY * scale

        // ВАЖНО: это extension-лямбда (DrawScope.(...) -> Unit), а не обычная локальная
        // функция — обычная функция захватила бы ВНЕШНИЙ (необрезанный) DrawScope из
        // Canvas{} лексически и продолжала бы рисовать в него даже вызванная изнутри
        // clipPath{} ниже, из-за чего клиппинг маски физически не применялся бы. Так же
        // (как extension-лямбда) она подхватывает ТЕКУЩИЙ implicit receiver в месте вызова —
        // обрезанный DrawScope внутри clipPath{}, обычный снаружи.
        val drawCall: DrawScope.(MovieClipDrawCall) -> Unit = { call ->
            val bitmap = call.textureItem.bitmap
            if (bitmap != null) {
                val scaledPositions = FloatArray(call.positions.size)
                for (i in call.positions.indices step 2) {
                    scaledPositions[i] = call.positions[i] * scale + offsetX
                    scaledPositions[i + 1] = call.positions[i + 1] * scale + offsetY
                }
                drawTexturedMesh(bitmap, scaledPositions, call.texCoords, call.indices, call.alpha)
            }
        }

        // Pass 2: группируем по маске и рисуем — немаскированные (maskGroupId == null)
        // отдельно, каждую маскированную группу — внутри clipPath со своим контуром.
        for ((maskId, calls) in drawCalls.groupBy { it.maskGroupId }) {
            if (maskId == null) {
                for (call in calls) drawCall(call)
                continue
            }

            val triangles = maskGeometryById[maskId]
            if (triangles.isNullOrEmpty()) {
                // Маска объявлена, но её форма пуста (например, все её шейпы без текстуры) —
                // значит замаскированный контент нигде не проходит clip-тест, ничего не рисуем.
                continue
            }

            val path = Path()
            for (tri in triangles) {
                path.moveTo(tri[0] * scale + offsetX, tri[1] * scale + offsetY)
                path.lineTo(tri[2] * scale + offsetX, tri[3] * scale + offsetY)
                path.lineTo(tri[4] * scale + offsetX, tri[5] * scale + offsetY)
                path.close()
            }

            clipPath(path) {
                for (call in calls) drawCall(call)
            }
        }
    }
}
