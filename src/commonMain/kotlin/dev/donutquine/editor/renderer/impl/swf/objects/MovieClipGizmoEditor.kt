package dev.donutquine.editor.renderer.impl.swf.objects

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import ui.ScMatrixItem
import ui.ScMovieClipModifierType
import ui.ScObjectItem

/**
 * Ручное редактирование позиции/размера ПРЯМЫХ children корневого MovieClip во вьюпорте —
 * аналог гизмо-хэндлов из старого Java-редактора (editor/gizmos/Gizmos.java), но упрощённый:
 * только move (тело) + uniform resize (угловые хэндлы), без поворота и без per-axis resize.
 *
 * Правки живут только в памяти этой сессии просмотра (GizmoState создаётся заново при смене
 * объекта — см. remember(movieClip.id) в ScMovieClipRenderer.kt) и НЕ пишутся обратно в файл.
 */

// Накопленная правка одного элемента кадра (по индексу в frame.elements). dx/dy — сдвиг в
// content-space (той же системе координат, что и matrix.x/y, т.е. ДО итогового fit-масштаба
// канвы), scale — множитель поверх исходных a/b/c/d матрицы.
internal data class GizmoOverride(
    val dx: Float = 0f,
    val dy: Float = 0f,
    val scale: Float = 1f
)

internal fun ScMatrixItem.withGizmoOverride(override: GizmoOverride): ScMatrixItem {
    if (override.dx == 0f && override.dy == 0f && override.scale == 1f) return this
    return ScMatrixItem(
        a = a * override.scale,
        b = b * override.scale,
        c = c * override.scale,
        d = d * override.scale,
        x = x + override.dx,
        y = y + override.dy
    )
}

// Стейт гизмо для одного открытого MovieClip во вьюпорте.
internal class GizmoState {
    var overrides by mutableStateOf<Map<Int, GizmoOverride>>(emptyMap())
    var selectedElementIndex by mutableStateOf<Int?>(null)
}

// Стабильный "fit" (масштаб + сдвиг для вписывания в канву), см. computeFitTransform в
// ScMovieClipRenderer.kt. Специально считается БЕЗ учёта текущих gizmo-overrides, иначе
// перетаскивание объекта дёргало бы зум всей канвы на каждый кадр драга.
internal class FitTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float
)

internal fun FloatArray.toScreenBounds(fit: FitTransform): FloatArray = floatArrayOf(
    this[0] * fit.scale + fit.offsetX,
    this[1] * fit.scale + fit.offsetY,
    this[2] * fit.scale + fit.offsetX,
    this[3] * fit.scale + fit.offsetY
)

// Один выделяемый (селектящийся) прямой ребёнок корневого мувиклипа на текущем кадре.
internal class SelectableGizmoElement(
    val elementIndex: Int,
    // Bounding box в content-space (ДО fit-масштаба), уже с применённым override, если есть.
    val contentBounds: FloatArray
)

// bounding box геометрии одного child (Shape или вложенный MovieClip целиком) в content-space —
// переиспользует collectMaskTriangles (даёт вершины без текстур, ровно то, что нужно для bbox).
internal fun computeChildContentBounds(
    childId: Int,
    matrix: ScMatrixItem,
    objectsById: Map<Int, ScObjectItem>,
    matrixBanks: List<ui.ScMatrixBankItem>,
    modifiersById: Map<Int, ScMovieClipModifierType>,
    useStrip: Boolean,
    timeSeconds: Float
): FloatArray? {
    val triangles = mutableListOf<FloatArray>()
    collectMaskTriangles(childId, matrix, objectsById, matrixBanks, modifiersById, useStrip, timeSeconds, 0, triangles)
    if (triangles.isEmpty()) return null

    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (tri in triangles) {
        for (i in 0 until 3) {
            val x = tri[i * 2]
            val y = tri[i * 2 + 1]
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
    }
    return floatArrayOf(minX, minY, maxX, maxY)
}

// Все выделяемые прямые children текущего кадра корневого мувиклипа (маски/скрытые/маркеры
// пропускаются — их двигать нельзя). Порядок совпадает с порядком отрисовки (z-order).
internal fun collectSelectableGizmoElements(
    movieClip: ScObjectItem,
    frame: ui.ScMovieClipFrameItem,
    bank: ui.ScMatrixBankItem?,
    overrides: Map<Int, GizmoOverride>,
    objectsById: Map<Int, ScObjectItem>,
    matrixBanks: List<ui.ScMatrixBankItem>,
    modifiersById: Map<Int, ScMovieClipModifierType>,
    useStrip: Boolean,
    timeSeconds: Float
): List<SelectableGizmoElement> {
    val result = mutableListOf<SelectableGizmoElement>()
    for ((elementIndex, element) in frame.elements.withIndex()) {
        val child = movieClip.mcChildren.getOrNull(element.childIndex) ?: continue
        if (child.blend and 64 != 0) continue
        if (modifiersById[child.id] != null) continue

        val rawMatrix = bank?.matrices?.getOrNull(element.matrixIndex) ?: IDENTITY_MATRIX
        val matrix = overrides[elementIndex]?.let { rawMatrix.withGizmoOverride(it) } ?: rawMatrix
        val bounds = computeChildContentBounds(child.id, matrix, objectsById, matrixBanks, modifiersById, useStrip, timeSeconds)
            ?: continue

        result.add(SelectableGizmoElement(elementIndex, bounds))
    }
    return result
}

internal fun screenBoundsContains(bounds: FloatArray, point: Offset, tolerance: Float): Boolean {
    return point.x >= bounds[0] - tolerance && point.x <= bounds[2] + tolerance &&
        point.y >= bounds[1] - tolerance && point.y <= bounds[3] + tolerance
}

// Угловые хэндлы: 0=верх-лево, 1=верх-право, 2=низ-лево, 3=низ-право.
internal fun hitTestCorner(bounds: FloatArray, point: Offset, radius: Float): Int? {
    val corners = arrayOf(
        Offset(bounds[0], bounds[1]),
        Offset(bounds[2], bounds[1]),
        Offset(bounds[0], bounds[3]),
        Offset(bounds[2], bounds[3])
    )
    for ((index, corner) in corners.withIndex()) {
        if ((point - corner).getDistance() <= radius) return index
    }
    return null
}

// Что именно тащит пользователь прямо сейчас.
internal sealed class GizmoDragMode {
    class Move(val elementIndex: Int) : GizmoDragMode()
    class Resize(
        val elementIndex: Int,
        val baseScale: Float,
        val centerScreen: Offset,
        val startDistance: Float
    ) : GizmoDragMode()
}
