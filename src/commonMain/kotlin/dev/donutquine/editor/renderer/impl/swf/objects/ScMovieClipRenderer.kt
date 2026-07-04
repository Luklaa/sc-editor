package dev.donutquine.editor.renderer.impl.swf.objects

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import dev.donutquine.swf.shapes.ShapeDrawBitmapCommand
import androidx.compose.runtime.withFrameNanos
import ui.ScBlendMode
import ui.ScColorTransformItem
import ui.ScMatrixBankItem
import ui.ScMatrixItem
import ui.ScMovieClipModifierType
import ui.ScObjectItem
import ui.ScRectItem
import ui.ScTextureItem
import ui.blendCodeToScBlendMode

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
    var isPlaying by mutableStateOf(true)

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
        val startFrame = controller.currentFrame
        var startTimeNanos = -1L
        while (true) {
            withFrameNanos { frameTimeNanos ->
                if (startTimeNanos < 0) startTimeNanos = frameTimeNanos
                val elapsedSeconds = (frameTimeNanos - startTimeNanos) / 1_000_000_000f
                val framesElapsed = (elapsedSeconds * controller.fps).toInt()
                controller.setFrame(startFrame + framesElapsed)
            }
        }
    }

    return controller
}

internal val IDENTITY_MATRIX = ScMatrixItem()

// texCoords и indices зависят только от самого Shape-command (его вершин/UV/треугольников)
// и от useStrip (константа на весь файл) — от текущего кадра/матрицы НЕ зависят, в отличие
// от positions. Раньше они пересобирались заново на каждый draw call КАЖДЫЙ кадр — кэшируем
// по идентичности command (ShapeDrawBitmapCommand не переиспользуется между разными шейпами,
// а сам список obj.shapeCommands не пересоздаётся между кадрами).
private val shapeGeometryCache = mutableMapOf<ShapeDrawBitmapCommand, Pair<FloatArray, ShortArray>>()

private fun getOrBuildShapeGeometry(
    command: ShapeDrawBitmapCommand,
    textureItem: ScTextureItem,
    useStrip: Boolean
): Pair<FloatArray, ShortArray> {
    return shapeGeometryCache.getOrPut(command) {
        val vertexCount = command.vertexCount
        val triangleCount = command.triangleCount
        val texCoords = FloatArray(vertexCount * 2)
        for (i in 0 until vertexCount) {
            texCoords[i * 2] = command.getU(i) * textureItem.bitmap!!.width
            texCoords[i * 2 + 1] = command.getV(i) * textureItem.bitmap!!.height
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
        texCoords to indices
    }
}

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

// Композиция ColorTransform родителя и ребёнка — порт dev.donutquine.swf.ColorTransform.multiply():
// множители перемножаются (с нормировкой на 255, т.к. хранятся как 0..255, а не 0..1),
// добавки складываются, всё клампится в 0..255.
internal fun composeColorTransform(parent: ScColorTransformItem, child: ScColorTransformItem): ScColorTransformItem {
    fun mul(a: Int, b: Int) = (a * b / 255f).toInt().coerceIn(0, 255)
    fun add(a: Int, b: Int) = (a + b).coerceIn(0, 255)
    return ScColorTransformItem(
        redMultiplier = mul(parent.redMultiplier, child.redMultiplier),
        greenMultiplier = mul(parent.greenMultiplier, child.greenMultiplier),
        blueMultiplier = mul(parent.blueMultiplier, child.blueMultiplier),
        alpha = mul(parent.alpha, child.alpha),
        redAddition = add(parent.redAddition, child.redAddition),
        greenAddition = add(parent.greenAddition, child.greenAddition),
        blueAddition = add(parent.blueAddition, child.blueAddition)
    )
}

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
    val colorTransform: ScColorTransformItem,
    val blendMode: ScBlendMode,
    val maskGroupId: Int?
)

// Рекурсивно обходит дерево MovieClip -> children -> (Shape | MovieClip | TextField),
// на каждом уровне накапливая аффинную трансформацию и ColorTransform (RGB-тонирование +
// alpha), и на листьях (Shape) генерирует draw call'ы с уже применённой трансформацией
// к вершинам. depth — защита от случайной цикличности ссылок (a содержит b, который
// содержит a).
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
//
// overrides — ручные правки позиции/масштаба (гизмо, см. MovieClipGizmoEditor.kt), ключ —
// индекс элемента в frame.elements. Применяются ТОЛЬКО на depth == 0, т.е. только к прямым
// children КОРНЕВОГО мувиклипа — редактировать вложенные (глубже одного уровня) объекты
// пока нельзя, см. комментарий у ScMovieClipView.
private fun collectDrawCalls(
    objectId: Int,
    matrix: ScMatrixItem,
    ownMatrix: ScMatrixItem,
    scalingGrid: ScRectItem?,
    colorTransform: ScColorTransformItem,
    blendMode: ScBlendMode,
    maskGroupId: Int?,
    overrides: Map<Int, GizmoOverride>,
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
    if (depth > 16 || colorTransform.alpha <= 0) return
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
                }
                val (texCoords, indices) = getOrBuildShapeGeometry(command, textureItem, useStrip)

                output.add(MovieClipDrawCall(textureItem, positions, texCoords, indices, colorTransform, blendMode, maskGroupId))
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

            for ((elementIndex, element) in frame.elements.withIndex()) {
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

                val rawChildMatrix = bank?.matrices?.getOrNull(element.matrixIndex) ?: IDENTITY_MATRIX
                // Override двигает/масштабирует только ПРЯМЫХ children корневого мувиклипа —
                // см. комментарий у параметра overrides в сигнатуре функции.
                val childMatrix = if (depth == 0) {
                    overrides[elementIndex]?.let { rawChildMatrix.withGizmoOverride(it) } ?: rawChildMatrix
                } else {
                    rawChildMatrix
                }
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

                val childColor: ScColorTransformItem = bank?.colorTransforms?.getOrNull(element.colorTransformIndex)
                    ?: ScColorTransformItem()
                val childColorTransform = composeColorTransform(colorTransform, childColor)
                // Блендинг НЕ наследуется/накапливается — как и в оригинале
                // (displayObject.setBlendMode(...) вызывается для каждого ребёнка от его
                // СОБСТВЕННОГО blend-байта в родительском mcChildren, см. blendCodeToScBlendMode).
                // Если child сам MovieClip, при обходе ЕГО children ниже blendMode для
                // грандчилдов посчитается заново из их собственных blend — этот параметр
                // "доживёт" только до ближайшего листа Shape.
                val childBlendMode = blendCodeToScBlendMode(child.blend)

                collectDrawCalls(
                    objectId = child.id,
                    matrix = childFullMatrix,
                    ownMatrix = childMatrix,
                    // 9-slice сетка ЭТОГО мувиклипа (obj) действует на его прямых детей —
                    // см. комментарий над функцией.
                    scalingGrid = obj.scalingGrid,
                    colorTransform = childColorTransform,
                    blendMode = childBlendMode,
                    maskGroupId = currentMaskGroupId,
                    overrides = overrides,
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

// Считает fit (масштаб + сдвиг для вписывания в канву) ВСЕГДА по геометрии без учёта
// gizmo-overrides (overrides = emptyMap() в пробном проходе) — иначе перетаскивание/ресайз
// объекта на каждом кадре драга сдвигало/масштабировало бы саму канву под курсором, что
// ощущалось бы как "убегающий" объект. canvasWidth/canvasHeight — размер канвы в пикселях
// (DrawScope.size внутри Canvas{}, либо PointerInputScope.size снаружи — оба в px).
private fun computeFitTransform(
    movieClip: ScObjectItem,
    canvasWidth: Float,
    canvasHeight: Float
): FitTransform? {
    if (movieClip.mcFrames.isEmpty()) return null
    return FitTransform(1f, canvasWidth / 2f, canvasHeight / 2f)
}

private const val GIZMO_HANDLE_HIT_RADIUS_PX = 14f
private const val GIZMO_BODY_HIT_TOLERANCE_PX = 4f
private const val GIZMO_HANDLE_VISUAL_RADIUS_PX = 5f
private val GIZMO_ACCENT_COLOR = Color(0xFF3B82F6)

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
 *
 * Гизмо (см. MovieClipGizmoEditor.kt): клик по любому ПРЯМОМУ ребёнку корневого мувиклипа
 * на текущем кадре выделяет его (рамка + угловые хэндлы); драг тела двигает, драг угла —
 * uniform-масштабирует. Правки живут только в этой сессии просмотра, в файл не пишутся.
 * Редактировать объекты глубже одного уровня вложенности (внутри вложенных MovieClip) пока
 * нельзя — см. overrides в collectDrawCalls.
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
    // Гизмо (перетаскивание/ресайз children мувиклипа, см. MovieClipGizmoEditor.kt) сейчас
    // выключен по умолчанию — пользователь явно попросил не использовать его в текущем UX
    // (это не то, что есть в оригинальном редакторе). Код НЕ удалён, просто гейтится этим
    // флагом на случай, если понадобится вернуть/доработать позже.
    gizmoEnabled: Boolean = false,
    modifier: Modifier = Modifier
) {
    // key(movieClip.id): при переключении на другой объект в списке выделение/правки должны
    // сбрасываться, а не тащиться за собой на новый мувиклип.
    val gizmoState = remember(movieClip.id) { GizmoState() }

    // Текущий кадр КОРНЯ — та же формула, что и в collectDrawCalls для вложенных клипов,
    // но тут нужна отдельно и снаружи Canvas{} (для хит-тестинга в pointerInput).
    val rootFrameIndex = if (movieClip.mcFrames.size <= 1) 0
    else (timeSeconds * movieClip.fps.coerceAtLeast(1)).toInt().mod(movieClip.mcFrames.size.coerceAtLeast(1))
    val rootFrame = movieClip.mcFrames.getOrNull(rootFrameIndex)
    val rootBank = matrixBanks.getOrNull(movieClip.matrixBankIndex)

    Canvas(
        modifier = modifier
            .pointerHoverIcon(if (gizmoEnabled && gizmoState.selectedElementIndex != null) PointerIcon.Hand else PointerIcon.Default)
            // Ключ — только movieClip.id (НЕ timeSeconds/кадр): жест не должен прерываться
            // каждый раз, когда таймлайн переходит на следующий кадр во время драга. Из-за
            // этого хит-тестинг внутри может использовать чуть устаревший timeSeconds, если
            // редактировать прямо во время Play — некритичное упрощение, гизмо рассчитан на
            // редактирование на паузе.
            .let { base ->
                if (!gizmoEnabled) return@let base
                base.pointerInput(movieClip.id) {
                    var dragMode: GizmoDragMode? = null
                    var pointerScreen = Offset.Zero

                    detectDragGestures(
                        onDragStart = { start ->
                            pointerScreen = start
                            val frame = rootFrame
                            val fit = if (frame != null) {
                                computeFitTransform(movieClip, size.width.toFloat(), size.height.toFloat())
                            } else {
                                null
                            }

                            if (frame == null || fit == null) {
                                dragMode = null
                            } else {
                                val elements = collectSelectableGizmoElements(
                                    movieClip, frame, rootBank, gizmoState.overrides,
                                    objectsById, matrixBanks, modifiersById, useStrip, timeSeconds
                                )

                                val selectedIndex = gizmoState.selectedElementIndex
                                val selectedScreenBounds = elements.find { it.elementIndex == selectedIndex }
                                    ?.contentBounds?.toScreenBounds(fit)
                                val corner = selectedScreenBounds?.let { hitTestCorner(it, start, GIZMO_HANDLE_HIT_RADIUS_PX) }

                                if (selectedIndex != null && corner != null && selectedScreenBounds != null) {
                                    val center = Offset(
                                        (selectedScreenBounds[0] + selectedScreenBounds[2]) / 2f,
                                        (selectedScreenBounds[1] + selectedScreenBounds[3]) / 2f
                                    )
                                    val startDistance = (start - center).getDistance().coerceAtLeast(1f)
                                    val baseScale = gizmoState.overrides[selectedIndex]?.scale ?: 1f
                                    dragMode = GizmoDragMode.Resize(selectedIndex, baseScale, center, startDistance)
                                } else {
                                    // Последний в списке = верхний по z-order (нарисован поверх остальных).
                                    val hit = elements.lastOrNull {
                                        screenBoundsContains(it.contentBounds.toScreenBounds(fit), start, GIZMO_BODY_HIT_TOLERANCE_PX)
                                    }
                                    if (hit != null) {
                                        gizmoState.selectedElementIndex = hit.elementIndex
                                        dragMode = GizmoDragMode.Move(hit.elementIndex)
                                    } else {
                                        gizmoState.selectedElementIndex = null
                                        dragMode = null
                                    }
                                }
                            }
                        },
                        onDrag = { _, dragAmount ->
                            pointerScreen += dragAmount
                            val fit = computeFitTransform(movieClip, size.width.toFloat(), size.height.toFloat())

                            if (fit != null) {
                                when (val mode = dragMode) {
                                    is GizmoDragMode.Move -> {
                                        val current = gizmoState.overrides[mode.elementIndex] ?: GizmoOverride()
                                        gizmoState.overrides = gizmoState.overrides + (
                                                mode.elementIndex to current.copy(
                                                    dx = current.dx + dragAmount.x / fit.scale,
                                                    dy = current.dy + dragAmount.y / fit.scale
                                                )
                                                )
                                    }
                                    is GizmoDragMode.Resize -> {
                                        val currentDistance = (pointerScreen - mode.centerScreen).getDistance().coerceAtLeast(1f)
                                        val ratio = (currentDistance / mode.startDistance).coerceIn(0.05f, 25f)
                                        val current = gizmoState.overrides[mode.elementIndex] ?: GizmoOverride()
                                        gizmoState.overrides = gizmoState.overrides + (
                                                mode.elementIndex to current.copy(scale = mode.baseScale * ratio)
                                                )
                                    }
                                    null -> Unit
                                }
                            }
                        },
                        onDragEnd = { dragMode = null },
                        onDragCancel = { dragMode = null }
                    )
                }
            }
    ) {
        val drawCalls = mutableListOf<MovieClipDrawCall>()
        val maskGeometryById = mutableMapOf<Int, MutableList<FloatArray>>()
        val maskIdAllocator = MaskIdAllocator()

        collectDrawCalls(
            objectId = movieClip.id,
            matrix = IDENTITY_MATRIX,
            ownMatrix = IDENTITY_MATRIX,
            scalingGrid = null,
            colorTransform = ScColorTransformItem(),
            blendMode = ScBlendMode.NORMAL,
            maskGroupId = null,
            overrides = if (gizmoEnabled) gizmoState.overrides else emptyMap(),
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

        // Стабильный fit — БЕЗ учёта overrides (см. комментарий у computeFitTransform),
        // иначе редактирование дёргало бы масштаб канвы на каждый кадр драга.
        val fit = computeFitTransform(movieClip, size.width, size.height) ?: return@Canvas
        val scale = fit.scale
        val offsetX = fit.offsetX
        val offsetY = fit.offsetY

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
                drawTexturedMesh(bitmap, scaledPositions, call.texCoords, call.indices, call.colorTransform, call.blendMode)
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

        // Pass 3: рамка выделения + угловые хэндлы для выбранного гизмо-элемента (см.
        // MovieClipGizmoEditor.kt). Считаем bounds ЖИВЬЁМ (с текущим override), чтобы рамка
        // двигалась/масштабировалась синхронно с объектом во время драга.
        // Выключено по умолчанию — см. gizmoEnabled в сигнатуре функции.
        val selectedIndex = if (gizmoEnabled) gizmoState.selectedElementIndex else null
        if (selectedIndex != null && rootFrame != null) {
            val element = rootFrame.elements.getOrNull(selectedIndex)
            val child = element?.let { movieClip.mcChildren.getOrNull(it.childIndex) }
            if (element != null && child != null) {
                val rawMatrix = rootBank?.matrices?.getOrNull(element.matrixIndex) ?: IDENTITY_MATRIX
                val override = gizmoState.overrides[selectedIndex] ?: GizmoOverride()
                val liveMatrix = rawMatrix.withGizmoOverride(override)
                val bounds = computeChildContentBounds(
                    child.id, liveMatrix, objectsById, matrixBanks, modifiersById, useStrip, timeSeconds
                )
                if (bounds != null) {
                    val screenBounds = bounds.toScreenBounds(fit)
                    drawRect(
                        color = GIZMO_ACCENT_COLOR,
                        topLeft = Offset(screenBounds[0], screenBounds[1]),
                        size = Size(
                            (screenBounds[2] - screenBounds[0]).coerceAtLeast(0f),
                            (screenBounds[3] - screenBounds[1]).coerceAtLeast(0f)
                        ),
                        style = Stroke(width = 2f)
                    )
                    val corners = listOf(
                        Offset(screenBounds[0], screenBounds[1]),
                        Offset(screenBounds[2], screenBounds[1]),
                        Offset(screenBounds[0], screenBounds[3]),
                        Offset(screenBounds[2], screenBounds[3])
                    )
                    for (corner in corners) {
                        drawCircle(color = Color.White, radius = GIZMO_HANDLE_VISUAL_RADIUS_PX, center = corner)
                        drawCircle(
                            color = GIZMO_ACCENT_COLOR,
                            radius = GIZMO_HANDLE_VISUAL_RADIUS_PX,
                            center = corner,
                            style = Stroke(width = 2f)
                        )
                    }
                }
            }
        }
    }
}