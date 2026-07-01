package team.nulls.ntengine.assets

import team.nulls.ntengine.assets.KhronosTextureLoadingException

class KtxReader(private val bytes: ByteArray) {
    private var position = 0
    private var isLittleEndian = false

    fun getBytes(dest: ByteArray) {
        bytes.copyInto(dest, 0, position, position + dest.size)
        position += dest.size
    }

    fun getInt(): Int {
        val b1 = bytes[position++].toInt() and 0xFF
        val b2 = bytes[position++].toInt() and 0xFF
        val b3 = bytes[position++].toInt() and 0xFF
        val b4 = bytes[position++].toInt() and 0xFF

        return if (isLittleEndian) {
            b1 or (b2 shl 8) or (b3 shl 16) or (b4 shl 24)
        } else {
            (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
        }
    }

    fun setEndianness(endianSignature: Int) {
        if (endianSignature == 0x01020304) {
            isLittleEndian = true
        }
    }

    fun skip(n: Int) {
        position += n
    }
}

object KhronosTextureDataLoader {
    private val HEADER = byteArrayOf(
        0xAB.toByte(), 0x4B.toByte(), 0x54.toByte(), 0x58.toByte(),
        0x20.toByte(), 0x31.toByte(), 0x31.toByte(), 0xBB.toByte(),
        0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
    )

    fun decodeKtx(bytes: ByteArray): KhronosTexture {
        val reader = KtxReader(bytes)

        val header = ByteArray(12)
        reader.getBytes(header)
        if (!header.contentEquals(HEADER)) {
            throw KhronosTextureLoadingException("invalid KTX header")
        }

        val endianSign = reader.getInt()
        reader.setEndianness(endianSign)

        val glType = reader.getInt()
        val glTypeSize = reader.getInt()
        val glFormat = reader.getInt()
        val glInternalFormat = reader.getInt()
        val glBaseInternalFormat = reader.getInt()
        val width = reader.getInt()
        val height = reader.getInt()

        if (reader.getInt() != 0) throw KhronosTextureLoadingException("pixelDepth != 0")
        if (reader.getInt() != 0) throw KhronosTextureLoadingException("numberOfArrayElements != 0")
        if (reader.getInt() != 1) throw KhronosTextureLoadingException("numberOfFaces != 1")

        val mipmapLevels = reader.getInt()
        val dictSize = reader.getInt()

        reader.skip(dictSize)

        val levels = Array(mipmapLevels) { ByteArray(0) }
        for (i in 0 until mipmapLevels) {
            val dataChunkSize = addPadding4(reader.getInt())
            val dataChunk = ByteArray(dataChunkSize)
            reader.getBytes(dataChunk)
            levels[i] = dataChunk
        }

        return KhronosTexture(
            glType, glTypeSize, glFormat, glInternalFormat, glBaseInternalFormat,
            width, height, levels
        )
    }

    private fun addPadding4(offset: Int): Int {
        return offset + (3 - ((offset + 3) % 4))
    }
}
