package dev.donutquine.editor.assets

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

expect fun zstdFrameCompressedSize(source: ByteBuffer, offset: Int): Int

expect fun zstdInputStream(input: InputStream): InputStream

fun zstdDecompressFrameMapped(source: ByteBuffer, offset: Int): ByteBuffer {
    val frameSize = zstdFrameCompressedSize(source, offset)
    val view = source.duplicate()
    view.position(offset)
    view.limit(offset + frameSize)

    val tmp = File.createTempFile("sc2_", ".bin")
    try {
        zstdInputStream(object : InputStream() {
            override fun read(): Int = if (view.hasRemaining()) view.get().toInt() and 0xFF else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (!view.hasRemaining()) return -1
                val n = minOf(len, view.remaining())
                view.get(b, off, n)
                return n
            }
        }).use { input ->
            FileOutputStream(tmp).use { output ->
                val chunk = ByteArray(1 shl 20)
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    output.write(chunk, 0, n)
                }
            }
        }
        check(tmp.length() > 0) { "Decompressed zstd frame at offset $offset is empty" }
        val mapped = RandomAccessFile(tmp, "r").use { raf ->
            raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.length())
        }
        return mapped.order(ByteOrder.LITTLE_ENDIAN)
    } finally {
        if (!tmp.delete()) tmp.deleteOnExit()
    }
}
