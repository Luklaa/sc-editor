package dev.donutquine.editor.renderer.impl.swf.objects

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import dev.donutquine.swf.shapes.ShapeDrawBitmapCommand
import kotlinx.coroutines.delay
import ui.ScColorTransformItem
import ui.ScMatrixBankItem
import ui.ScMatrixItem
import ui.ScObjectItem
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

private val IDENTITY_MATRIX = ScMatrixItem()

// Композиция двух аффинных матриц: результат.apply(p) == parent.apply(child.apply(p)).
// См. dev.donutquine.swf.Matrix2x3#applyX/applyY:
//   applyX(x,y) = x*a + y*c + this.x
//   applyY(x,y) = y*d + x*b + this.y
private fun composeMatrix(parent: ScMatrixItem, child: ScMatrixItem): ScMatrixItem {
    return ScMatrixItem(
        a = parent.a * child.a + parent.c * child.b,
        c = parent.a * child.c + parent.c * child.d,
        b = parent.b * child.a + parent.d * child.b,
        d = parent.b * child.c + parent.d * child.d,
        x = parent.a * child.x + parent.c * child.y + parent.x,
        y = parent.b * child.x + parent.d * child.y + parent.y
    )
}

private fun ScMatrixItem.applyX(px: Float, py: Float) = px * a + py * c + x
private fun ScMatrixItem.applyY(px: Float, py: Float) = py * d + px * b + y

// Один "плоский" draw call — уже полностью посчитанные позиции вершин в системе
// координат корневого мувиклипа (до масштабирования под канву), готовые к отрисовке.
private class MovieClipDrawCall(
    val textureItem: ScTextureItem,
    val positions: FloatArray,
    val texCoords: FloatArray,
    val indices: ShortArray,
    val alpha: Float
)

// Рекурсивно обходит дерево MovieClip -> children -> (Shape | MovieClip | TextField),
// на каждом уровне накапливая аффинную трансформацию и alpha, и на листьях (Shape)
// генерирует draw call'ы с уже применённой трансформацией к вершинам.
// depth — защита от случайной цикличности ссылок (a содержит b, который содержит a).
private fun collectDrawCalls(
    objectId: Int,
    matrix: ScMatrixItem,
    alpha: Float,
    objectsById: Map<Int, ScObjectItem>,
    matrixBanks: List<ScMatrixBankItem>,
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
            for (command in obj.shapeCommands) {
                val textureItem = textures.getOrNull(command.textureIndex) ?: continue
                if (textureItem.bitmap == null) continue

                val vertexCount = command.vertexCount
                val triangleCount = command.triangleCount
                if (vertexCount < 3 || triangleCount <= 0) continue

                val positions = FloatArray(vertexCount * 2)
                val texCoords = FloatArray(vertexCount * 2)
                for (i in 0 until vertexCount) {
                    val localX = command.getX(i)
                    val localY = command.getY(i)
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

                output.add(MovieClipDrawCall(textureItem, positions, texCoords, indices, alpha))
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

            for (element in frame.elements) {
                val child = obj.mcChildren.getOrNull(element.childIndex) ?: continue
                val childMatrix = bank?.matrices?.getOrNull(element.matrixIndex) ?: IDENTITY_MATRIX
                val childColor: ScColorTransformItem? = bank?.colorTransforms?.getOrNull(element.colorTransformIndex)
                val childAlpha = alpha * ((childColor?.alpha ?: 255) / 255f)

                collectDrawCalls(
                    objectId = child.id,
                    matrix = composeMatrix(matrix, childMatrix),
                    alpha = childAlpha,
                    objectsById = objectsById,
                    matrixBanks = matrixBanks,
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
 */
@Composable
fun ScMovieClipView(
    movieClip: ScObjectItem,
    objectsById: Map<Int, ScObjectItem>,
    matrixBanks: List<ScMatrixBankItem>,
    textures: List<ScTextureItem>,
    useStrip: Boolean,
    timeSeconds: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val drawCalls = mutableListOf<MovieClipDrawCall>()
        collectDrawCalls(
            objectId = movieClip.id,
            matrix = IDENTITY_MATRIX,
            alpha = 1f,
            objectsById = objectsById,
            matrixBanks = matrixBanks,
            textures = textures,
            useStrip = useStrip,
            timeSeconds = timeSeconds,
            depth = 0,
            output = drawCalls
        )

        if (drawCalls.isEmpty()) return@Canvas

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

        for (call in drawCalls) {
            val bitmap = call.textureItem.bitmap ?: continue

            val scaledPositions = FloatArray(call.positions.size)
            for (i in call.positions.indices step 2) {
                scaledPositions[i] = call.positions[i] * scale + offsetX
                scaledPositions[i + 1] = call.positions[i + 1] * scale + offsetY
            }

            drawTexturedMesh(bitmap, scaledPositions, call.texCoords, call.indices, call.alpha)
        }
    }
}
