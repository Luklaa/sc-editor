package dev.donutquine.editor.assets

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/** Размер zstd-кадра (в сжатом виде), начинающегося с [offset] в direct-буфере [source]. */
expect fun zstdFrameCompressedSize(source: ByteBuffer, offset: Int): Int

/** Оборачивает поток сжатых данных в поток распаковки zstd. */
expect fun zstdInputStream(input: InputStream): InputStream

/**
 * Распаковывает один zstd-кадр, начинающийся с [offset] в direct-буфере [source], во временный файл
 * и возвращает его mmap-отображение (только чтение, little-endian).
 *
 * Почему не обычный direct-буфер: на Android `ByteBuffer.allocateDirect` выделяется в куче Java
 * (non-movable массив) и считается в её лимит, то есть ничего не выигрывает. Страницы mmap-файла
 * в кучу не входят.
 */
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
        // На Linux/Android отображение живёт и после удаления файла; на Windows удаление может не получиться
        // пока файл отображён - тогда удалится при выходе.
        if (!tmp.delete()) tmp.deleteOnExit()
    }
}
