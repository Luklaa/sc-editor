package dev.donutquine.editor.assets

import com.github.luben.zstd.Zstd
import java.nio.ByteBuffer

actual fun zstdDecompressFrameDirect(source: ByteBuffer, offset: Int): ByteBuffer {
    val view = source.duplicate()
    view.position(offset)
    val frameSize = Zstd.findFrameCompressedSize(view)
    check(frameSize > 0) { "Broken zstd frame at offset $offset" }
    view.limit(offset + frameSize.toInt())
    @Suppress("DEPRECATION")
    val originalSize = Zstd.decompressedSize(view)
    check(originalSize > 0 && originalSize <= Int.MAX_VALUE) { "Unknown zstd frame size at offset $offset" }
    return Zstd.decompress(view, originalSize.toInt())
}
