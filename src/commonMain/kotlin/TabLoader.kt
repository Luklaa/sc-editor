import androidx.compose.ui.graphics.ImageBitmap
import com.luklaaa.sceditor.AppLog
import dev.donutquine.editor.assets.Sc1StreamingLoader
import dev.donutquine.editor.assets.Sc2OffHeapLoader
import dev.donutquine.editor.assets.SupercellSWFAssetFileLoader
import dev.donutquine.editor.assets.SwfData
import dev.donutquine.editor.assets.SupercellTextureAssetFileLoader
import dev.donutquine.editor.assets.TextureFileMissingException
import dev.donutquine.utilities.ImageUtils
import dev.donutquine.utilities.rgbaBytesToArgbInts
import ui.OpenedTab
import ui.ScColorTransformItem
import ui.ScMatrixBankItem
import ui.ScMatrixItem
import ui.ScMovieClipChildItem
import ui.ScMovieClipFrameElementItem
import ui.ScMovieClipFrameItem
import ui.ScMovieClipModifierItem
import ui.ScMovieClipModifierType
import ui.ScObjectItem
import ui.ScRectItem
import ui.ScTextureItem
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class ExternalTexture(val path: String, val expectedName: String)

sealed class LoadOutcome {
    class Loaded(val tab: OpenedTab) : LoadOutcome()

    class NeedsTexture(val expectedName: String) : LoadOutcome()

    class Failed(val message: String) : LoadOutcome()
}

fun loadTab(
    path: String,
    externalTexture: ExternalTexture? = null,
    onProgress: (String) -> Unit = {}
): LoadOutcome {
    val fileName = path.substringAfterLast('\\').substringAfterLast('/')
    var stage = "starting"
    val tracked: (String) -> Unit = {
        stage = it
        AppLog.i("  $it (${AppLog.memory()})")
        onProgress(it)
    }
    System.gc()
    val startedAt = System.currentTimeMillis()
    val files = listOf(path, path.dropLast(3) + "_tex.sc").distinct().mapNotNull { describeScFile(it) }.joinToString("; ")
    AppLog.i("Opening $fileName${if (externalTexture != null) " (+ external texture ${externalTexture.expectedName})" else ""} | $files | ${AppLog.memory()}")
    return try {
        val tab = buildTab(path, fileName, externalTexture, tracked)
        AppLog.i("Opened $fileName in ${System.currentTimeMillis() - startedAt} ms | ${AppLog.memory()}")
        LoadOutcome.Loaded(tab)
    } catch (e: TextureFileMissingException) {
        AppLog.i("$fileName needs external texture file: ${e.expectedName}")
        LoadOutcome.NeedsTexture(e.expectedName)
    } catch (e: OutOfMemoryError) {
        e.printStackTrace()
        AppLog.e("OUT OF MEMORY while opening $fileName, stopped at: $stage | ${AppLog.memory()} | ${e.message}", e)
        val limitMb = Runtime.getRuntime().maxMemory() / (1024 * 1024)
        val details = listOf(path, path.dropLast(3) + "_tex.sc")
            .distinct()
            .mapNotNull { describeScFile(it) }
            .joinToString("; ")
        LoadOutcome.Failed(
            "Not enough memory to open $fileName (heap limit $limitMb MB; stopped at: $stage" +
                    (if (details.isEmpty()) "" else "; $details") + ")"
        )
    } catch (e: StackOverflowError) {
        e.printStackTrace()
        AppLog.e("Stack overflow while opening $fileName, stopped at: $stage", e)
        LoadOutcome.Failed("Failed to open $fileName (file structure is too deep)")
    } catch (e: Throwable) {
        e.printStackTrace()
        AppLog.e("Failed to open $fileName, stopped at: $stage", e)
        val reason = (e.cause?.message ?: e.message)?.lineSequence()?.firstOrNull()?.take(160)
        LoadOutcome.Failed("Failed to open $fileName" + if (reason.isNullOrBlank()) "" else ": $reason")
    }
}

suspend fun loadTabOnLargeStack(
    path: String,
    externalTexture: ExternalTexture?,
    onProgress: (String) -> Unit
): LoadOutcome = suspendCancellableCoroutine { cont ->
    val thread = Thread(
        null,
        { cont.resume(loadTab(path, externalTexture, onProgress)) },
        "sc-loader",
        64L * 1024 * 1024
    )
    thread.isDaemon = true
    thread.start()
}

private fun loadSwfData(path: String, externalTexture: ExternalTexture?, onStage: (String) -> Unit): SwfData {
    if (externalTexture == null) {
        try {
            Sc2OffHeapLoader.load(path, false, onStage)?.let { return it }
        } catch (e: OutOfMemoryError) {
            throw e
        } catch (e: Throwable) {
            e.printStackTrace()
           AppLog.e("SC2 off-heap loader failed, falling back (${AppLog.memory()})", e)
        }
    }
    try {
        val streamed = if (externalTexture != null) {
            SupercellSWFAssetFileLoader.withTextureCopy(path, externalTexture.path, externalTexture.expectedName) { copied ->
                Sc1StreamingLoader.load(copied, onStage)
            }
        } else {
            Sc1StreamingLoader.load(path, onStage)
        }
        if (streamed != null) return streamed
    } catch (e: OutOfMemoryError) {
        throw e
    } catch (e: TextureFileMissingException) {
        throw e
    } catch (e: Throwable) {
        e.printStackTrace()
        AppLog.e("SC1 streaming loader failed, falling back to the library loader (${AppLog.memory()})", e)
    }

    AppLog.i("Using the library loader")
    onStage("Decoding (library loader)")
    val swf = if (externalTexture != null) {
        SupercellSWFAssetFileLoader.loadWithTexture(path, externalTexture.path, externalTexture.expectedName)
    } else {
        SupercellSWFAssetFileLoader.loadInternal(path)
    }
    return SwfData.fromLibrary(swf)
}

private fun buildTab(
    path: String,
    fileName: String,
    externalTexture: ExternalTexture?,
    onProgress: (String) -> Unit
): OpenedTab {
    if (!path.endsWith(".sctx", ignoreCase = true) && !path.endsWith(".sc", ignoreCase = true)) {
        throw IllegalArgumentException("Unsupported file type (only .sc and .sctx are supported)")
    }

    val texturesList = mutableListOf<ScTextureItem>()
    val objectsList = mutableListOf<ScObjectItem>()
    val matrixBanksList = mutableListOf<ScMatrixBankItem>()
    val modifiersList = mutableListOf<ScMovieClipModifierItem>()
    var loadedImage: ImageBitmap? = null
    var statusText = "Loading..."
    var containerVersion = 1

    if (path.endsWith(".sctx", ignoreCase = true)) {
        val texture = SupercellTextureAssetFileLoader.loadInternal(path)
        val pixelTypeStr = texture.pixelType?.toString() ?: "RGBA8"
        statusText = "File: $fileName\nTexture SCTX ${texture.width}x${texture.height}\nFormat: $pixelTypeStr"
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
        onProgress("Decoding $fileName...")
        val swf = loadSwfData(path, externalTexture, onProgress)
        fun <T> safeList(block: () -> List<T>?): List<T> =
            try { block() ?: emptyList() } catch (e: NullPointerException) { emptyList() }

        containerVersion = swf.containerVersion
        val tCount = swf.textureCount
        val sCount = safeList { swf.shapes }.size
        val mcCount = swf.movieClipCount
        val exportsCount = safeList { swf.exports }.size
        val tfCount = safeList { swf.textFields }.size

        statusText = "File: $fileName\nContainer version: ${swf.containerVersion}\n" +
                "Textures: $tCount | Exports: $exportsCount | MovieClips: $mcCount | Shapes: $sCount | TextFields: $tfCount"

        for (i in 0 until tCount) {
            onProgress("Decoding textures ${i + 1}/$tCount...")
            val tex = swf.openTexture(i)
            val typeStr = tex.typeName ?: "RGBA8"
            val bitmap = try {
                decodeTextureToBitmap(tex.width, tex.height, tex.pixels, tex.ktxData, typeStr)
            } catch (e: Throwable) {
                e.printStackTrace()
                AppLog.e("Texture $i (${tex.width}x${tex.height}, $typeStr) failed to decode", e)
                null
            }

            texturesList.add(ScTextureItem(i, tex.width, tex.height, typeStr, bitmap))
            tex.release()
        }

        for (mcIndex in 0 until mcCount) {
            if (mcIndex % 500 == 0) onProgress("Reading movie clips $mcIndex/$mcCount...")
            val mc = swf.movieClipAt(mcIndex)
            val mcChildren = mc.children.map { child ->
                ScMovieClipChildItem(id = child.id(), blend = child.blend(), name = child.name())
            }
            val mcFrames = mc.frames.map { frame ->
                ScMovieClipFrameItem(
                    label = frame.label,
                    elements = packElements(frame.elements)
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

        safeList { swf.shapes }.forEach { shape ->
            objectsList.add(ScObjectItem(shape.id, "", "Shape", shapeCommands = shape.commands))
        }

        safeList { swf.textFields }.forEach { tf ->
            objectsList.add(ScObjectItem(tf.id, "", "TextField"))
        }

        safeList { swf.movieClipModifiers }.forEach { modifier ->
            val type = when (modifier.tag) {
                dev.donutquine.swf.Tag.MODIFIER_STATE_2 -> ScMovieClipModifierType.MASK_BEGIN
                dev.donutquine.swf.Tag.MODIFIER_STATE_3 -> ScMovieClipModifierType.MASKED_BEGIN
                dev.donutquine.swf.Tag.MODIFIER_STATE_4 -> ScMovieClipModifierType.MASK_END
                else -> null
            }
            if (type != null) {
                modifiersList.add(ScMovieClipModifierItem(modifier.id, type))
            }
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

    val defaultObjectIndex = objectsList
        .withIndex()
        .filter { it.value.name.isNotBlank() }
        .minByOrNull { it.value.name }
        ?.index ?: -1

    return OpenedTab(
        name = fileName,
        path = path,
        containerVersion = containerVersion,
        textures = texturesList,
        objects = objectsList,
        activeObjectIndex = defaultObjectIndex,
        activeTextureIndex = 0,
        statusText = statusText,
        matrixBanks = matrixBanksList,
        modifiers = modifiersList
    )
}

private fun describeScFile(path: String): String? = try {
    val file = java.io.File(path)
    if (!file.isFile) {
        null
    } else {
        val sizeMb = "%.1f".format(file.length() / (1024.0 * 1024.0))
        val header = ByteArray(10)
        val read = file.inputStream().use { it.read(header) }
        fun beInt(offset: Int) =
            ((header[offset].toInt() and 0xFF) shl 24) or ((header[offset + 1].toInt() and 0xFF) shl 16) or
                    ((header[offset + 2].toInt() and 0xFF) shl 8) or (header[offset + 3].toInt() and 0xFF)
        val container = if (read >= 6 && header[0] == 'S'.code.toByte() && header[1] == 'C'.code.toByte()) {
            var version = beInt(2)
            if (version == 4 && read >= 10) version = beInt(6)
            if (version > 4) version = Integer.reverseBytes(version)
            val codec = when (version) {
                1 -> "LZMA"
                2, 3 -> "Zstd"
                5, 6 -> "SC2"
                else -> "?"
            }
            ", v$version $codec"
        } else {
            ""
        }
        "${file.name}: $sizeMb MB$container"
    }
} catch (e: Throwable) {
    null
}

private class PackedElementList(private val data: IntArray) : AbstractList<ScMovieClipFrameElementItem>() {
    override val size: Int get() = data.size / 3

    override fun get(index: Int): ScMovieClipFrameElementItem {
        if (index < 0 || index >= size) throw IndexOutOfBoundsException("Index: $index, Size: $size")
        val base = index * 3
        return ScMovieClipFrameElementItem(data[base], data[base + 1], data[base + 2])
    }
}

private fun packElements(elements: List<dev.donutquine.swf.movieclips.MovieClipFrameElement>?): List<ScMovieClipFrameElementItem> {
    if (elements.isNullOrEmpty()) return emptyList()
    val data = IntArray(elements.size * 3)
    var i = 0
    for (el in elements) {
        data[i++] = el.childIndex()
        data[i++] = el.matrixIndex()
        data[i++] = el.colorTransformIndex()
    }
    return PackedElementList(data)
}
