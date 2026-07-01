import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import dev.donutquine.editor.assets.SupercellTextureAssetFileLoader
import dev.donutquine.editor.assets.SupercellSWFAssetFileLoader
import dev.donutquine.utilities.ImageUtils
import dev.donutquine.utilities.rgbaBytesToArgbInts
import team.nulls.ntengine.assets.KhronosTextureDataLoader
import java.awt.Cursor
import ui.GlassSidebar
import ui.GlassViewport
import ui.GlassTimelinePanel
import ui.OpenedTab
import ui.ScObjectItem
import ui.ScTextureItem
import ui.ScMatrixBankItem
import ui.ScMatrixItem
import ui.ScColorTransformItem
import ui.ScMovieClipChildItem
import ui.ScMovieClipFrameItem
import ui.ScMovieClipFrameElementItem
import ui.ScRectItem
import dev.donutquine.editor.renderer.impl.swf.objects.ScMovieClipView
import dev.donutquine.editor.renderer.impl.swf.objects.rememberMovieClipController
import java.nio.ByteBuffer
import java.nio.IntBuffer

fun bufferToIntArray(buffer: Any, width: Int, height: Int): IntArray {
    return when (buffer) {
        is IntBuffer -> {
            buffer.rewind()
            val ints = IntArray(buffer.remaining())
            buffer.get(ints)
            ints
        }
        is ByteBuffer -> {
            buffer.rewind()
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            rgbaBytesToArgbInts(width, height, bytes)
        }
        else -> IntArray(width * height)
    }
}

fun convert16BitToArgb(width: Int, height: Int, bytes: ByteArray, pixelTypeName: String): IntArray {
    val pixels = IntArray(width * height)
    val isRgba4 = pixelTypeName.contains("RGBA4", ignoreCase = true) || pixelTypeName.contains("4444", ignoreCase = true)
    for (i in pixels.indices) {
        val b1 = bytes[i * 2].toInt() and 0xFF
        val b2 = bytes[i * 2 + 1].toInt() and 0xFF
        val val16 = (b2 shl 8) or b1
        if (isRgba4) {
            val r = (val16 ushr 12) and 0x0F
            val g = (val16 ushr 8) and 0x0F
            val b = (val16 ushr 4) and 0x0F
            val a = val16 and 0x0F
            pixels[i] = ((a * 17) shl 24) or ((r * 17) shl 16) or ((g * 17) shl 8) or (b * 17)
        } else {
            val r = (val16 ushr 11) and 0x1F
            val g = (val16 ushr 5) and 0x3F
            val b = val16 and 0x1F
            pixels[i] = (0xFF shl 24) or ((r * 255 / 31) shl 16) or ((g * 255 / 63) shl 8) or (b * 255 / 31)
        }
    }
    return pixels
}

fun convert8BitToArgb(width: Int, height: Int, bytes: ByteArray): IntArray {
    val pixels = IntArray(width * height)
    for (i in pixels.indices) {
        val gray = bytes[i].toInt() and 0xFF
        pixels[i] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
    }
    return pixels
}

fun decodeTextureToBitmap(width: Int, height: Int, rawBuffer: Any?, ktxData: ByteArray?, pixelTypeStr: String): ImageBitmap? {
    if (rawBuffer != null) {
        val argbPixels = bufferToIntArray(rawBuffer, width, height)
        return ImageUtils.createBitmap(width, height, argbPixels, false)
    } else if (ktxData != null) {
        val ktx = KhronosTextureDataLoader.decodeKtx(ktxData)
        if (ktx.glType != 0 && ktx.glFormat != 0) {
            val ktxBytes = ktx.levels[0]
            val size32 = ktx.width * ktx.height * 4
            val size16 = ktx.width * ktx.height * 2
            val argbPixels = try {
                when {
                    ktxBytes.size >= size32 -> rgbaBytesToArgbInts(ktx.width, ktx.height, ktxBytes)
                    ktxBytes.size >= size16 -> convert16BitToArgb(ktx.width, ktx.height, ktxBytes, pixelTypeStr)
                    else -> convert8BitToArgb(ktx.width, ktx.height, ktxBytes)
                }
            } catch (e: Exception) { null }
            if (argbPixels != null) {
                return ImageUtils.createBitmap(ktx.width, ktx.height, argbPixels, false)
            }
        } else {
            return ImageUtils.decompressKtx(ktx)
        }
    }
    return null
}

@Suppress("UnusedBoxWithConstraintsScope")
@Composable
fun App(
    onTitleChanged: (String) -> Unit,
    onExit: () -> Unit,
    triggerOpenFile: Boolean,
    onOpenFileHandled: () -> Unit,
    triggerCloseFile: Boolean,
    onCloseFileHandled: () -> Unit,
    triggerCloseAllFiles: Boolean,
    onCloseAllFilesHandled: () -> Unit
) {
    val openedTabs = remember { mutableStateListOf<OpenedTab>() }
    var activeTabIndex by remember { mutableStateOf(-1) }

    var sidebarWidth by remember { mutableStateOf(320.dp) }

    val activeTab = if (activeTabIndex in openedTabs.indices) openedTabs[activeTabIndex] else null

    LaunchedEffect(activeTab) {
        if (activeTab != null) {
            onTitleChanged(activeTab.name)
        } else {
            onTitleChanged("SC Editor 1.6.2")
        }
    }

    val openFileLambda = {
        openFilePicker { path ->
            if (path != null) {
                val fileName = path.substringAfterLast('\\').substringAfterLast('/')

                try {
                    val texturesList = mutableListOf<ScTextureItem>()
                    val objectsList = mutableListOf<ScObjectItem>()
                    val matrixBanksList = mutableListOf<ScMatrixBankItem>()
                    var loadedImage: ImageBitmap? = null
                    var statusText = "Загрузка..."
                    var containerVersion = 1

                    if (path.endsWith(".sctx", ignoreCase = true)) {
                        val texture = SupercellTextureAssetFileLoader.loadInternal(path)
                        val pixelTypeStr = texture.pixelType?.toString() ?: "RGBA8"
                        statusText = "Файл: $fileName\nТекстура SCTX ${texture.width}x${texture.height}\nФормат: $pixelTypeStr"
                        texturesList.add(ScTextureItem(0, texture.width, texture.height, pixelTypeStr, bitmap = null))

                        if (texture.mipMaps != null && texture.mipMaps.isNotEmpty()) {
                            val firstMipMap = texture.mipMaps[0]
                            val rawBytes = firstMipMap.data
                            if (rawBytes != null) {
                                val isCompressed = pixelTypeStr.contains("ASTC", ignoreCase = true) || pixelTypeStr.contains("ETC", ignoreCase = true)
                                if (isCompressed) {
                                    val ktx = team.nulls.ntengine.assets.KhronosTexture(
                                        0, 1, 0, if (pixelTypeStr.contains("4x4")) 0x93B0 else 0x93B7,
                                        0x1908, texture.width, texture.height, arrayOf(rawBytes)
                                    )
                                    loadedImage = ImageUtils.decompressKtx(ktx)
                                } else {
                                    val size32 = firstMipMap.width * firstMipMap.height * 4
                                    val size16 = firstMipMap.width * firstMipMap.height * 2
                                    val argbPixels = try {
                                        when {
                                            rawBytes.size >= size32 -> rgbaBytesToArgbInts(firstMipMap.width, firstMipMap.height, rawBytes)
                                            rawBytes.size >= size16 -> convert16BitToArgb(firstMipMap.width, firstMipMap.height, rawBytes, pixelTypeStr)
                                            else -> convert8BitToArgb(firstMipMap.width, firstMipMap.height, rawBytes)
                                        }
                                    } catch (e: Exception) { IntArray(firstMipMap.width * firstMipMap.height) }
                                    loadedImage = ImageUtils.createBitmap(firstMipMap.width, firstMipMap.height, argbPixels, false)
                                }
                            }
                            texturesList[0] = texturesList[0].copy(bitmap = loadedImage)
                        }
                    } else if (path.endsWith(".sc", ignoreCase = true)) {
                        val swf = SupercellSWFAssetFileLoader.loadInternal(path)
                        containerVersion = swf.containerVersion
                        val tCount = swf.textures?.size ?: 0
                        val sCount = swf.shapes?.size ?: 0
                        val mcCount = swf.movieClips?.size ?: 0
                        val exportsCount = swf.exports?.size ?: 0
                        val tfCount = swf.textFields?.size ?: 0

                        statusText = "Файл: $fileName\nВерсия контейнера: ${swf.containerVersion}\n" +
                            "Текстур: $tCount | Экспортов: $exportsCount | Мувиклипов: $mcCount | Форм: $sCount | Текстовых полей: $tfCount"

                        for (i in 0 until tCount) {
                            val tex = swf.textures[i]
                            val rawBuffer = tex.getPixels()
                            val ktxData = tex.getKtxData()
                            val typeStr = tex.type?.toString() ?: "RGBA8"
                            val bitmap = decodeTextureToBitmap(tex.width, tex.height, rawBuffer, ktxData, typeStr)

                            texturesList.add(ScTextureItem(i, tex.width, tex.height, typeStr, bitmap))
                        }

                        swf.movieClips?.forEach { mc ->
                            val mcChildren = mc.children.map { child ->
                                ScMovieClipChildItem(id = child.id(), blend = child.blend(), name = child.name())
                            }
                            val mcFrames = mc.frames.map { frame ->
                                ScMovieClipFrameItem(
                                    label = frame.label,
                                    elements = frame.elements.map { el ->
                                        ScMovieClipFrameElementItem(el.childIndex(), el.matrixIndex(), el.colorTransformIndex())
                                    }
                                )
                            }
                            objectsList.add(
                                ScObjectItem(
                                    id = mc.id,
                                    name = mc.exportName ?: "",
                                    type = "MovieClip",
                                    fps = mc.fps,
                                    matrixBankIndex = mc.matrixBankIndex,
                                    mcChildren = mcChildren,
                                    mcFrames = mcFrames,
                                    scalingGrid = mc.scalingGrid?.let { grid ->
                                        ScRectItem(grid.left, grid.top, grid.right, grid.bottom)
                                    }
                                )
                            )
                        }

                        swf.shapes?.forEach { shape ->
                            objectsList.add(ScObjectItem(shape.id, "", "Shape", shapeCommands = shape.commands))
                        }

                        swf.textFields?.forEach { tf ->
                            objectsList.add(ScObjectItem(tf.id, "", "TextField"))
                        }

                        swf.matrixBanks?.forEach { bank ->
                            matrixBanksList.add(
                                ScMatrixBankItem(
                                    matrices = bank.matrices.map { m -> ScMatrixItem(m.a, m.b, m.c, m.d, m.x, m.y) },
                                    colorTransforms = bank.colorTransforms.map { ct ->
                                        ScColorTransformItem(
                                            redMultiplier = ct.redMultiplier,
                                            greenMultiplier = ct.greenMultiplier,
                                            blueMultiplier = ct.blueMultiplier,
                                            alpha = ct.alpha,
                                            redAddition = ct.redAddition,
                                            greenAddition = ct.greenAddition,
                                            blueAddition = ct.blueAddition
                                        )
                                    }
                                )
                            )
                        }
                    }

                    openedTabs.add(OpenedTab(fileName, path, containerVersion, texturesList, objectsList, -1, 0, statusText, matrixBanksList))
                    activeTabIndex = openedTabs.size - 1
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    LaunchedEffect(triggerOpenFile) {
        if (triggerOpenFile) {
            onOpenFileHandled()
            openFileLambda()
        }
    }

    LaunchedEffect(triggerCloseFile) {
        if (triggerCloseFile) {
            onCloseFileHandled()
            if (activeTabIndex in openedTabs.indices) {
                openedTabs.removeAt(activeTabIndex)
                activeTabIndex = if (openedTabs.isEmpty()) -1 else openedTabs.size - 1
            }
        }
    }

    LaunchedEffect(triggerCloseAllFiles) {
        if (triggerCloseAllFiles) {
            onCloseAllFilesHandled()
            openedTabs.clear()
            activeTabIndex = -1
        }
    }

    MaterialTheme {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.linearGradient(colors = listOf(Color(0xFFE2E8F0), Color(0xFFF8FAFC))))
                .padding(12.dp)
        ) {
            val sidebarMaxWidth = maxWidth - 200.dp

            Column(modifier = Modifier.fillMaxSize()) {

                Spacer(modifier = Modifier.height((-3).dp))

                if (openedTabs.isNotEmpty()) {
                    ui.GlassFileTabBar(
                        openedTabs = openedTabs,
                        activeTabIndex = activeTabIndex,
                        onTabSelect = { activeTabIndex = it },
                        onTabClose = { index ->
                            openedTabs.removeAt(index)
                            activeTabIndex = if (openedTabs.isEmpty()) -1 else openedTabs.size - 1
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }


                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (activeTab != null) {
                        GlassSidebar(
                            openedTab = activeTab,
                            onObjectSelected = { objIndex ->
                                openedTabs[activeTabIndex] = activeTab.copy(activeObjectIndex = objIndex, viewMode = "OBJECT")
                            },
                            onTextureSelected = { texIndex ->
                                openedTabs[activeTabIndex] = activeTab.copy(activeTextureIndex = texIndex, viewMode = "TEXTURE")
                            },
                            modifier = Modifier.width(sidebarWidth).fillMaxHeight()
                        )

                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .fillMaxHeight()
                                .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)))
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        sidebarWidth = (sidebarWidth + dragAmount.x.toDp()).coerceIn(220.dp, sidebarMaxWidth)
                                    }
                                }
                        )
                    }


                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        val selectedObj = if (activeTab != null && activeTab.activeObjectIndex in activeTab.objects.indices) {
                            activeTab.objects[activeTab.activeObjectIndex]
                        } else null

                        val currentTexture = if (activeTab != null && activeTab.textures.isNotEmpty()) {
                            val texIndex = activeTab.activeTextureIndex.coerceIn(activeTab.textures.indices)
                            activeTab.textures[texIndex]
                        } else null

                        val viewMode = activeTab?.viewMode ?: "OBJECT"

                        val isShapeSelected = viewMode == "OBJECT" && selectedObj?.type == "Shape" && selectedObj.shapeCommands.isNotEmpty()
                        val isMovieClipSelected = viewMode == "OBJECT" && selectedObj?.type == "MovieClip" && selectedObj.mcFrames.isNotEmpty()
                        val showTextureCanvas = !isShapeSelected && !isMovieClipSelected

                        val mcController = if (isMovieClipSelected && selectedObj != null) {
                            rememberMovieClipController(selectedObj)
                        } else null

                        GlassViewport(
                            loadedImage = if (showTextureCanvas) currentTexture?.bitmap else null,
                            infoLabel = when {
                                isShapeSelected -> "Shape ${selectedObj?.id} · команд: ${selectedObj?.shapeCommands?.size}"
                                isMovieClipSelected -> {
                                    val nameSuffix = if (!selectedObj?.name.isNullOrEmpty()) " · ${selectedObj?.name}" else ""
                                    "MovieClip ${selectedObj?.id}$nameSuffix · ${selectedObj?.fps} fps · кадр ${(mcController?.currentFrame ?: 0) + 1}/${selectedObj?.mcFrames?.size}"
                                }
                                currentTexture != null -> "Texture ${currentTexture.index} · ${currentTexture.width}×${currentTexture.height} · ${currentTexture.format}"
                                else -> null
                            },
                            content = when {
                                isShapeSelected && activeTab != null && selectedObj != null -> {
                                    {
                                        dev.donutquine.editor.renderer.impl.swf.objects.ScShapeView(
                                            commands = selectedObj.shapeCommands,
                                            textures = activeTab.textures,
                                            useStrip = activeTab.containerVersion >= 5,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                isMovieClipSelected && activeTab != null && selectedObj != null && mcController != null -> {
                                    {
                                        ScMovieClipView(
                                            movieClip = selectedObj,
                                            objectsById = activeTab.objectsById,
                                            matrixBanks = activeTab.matrixBanks,
                                            textures = activeTab.textures,
                                            useStrip = activeTab.containerVersion >= 5,
                                            timeSeconds = mcController.timeSeconds,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                else -> null
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )

                        if (isMovieClipSelected && selectedObj != null && mcController != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            GlassTimelinePanel(
                                frameCount = selectedObj.mcFrames.size,
                                currentFrame = mcController.currentFrame,
                                isPlaying = mcController.isPlaying,
                                onFrameChange = { mcController.setFrame(it) },
                                onTogglePlaying = { mcController.togglePlaying() },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
