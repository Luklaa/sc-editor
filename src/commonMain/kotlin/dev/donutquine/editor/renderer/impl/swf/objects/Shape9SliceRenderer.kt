package dev.donutquine.editor.renderer.impl.swf.objects

import dev.donutquine.swf.shapes.ShapeDrawBitmapCommand
import ui.ScMatrixItem
import ui.ScRectItem
import kotlin.math.sqrt

internal fun ScRectItem.movedBy(dx: Float, dy: Float) = ScRectItem(left + dx, top + dy, right + dx, bottom + dy)

internal fun computeShapeLocalBounds(commands: List<ShapeDrawBitmapCommand>): ScRectItem {
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (command in commands) {
        for (i in 0 until command.vertexCount) {
            val x = command.getX(i)
            val y = command.getY(i)
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
    }
    if (minX > maxX || minY > maxY) return ScRectItem(0f, 0f, 0f, 0f)
    return ScRectItem(minX, minY, maxX, maxY)
}

internal fun computeNineSliceScale(scalingGrid: ScRectItem, fullMatrix: ScMatrixItem): Pair<Float, Float> {
    val widthSheared = scalingGrid.width * fullMatrix.b
    val widthScaled = scalingGrid.width * fullMatrix.a
    val widthDistance = widthSheared * widthSheared + widthScaled * widthScaled
    val scaledWidth = if (widthDistance != 0f) scalingGrid.width / sqrt(widthDistance) else 1f

    val heightSheared = scalingGrid.height * fullMatrix.c
    val heightScaled = scalingGrid.height * fullMatrix.d
    val heightDistance = heightSheared * heightSheared + heightScaled * heightScaled
    val scaledHeight = if (heightDistance != 0f) scalingGrid.height / sqrt(heightDistance) else 1f

    return scaledWidth to scaledHeight
}

internal fun nineSliceLocalXY(
    rawX: Float,
    rawY: Float,
    safeArea: ScRectItem,
    shapeBounds: ScRectItem,
    scaledWidth: Float,
    scaledHeight: Float
): Pair<Float, Float> {
    var x = rawX
    if (x <= safeArea.left) {
        x = minOf(safeArea.midX, shapeBounds.left + (x - shapeBounds.left) * scaledWidth)
    } else if (x >= safeArea.right) {
        x = maxOf(safeArea.midX, shapeBounds.right + (x - shapeBounds.right) * scaledWidth)
    }

    var y = rawY
    if (y <= safeArea.top) {
        y = minOf(safeArea.midY, shapeBounds.top + (y - shapeBounds.top) * scaledHeight)
    } else if (y >= safeArea.bottom) {
        y = maxOf(safeArea.midY, shapeBounds.bottom + (y - shapeBounds.bottom) * scaledHeight)
    }

    return x to y
}
