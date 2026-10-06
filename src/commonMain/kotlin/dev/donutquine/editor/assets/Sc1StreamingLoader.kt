package dev.donutquine.editor.assets

import com.luklaaa.sceditor.AppLog
import dev.donutquine.streams.ByteStream
import dev.donutquine.swf.Export
import dev.donutquine.swf.ScMatrixBank
import dev.donutquine.swf.Tag
import dev.donutquine.swf.movieclips.MovieClipModifierOriginal
import dev.donutquine.swf.movieclips.MovieClipOriginal
import dev.donutquine.swf.shapes.ShapeOriginal
import dev.donutquine.swf.textfields.TextFieldOriginal
import dev.donutquine.swf.textures.SWFTexture
import org.sevenzip.compression.LZMA.Decoder
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

internal class ByteBufferInputStream(private val buffer: ByteBuffer) : InputStream() {
    override fun read(): Int = if (buffer.hasRemaining()) buffer.get().toInt() and 0xFF else -1

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (!buffer.hasRemaining()) return -1
        val n = minOf(len, buffer.remaining())
        buffer.get(b, off, n)
        return n
    }
}

object Sc1StreamingLoader {
    private const val SC_MAGIC = 0x5343
    private const val TEXTURE_EXTENSION = "_tex.sc"
    private const val DEFAULT_HIGHRES_SUFFIX = "_highres"
    private const val DEFAULT_LOWRES_SUFFIX = "_lowres"
    private const val HEADER_WINDOW = 8 shl 20

    private class Unpacked(val version: Int, val data: ByteBuffer)
    private class TagRef(val tag: Tag, val offset: Int, val length: Int)

    private val TEXTURE_TAGS = setOf(
        Tag.TEXTURE, Tag.TEXTURE_2, Tag.TEXTURE_3, Tag.TEXTURE_4, Tag.TEXTURE_5, Tag.TEXTURE_6,
        Tag.TEXTURE_7, Tag.TEXTURE_8, Tag.KHRONOS_TEXTURE, Tag.TEXTURE_FILE_REFERENCE
    )

    fun load(path: String, onStage: (String) -> Unit = {}): SwfData? {
        val file = File(path)
        if (!file.isFile) return null

        onStage("Decoding: Unpacking")
        val unpacked = unpackToMapped(path) ?: return null
        val data = unpacked.data
        AppLog.i("SC1 streaming loader: container v${unpacked.version}, unpacked ${data.limit() / 1024} KB, ${AppLog.memory()}")

        val window = ByteArray(minOf(data.limit(), HEADER_WINDOW))
        data.duplicate().also { it.position(0) }.get(window)
        val header = ByteStream(window)

        val shapeCount = header.readShort()
        val movieClipCount = header.readShort()
        val textureCount = header.readShort()
        val textFieldCount = header.readShort()
        val matrixCount = header.readShort()
        val colorTransformCount = header.readShort()

        val matrixBanks = ArrayList<ScMatrixBank>()
        var matrixBank = ScMatrixBank(matrixCount, colorTransformCount)
        matrixBanks.add(matrixBank)

        header.skip(5)

        val exportCount = header.readShort()
        val exportIds = header.readShortArray(exportCount)
        val exportNames = arrayOfNulls<String>(exportCount)
        for (i in 0 until exportCount) exportNames[i] = header.readAscii()

        val exports = ArrayList<Export>(exportCount)
        val exportNameById = HashMap<Int, String>(exportCount * 2)
        for (i in 0 until exportCount) {
            val id = exportIds[i].toInt() and 0xFFFF
            exports.add(Export(id, exportNames[i]))
            exportNames[i]?.let { exportNameById[id] = it }
        }

        val shapes = ArrayList<ShapeOriginal>(shapeCount)
        repeat(shapeCount) { shapes.add(ShapeOriginal()) }
        val textures = ArrayList<SWFTexture>(textureCount)
        repeat(textureCount) { textures.add(SWFTexture()) }
        val textFields = ArrayList<TextFieldOriginal>(textFieldCount)
        repeat(textFieldCount) { textFields.add(TextFieldOriginal()) }
        var modifiers: ArrayList<MovieClipModifierOriginal>? = null
        val clipRefs = ArrayList<TagRef>(movieClipCount)

        var useExternalTexture = false
        var useUncommonResolution = false
        var uncommonTexturePath: String? = null
        var highresSuffix = DEFAULT_HIGHRES_SUFFIX
        var lowresSuffix = DEFAULT_LOWRES_SUFFIX

        var loadedShapes = 0
        var loadedTextures = 0
        var loadedTextFields = 0
        var loadedMatrices = 0
        var loadedColorTransforms = 0
        var loadedModifiers = 0

        val fileName = file.name
        val scratch = ByteArray(256)
        val small = ByteStream(scratch)
        fun smallStream(offset: Int, length: Int): ByteStream {
            if (length > scratch.size) return ByteStream(readPayload(data, offset, length))
            val view = data.duplicate()
            view.position(offset)
            view.get(scratch, 0, length)
            small.setData(scratch)
            return small
        }

        onStage("Decoding: reading tags")
        var pos = header.position
        val limit = data.limit()
        val textureGetter = java.util.function.Function<Int, SWFTexture> { index -> textures[index] }
        val fontNameReader = java.util.function.Function<ByteStream, String?> { stream -> stream.readAscii() }

        while (true) {
            if (pos + 5 > limit) throw IllegalStateException("Unexpected end of $fileName (no EOF tag)")
            val tagId = data.get(pos).toInt() and 0xFF
            val length = data.getInt(pos + 1)
            pos += 5
            if (length < 0) throw IllegalStateException("Negative tag length. Tag $tagId, $fileName")
            val tagValue = Tag.values().getOrNull(tagId)
            if (tagValue == null) {
                AppLog.w("Unknown tag $tagId in $fileName - skipped")
                pos += length
                continue
            }

            when (tagValue) {
                Tag.EOF -> break
                in TEXTURE_TAGS -> {
                    check(loadedTextures < textures.size) { "Trying to load too many textures from $fileName" }
                    val texture = textures[loadedTextures]
                    texture.setIndex(loadedTextures)
                    loadedTextures++
                    texture.load(ByteStream(readPayload(data, pos, length)), tagValue, !useExternalTexture)
                }
                Tag.SHAPE, Tag.SHAPE_2 -> {
                    check(loadedShapes < shapes.size) { "Trying to load too many shapes from $fileName" }
                    shapes[loadedShapes++].load(ByteStream(readPayload(data, pos, length)), tagValue, textureGetter, fileName)
                }
                Tag.MOVIE_CLIP, Tag.MOVIE_CLIP_2, Tag.MOVIE_CLIP_3, Tag.MOVIE_CLIP_4, Tag.MOVIE_CLIP_5,
                Tag.MOVIE_CLIP_6, Tag.MOVIE_CLIP_7 -> {
                    check(clipRefs.size < movieClipCount) { "Trying to load too many MovieClips from $fileName" }
                    clipRefs.add(TagRef(tagValue, pos, length))
                }
                Tag.TEXT_FIELD, Tag.TEXT_FIELD_2, Tag.TEXT_FIELD_3, Tag.TEXT_FIELD_4, Tag.TEXT_FIELD_5,
                Tag.TEXT_FIELD_6, Tag.TEXT_FIELD_7, Tag.TEXT_FIELD_8, Tag.TEXT_FIELD_9 -> {
                    check(loadedTextFields < textFields.size) { "Trying to load too many TextFields from $fileName" }
                    textFields[loadedTextFields++].load(ByteStream(readPayload(data, pos, length)), tagValue, fontNameReader)
                }
                Tag.MATRIX, Tag.MATRIX_PRECISE ->
                    matrixBank.getMatrix(loadedMatrices++).load(smallStream(pos, length), tagValue == Tag.MATRIX_PRECISE)
                Tag.COLOR_TRANSFORM ->
                    matrixBank.getColorTransform(loadedColorTransforms++).read(smallStream(pos, length))
                Tag.HALF_SCALE_POSSIBLE -> Unit
                Tag.USE_EXTERNAL_TEXTURE -> useExternalTexture = true
                Tag.USE_UNCOMMON_RESOLUTION -> {
                    useUncommonResolution = true
                    val withoutExtension = path.substring(0, path.length - 3)
                    val highres = withoutExtension + highresSuffix + TEXTURE_EXTENSION
                    val lowres = withoutExtension + lowresSuffix + TEXTURE_EXTENSION
                    uncommonTexturePath = if (!File(highres).exists() && File(lowres).exists()) lowres else highres
                }
                Tag.EXTERNAL_FILES_SUFFIXES -> {
                    val stream = ByteStream(readPayload(data, pos, length))
                    highresSuffix = stream.readAscii()
                    lowresSuffix = stream.readAscii()
                }
                Tag.MOVIE_CLIP_MODIFIERS -> {
                    val count = ByteStream(readPayload(data, pos, length)).readShort()
                    val list = ArrayList<MovieClipModifierOriginal>(count)
                    repeat(count) { list.add(MovieClipModifierOriginal()) }
                    modifiers = list
                }
                Tag.MODIFIER_STATE_2, Tag.MODIFIER_STATE_3, Tag.MODIFIER_STATE_4 ->
                    modifiers!![loadedModifiers++].load(ByteStream(readPayload(data, pos, length)), tagValue)
                Tag.EXTRA_MATRIX_BANK -> {
                    val stream = ByteStream(readPayload(data, pos, length))
                    val matrices = stream.readShort()
                    val colors = stream.readShort()
                    matrixBank = ScMatrixBank(matrices, colors)
                    matrixBanks.add(matrixBank)
                    loadedMatrices = 0
                    loadedColorTransforms = 0
                }
                else -> Unit
            }
            pos += length
        }

        if (loadedMatrices != matrixBank.matrixCount ||
            loadedColorTransforms != matrixBank.colorTransformCount ||
            clipRefs.size != movieClipCount ||
            loadedShapes != shapes.size ||
            loadedTextFields != textFields.size
        ) {
            throw IllegalStateException("Didn't load whole .sc properly. $fileName")
        }
        AppLog.i("  SC1: tags read: $shapeCount shapes, $movieClipCount movie clips, $textFieldCount text fields, $textureCount textures, ${matrixBanks.size} matrix banks (${AppLog.memory()})")

        var textureFile: ByteBuffer? = null
        var textureRefs: List<TagRef> = emptyList()
        if (useExternalTexture) {
            val texturePath = if (useUncommonResolution) uncommonTexturePath!! else path.substring(0, path.length - 3) + TEXTURE_EXTENSION
            val textureName = File(texturePath).name
            if (!File(texturePath).isFile) throw TextureFileMissingException(textureName)
            onStage("Decoding: unpacking $textureName")
            val unpackedTextures = unpackToMapped(texturePath)
                ?: throw IllegalStateException("Texture file $textureName is not an SC1 container")
            textureFile = unpackedTextures.data
            textureRefs = scanTextureTags(unpackedTextures.data)
            if (textureRefs.size != textureCount) {
                throw IllegalStateException("Texture count in .sc and _tex.sc doesn't match: $fileName")
            }
        }

        val finalTextureFile = textureFile
        val finalTextureRefs = textureRefs
        return SwfData(
            containerVersion = unpacked.version,
            textureCount = textureCount,
            openTexture = { index ->
                val texture: SWFTexture
                val ownsData: Boolean
                if (finalTextureFile != null) {
                    val ref = finalTextureRefs[index]
                    texture = SWFTexture()
                    texture.setIndex(index)
                    texture.load(ByteStream(readPayload(finalTextureFile, ref.offset, ref.length)), ref.tag, true)
                    ownsData = false
                } else {
                    texture = textures[index]
                    ownsData = true
                }
                TextureSource(
                    width = texture.width,
                    height = texture.height,
                    typeName = texture.type?.toString(),
                    pixels = texture.getPixels(),
                    ktxData = texture.getKtxData(),
                    release = { if (ownsData) releaseTextureData(texture) }
                )
            },
            shapes = shapes,
            movieClipCount = clipRefs.size,
            movieClipAt = { index ->
                val ref = clipRefs[index]
                val clip = MovieClipOriginal()
                clip.load(ByteStream(readPayload(data, ref.offset, ref.length)), ref.tag, fileName)
                exportNameById[clip.id]?.let { clip.setExportName(it) }
                clip
            },
            textFields = textFields,
            exports = exports,
            movieClipModifiers = modifiers,
            matrixBanks = matrixBanks
        )
    }

    private fun scanTextureTags(data: ByteBuffer): List<TagRef> {
        val refs = ArrayList<TagRef>()
        var pos = 0
        val limit = data.limit()
        while (pos + 5 <= limit) {
            val tagId = data.get(pos).toInt() and 0xFF
            val length = data.getInt(pos + 1)
            pos += 5
            if (length < 0) throw IllegalStateException("Negative tag length in texture file")
            val tag = Tag.values().getOrNull(tagId)
            if (tag == Tag.EOF) break
            if (tag != null && tag in TEXTURE_TAGS) refs.add(TagRef(tag, pos, length))
            pos += length
        }
        return refs
    }

    private fun readPayload(data: ByteBuffer, offset: Int, length: Int): ByteArray {
        val bytes = ByteArray(length)
        val view = data.duplicate()
        view.position(offset)
        view.get(bytes)
        return bytes
    }

    private fun unpackToMapped(path: String): Unpacked? {
        val mapped = RandomAccessFile(File(path), "r").use { raf ->
            raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())
        }
        if (mapped.limit() < 12 || mapped.getShort(0).toInt() != SC_MAGIC) return null

        var pos = 2
        var version = mapped.getInt(pos)
        pos += 4
        if (version == 4) {
            version = mapped.getInt(pos)
            pos += 4
        }
        if (version > 4) version = Integer.reverseBytes(version)
        if (version !in 1..3) return null

        val hashLength = mapped.getInt(pos)
        pos += 4 + hashLength

        val data = if (version == 1) lzmaToMapped(mapped, pos) else zstdDecompressFrameMapped(mapped, pos)
        return Unpacked(version, data)
    }

    private fun lzmaToMapped(source: ByteBuffer, offset: Int): ByteBuffer {
        val view = source.duplicate()
        view.position(offset)
        val properties = ByteArray(5)
        view.get(properties)
        var outSize = 0L
        for (i in 0 until 4) outSize = outSize or ((view.get().toLong() and 0xFF) shl (8 * i))

        val decoder = Decoder()
        check(decoder.setDecoderProperties(properties)) { "Incorrect LZMA properties" }

        val tmp = File.createTempFile("sc1_", ".bin")
        try {
            BufferedInputStream(ByteBufferInputStream(view), 1 shl 16).use { input ->
                BufferedOutputStream(FileOutputStream(tmp), 1 shl 20).use { output ->
                    check(decoder.code(input, output, outSize)) { "Error in LZMA data" }
                }
            }
            check(tmp.length() > 0) { "Decompressed LZMA data is empty" }
            val mapped = RandomAccessFile(tmp, "r").use { raf ->
                raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())
            }
            return mapped.order(ByteOrder.LITTLE_ENDIAN)
        } finally {
            if (!tmp.delete()) tmp.deleteOnExit()
        }
    }
}
