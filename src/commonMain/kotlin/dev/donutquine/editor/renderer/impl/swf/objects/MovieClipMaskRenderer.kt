package dev.donutquine.editor.renderer.impl.swf.objects

import ui.ScMatrixBankItem
import ui.ScMatrixItem
import ui.ScMovieClipModifierType
import ui.ScObjectItem

/**
 * Сбор геометрии ФОРМЫ маски — сегмента children мувиклипа между модификаторами
 * MASK_BEGIN и MASKED_BEGIN (см. ScMovieClipModifierType в ScModels.kt и
 * Tag.MODIFIER_STATE_2/3/4 в оригинальной библиотеке). Сама маска не рисуется как
 * видимый контент — эта функция только собирает треугольники (уже с применённой
 * матрицей, в той же системе координат, что и обычные draw call'ы в
 * ScMovieClipRenderer.kt), из которых потом строится Compose Path для clipPath.
 *
 * Упрощение: если внутри формы маски встречается СВОЙ вложенный маскинг (маска внутри
 * маски) — он игнорируется, вся геометрия такого поддерева просто идёт в контур как
 * есть. Полноценный вложенный стенсил (как в оригинальном GL-рендерере,
 * old_cdoe/.../EditorStage.java, с реальным stencil-буфером и ref-масками) здесь не
 * реализован — это редкий случай для формы самой маски.
 */
internal fun collectMaskTriangles(
    objectId: Int,
    matrix: ScMatrixItem,
    objectsById: Map<Int, ScObjectItem>,
    matrixBanks: List<ScMatrixBankItem>,
    modifiersById: Map<Int, ScMovieClipModifierType>,
    useStrip: Boolean,
    timeSeconds: Float,
    depth: Int,
    output: MutableList<FloatArray>
) {
    if (depth > 16) return
    val obj = objectsById[objectId] ?: return

    when (obj.type) {
        "Shape" -> {
            for (command in obj.shapeCommands) {
                val vertexCount = command.vertexCount
                val triangleCount = command.triangleCount
                if (vertexCount < 3 || triangleCount <= 0) continue

                val xs = FloatArray(vertexCount)
                val ys = FloatArray(vertexCount)
                for (i in 0 until vertexCount) {
                    xs[i] = matrix.applyX(command.getX(i), command.getY(i))
                    ys[i] = matrix.applyY(command.getX(i), command.getY(i))
                }

                // Та же триангуляция (FAN/STRIP), что и у видимых Shape — форма маски
                // должна совпадать с тем, как эти же вершины были бы отрисованы обычно.
                for (t in 0 until triangleCount) {
                    val i0 = if (useStrip) t else 0
                    val i1 = t + 1
                    val i2 = t + 2
                    if (i2 >= vertexCount) continue
                    output.add(floatArrayOf(xs[i0], ys[i0], xs[i1], ys[i1], xs[i2], ys[i2]))
                }
            }
        }

        "MovieClip" -> {
            if (obj.mcFrames.isEmpty()) return
            val fps = obj.fps.coerceAtLeast(1)
            val frameIndex = if (obj.mcFrames.size <= 1) 0
                else (timeSeconds * fps).toInt().mod(obj.mcFrames.size)
            val frame = obj.mcFrames[frameIndex]
            val bank = matrixBanks.getOrNull(obj.matrixBankIndex)

            for (element in frame.elements) {
                val child = obj.mcChildren.getOrNull(element.childIndex) ?: continue
                if (child.blend and 64 != 0) continue
                // Модификаторы внутри формы маски (маска в маске) — см. упрощение в
                // комментарии над функцией: просто пропускаем маркер, не меняя режим.
                if (modifiersById[child.id] != null) continue

                val childMatrix = bank?.matrices?.getOrNull(element.matrixIndex) ?: IDENTITY_MATRIX
                collectMaskTriangles(
                    objectId = child.id,
                    matrix = composeMatrix(matrix, childMatrix),
                    objectsById = objectsById,
                    matrixBanks = matrixBanks,
                    modifiersById = modifiersById,
                    useStrip = useStrip,
                    timeSeconds = timeSeconds,
                    depth = depth + 1,
                    output = output
                )
            }
        }

        else -> return
    }
}
