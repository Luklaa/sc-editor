package dev.donutquine.editor.renderer.impl.texture.khronos

import team.nulls.ntengine.assets.KhronosTexture
import java.io.ByteArrayOutputStream

object KhronosTextureDataSaver {
    private val HEADER = byteArrayOf(
        0xAB.toByte(), 0x4B.toByte(), 0x54.toByte(), 0x58.toByte(),
        0x20.toByte(), 0x31.toByte(), 0x31.toByte(), 0xBB.toByte(),
        0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte()
    )

    fun encodeKtx(ktx: KhronosTexture): ByteArray {
        val stream = ByteArrayOutputStream()
        stream.write(HEADER)

        // Записываем порядок байт (Little Endian сигнатура) в Big Endian потоке
        writeInt(stream, 0x04030201)

        writeInt(stream, ktx.glType)
        writeInt(stream, ktx.glTypeSize)
        writeInt(stream, ktx.glFormat)
        writeInt(stream, ktx.glInternalFormat)
        writeInt(stream, ktx.glBaseInternalFormat)
        writeInt(stream, ktx.width)
        writeInt(stream, ktx.height)
        writeInt(stream, 0) // pixelDepth
        writeInt(stream, 0) // numberOfArrayElements
        writeInt(stream, 1) // numberOfFaces

        writeInt(stream, ktx.levels.size)
        writeInt(stream, 0) // dict size (dict stuff)

        for (level in ktx.levels) {
            writeInt(stream, level.size)
            stream.write(level)
            val padding = getPadding4(level.size)
            stream.write(ByteArray(padding))
        }

        return stream.toByteArray()
    }

    private fun writeInt(stream: ByteArrayOutputStream, value: Int) {
        stream.write((value ushr 24) and 0xFF)
        stream.write((value ushr 16) and 0xFF)
        stream.write((value ushr 8) and 0xFF)
        stream.write(value and 0xFF)
    }

    private fun getPadding4(offset: Int): Int {
        return 3 - ((offset + 3) % 4)
    }
}
