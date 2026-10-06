package dev.donutquine.editor.assets

import com.luklaaa.sceditor.AppLog
import com.supercell.swf.ExternalMatrixBanks
import com.supercell.swf.FBExports
import com.supercell.swf.FBMovieClips
import com.supercell.swf.FBMovieClipModifiers
import com.supercell.swf.FBResources
import com.supercell.swf.FBShapes
import com.supercell.swf.FBTexture
import com.supercell.swf.FBTextFields
import com.supercell.swf.FBTextureSets
import com.supercell.swf.Metadata
import dev.donutquine.swf.Export
import dev.donutquine.swf.Matrix2x3
import dev.donutquine.swf.ScCompressedMatrixBank
import dev.donutquine.swf.ScMatrixBank
import dev.donutquine.swf.TextureType
import dev.donutquine.swf.movieclips.MovieClipModifierOriginal
import dev.donutquine.swf.movieclips.MovieClipOriginal
import dev.donutquine.swf.shapes.ShapeOriginal
import dev.donutquine.swf.textfields.TextFieldOriginal
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

object Sc2OffHeapLoader {
    private const val SC_MAGIC = 0x5343
    private val LE = ByteOrder.LITTLE_ENDIAN

    fun load(path: String, preferLowres: Boolean = false, onStage: (String) -> Unit = {}): SwfData? {
        val file = File(path)
        if (!file.isFile || file.length() < 8) return null

        val mapped = RandomAccessFile(file, "r").use { raf ->
            raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())
        }

        if (mapped.getShort(0).toInt() != SC_MAGIC) return null
        var pos = 2
        var version = mapped.getInt(pos)
        pos += 4
        if (version == 4) {
            version = mapped.getInt(pos)
            pos += 4
        }
        if (version > 4) version = Integer.reverseBytes(version)
        if (version != 5 && version != 6) return null
        if (version == 6) pos += 2

        val start = mapped.duplicate()
        start.position(pos)
        val data = start.slice().order(LE)

        AppLog.i("SC2 off-heap loader: container v$version, file ${file.length() / 1024} KB, ${AppLog.memory()}")
        return Parser(data, version, preferLowres, onStage).parse()
    }

    private fun nested(buffer: ByteBuffer): ByteBuffer {
        val length = buffer.getInt()
        val begin = buffer.position()
        val view = buffer.duplicate()
        view.limit(begin + length)
        val slice = view.slice()
        buffer.position(begin + length)
        return slice.asReadOnlyBuffer()
    }

    private class Parser(
        private val data: ByteBuffer,
        private val containerVersion: Int,
        private val preferLowres: Boolean,
        private val onStage: (String) -> Unit
    ) {
        private fun stage(name: String) {
            AppLog.i("  SC2: $name (${AppLog.memory()})")
            onStage("Parsing: $name")
        }

        private lateinit var resources: FBResources
        private var matrixDataBuffers: Array<ByteBuffer?>? = null

        fun parse(): SwfData {
            stage("metadata")
            val metadata = Metadata.getRootAsMetadata(nested(data))

            stage("decompressing main container")
            val main = zstdDecompressFrameMapped(data, data.position()).order(LE)
            stage("main container decompressed, ${main.remaining() / 1024} KB")
            resources = FBResources.getRootAsFBResources(nested(main))

            stage("matrix banks")

            val externalMatrixBanks = getExternalMatrixBanks(metadata)
            val matrixBanks = if (externalMatrixBanks == null) {
                deserializeMatrixBanks()
            } else {
                deserializeExternalMatrixBanks(externalMatrixBanks, data.position())
            }

            stage("exports")
            val exports = deserializeExports(nested(main))
            stage("text fields")
            val textFields = deserializeTextFields(nested(main))
            stage("shapes")
            val shapes = deserializeShapes(nested(main))
            stage("movie clips")
            val movieClips = FBMovieClips.getRootAsFBMovieClips(nested(main))
            val movieClipCount = movieClips.clipsLength().toInt()
            stage("modifiers")
            val modifiers = deserializeModifiers(nested(main))
            val textureSets = FBTextureSets.getRootAsFBTextureSets(nested(main))
            stage("done: ${shapes.size} shapes, $movieClipCount movie clips, ${textFields.size} text fields, ${textureSets.textureSetsLength()} textures")

            return SwfData(
                containerVersion = containerVersion,
                textureCount = textureSets.textureSetsLength().toInt(),
                openTexture = { index -> openTexture(textureSets, index) },
                shapes = shapes,
                movieClipCount = movieClipCount,
                movieClipAt = { index -> createMovieClip(movieClips, index) },
                textFields = textFields,
                exports = exports,
                movieClipModifiers = modifiers,
                matrixBanks = matrixBanks
            )
        }

        private fun openTexture(sets: FBTextureSets, index: Int): TextureSource {
            val set = sets.textureSets(index)
            val highres: FBTexture? = set.highresTexture()
            val lowres: FBTexture? = set.lowresTexture()
            val fb: FBTexture = when {
                (highres == null || preferLowres) && lowres != null -> lowres
                highres != null -> highres
                else -> throw IllegalArgumentException("FBTextureSet doesn't contain any textures.")
            }

            val typeName = try {
                TextureType.getByType(fb.type().toInt())?.toString()
            } catch (e: Throwable) {
                null
            }

            var ktx: ByteArray? = null
            val length = fb.dataLength().toInt()
            if (length != 0) {
                val bytes = ByteArray(length)
                val view = fb.dataAsByteBuffer()
                if (view != null && view.remaining() >= length) {
                    view.get(bytes, 0, length)
                } else {
                    for (i in 0 until length) bytes[i] = fb.data(i).toByte()
                }
                ktx = bytes
            }

            return TextureSource(
                width = fb.width().toInt(),
                height = fb.height().toInt(),
                typeName = typeName,
                pixels = null,
                ktxData = ktx
            )
        }

        private fun deserializeModifiers(chunk: ByteBuffer): List<MovieClipModifierOriginal> {
            val fb = FBMovieClipModifiers.getRootAsFBMovieClipModifiers(chunk)
            val result = ArrayList<MovieClipModifierOriginal>(fb.modifiersLength().toInt())
            for (i in 0 until fb.modifiersLength().toInt()) {
                result.add(MovieClipModifierOriginal(fb.modifiers(i)))
            }
            return result
        }

        private fun createMovieClip(clips: FBMovieClips, index: Int): MovieClipOriginal {
            val clip = clips.clips(index)
            val frameData = matrixDataBuffers?.get(clip.matrixBankIndex().toInt())
            return MovieClipOriginal(clip, resources, frameData)
        }

        private fun deserializeShapes(chunk: ByteBuffer): List<ShapeOriginal> {
            val fb = FBShapes.getRootAsFBShapes(chunk)
            val result = ArrayList<ShapeOriginal>(fb.shapesLength().toInt())
            for (i in 0 until fb.shapesLength().toInt()) {
                result.add(ShapeOriginal(fb.shapes(i), resources))
            }
            return result
        }

        private fun deserializeTextFields(chunk: ByteBuffer): List<TextFieldOriginal> {
            val fb = FBTextFields.getRootAsFBTextFields(chunk)
            val result = ArrayList<TextFieldOriginal>(fb.textFieldsLength().toInt())
            for (i in 0 until fb.textFieldsLength().toInt()) {
                result.add(TextFieldOriginal(fb.textFields(i), resources))
            }
            return result
        }

        private fun deserializeExports(chunk: ByteBuffer): List<Export> {
            val fb = FBExports.getRootAsFBExports(chunk)
            require(fb.exportIdsLength().toInt() == fb.exportNameIdsLength().toInt()) {
                "Export ids and name ids count must be equal!"
            }
            val result = ArrayList<Export>(fb.exportIdsLength().toInt())
            for (i in 0 until fb.exportIdsLength().toInt()) {
                val id = fb.exportIds(i).toInt()
                val nameId = fb.exportNameIds(i).toInt()
                if (nameId == 0) throw RuntimeException("Export name not found! Movie clip id: $id")
                result.add(Export(id, resources.strings(nameId)))
            }
            return result
        }

        private fun deserializeMatrixBanks(): List<ScMatrixBank> {
            val banks = ArrayList<ScMatrixBank>(resources.matrixBanksLength().toInt())
            for (i in 0 until resources.matrixBanksLength().toInt()) {
                val fbBank = resources.matrixBanks(i)

                var matrixCount = fbBank.matricesLength().toInt()
                if (matrixCount == 0) matrixCount = fbBank.shortMatricesLength().toInt()

                val bank = ScMatrixBank(matrixCount, fbBank.colorTransformsLength().toInt())

                if (fbBank.matricesLength().toInt() > 0) {
                    for (j in 0 until matrixCount) {
                        val m = fbBank.matrices(j)
                        bank.getMatrix(j).set(m.a(), m.b(), m.c(), m.d(), m.x(), m.y())
                    }
                } else {
                    for (j in 0 until fbBank.shortMatricesLength().toInt()) {
                        val m = fbBank.shortMatrices(j)
                        bank.getMatrix(j).set(m.a(), m.b(), m.c(), m.d(), m.x(), m.y())
                    }
                }

                for (j in 0 until fbBank.colorTransformsLength().toInt()) {
                    val ct = fbBank.colorTransforms(j)
                    bank.getColorTransform(j).set(
                        ct.r().toInt(), ct.g().toInt(), ct.b().toInt(), ct.a().toInt(),
                        ct.ra().toInt(), ct.ga().toInt(), ct.ba().toInt()
                    )
                }

                banks.add(bank)
            }
            return banks
        }

        private fun getExternalMatrixBanks(metadata: Metadata): ExternalMatrixBanks? {
            if (metadata.externalMatrixBanksSize().toLong() == 0L) return null
            check(metadata.compressedSize().toLong() != 0L) { "compressed size is unknown" }
            data.position((data.position() + metadata.compressedSize().toLong()).toInt())
            return ExternalMatrixBanks.getRootAsExternalMatrixBanks(nested(data))
        }

        private fun deserializeExternalMatrixBanks(
            external: ExternalMatrixBanks,
            matrixBankDataPosition: Int
        ): List<ScMatrixBank> {
            val banks = ArrayList<ScMatrixBank>()
            val count = external.matrixBanksLength().toInt()
            val buffers = arrayOfNulls<ByteBuffer>(count)
            matrixDataBuffers = buffers

            for (i in 0 until count) {
                val eb = external.matrixBanks(i)

                val floatCount = eb.floatMatrixCount().toInt()
                val shortCount = eb.shortMatrixCount().toInt()
                val blockCount = eb.matrixBlockCount().toInt()
                val colorCount = eb.colorTransformCount().toInt()
                val uncompressedCount = floatCount + shortCount
                val totalCount = maxOf(uncompressedCount, blockCount * ScCompressedMatrixBank.BLOCK_SIZE)
                val bank = ScMatrixBank(totalCount, colorCount)

                if (i % 8 == 0) stage("matrix bank ${i + 1}/$count")
                val buffer = zstdDecompressFrameMapped(data, matrixBankDataPosition + eb.offset().toInt()).order(LE)

                for (j in 0 until floatCount) {
                    val a = buffer.getFloat()
                    val b = buffer.getFloat()
                    val c = buffer.getFloat()
                    val d = buffer.getFloat()
                    val x = buffer.getFloat()
                    val y = buffer.getFloat()
                    bank.getMatrix(j).set(a, b, c, d, x, y)
                }

                buffer.position(floatCount * 4 * 6 + blockCount * 4)

                for (j in floatCount until uncompressedCount) {
                    bank.getMatrix(j).set(
                        buffer.getShort(), buffer.getShort(), buffer.getShort(),
                        buffer.getShort(), buffer.getShort(), buffer.getShort()
                    )
                }

                val compressed = ScCompressedMatrixBank(buffer, floatCount, shortCount, blockCount)
                for (j in uncompressedCount until blockCount * ScCompressedMatrixBank.BLOCK_SIZE) {
                    val m: Matrix2x3 = compressed.getMatrix(j)
                    bank.getMatrix(j).set(m.getA(), m.getB(), m.getC(), m.getD(), m.getX(), m.getY())
                }

                buffer.position(floatCount * 4 * 6 + blockCount * 4 + eb.blocksDataSize().toInt() * 2)

                for (j in 0 until colorCount) {
                    val r = buffer.get().toInt() and 0xFF
                    val g = buffer.get().toInt() and 0xFF
                    val b = buffer.get().toInt() and 0xFF
                    val a = buffer.get().toInt() and 0xFF
                    val ra = buffer.get().toInt() and 0xFF
                    val ga = buffer.get().toInt() and 0xFF
                    val ba = buffer.get().toInt() and 0xFF
                    bank.getColorTransform(j).set(r, g, b, a, ra, ga, ba)
                }

                banks.add(bank)

                val frameView = buffer.duplicate()
                val frameStart = eb.frameDataOffset().toInt()
                frameView.position(frameStart)
                frameView.limit(frameStart + eb.frameDataSize().toInt())
                buffers[i] = frameView.slice().order(LE)
            }

            return banks
        }
    }
}
