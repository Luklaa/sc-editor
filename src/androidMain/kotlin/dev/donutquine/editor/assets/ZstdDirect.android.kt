package dev.donutquine.editor.assets

import com.github.luben.zstd.Zstd
import com.github.luben.zstd.ZstdInputStream
import java.io.InputStream
import java.nio.ByteBuffer

actual fun zstdFrameCompressedSize(source: ByteBuffer, offset: Int): Int {
    val view = source.duplicate()
    view.position(offset)
    val size = Zstd.findFrameCompressedSize(view)
    check(size > 0 && size <= Int.MAX_VALUE) { "Broken zstd frame at offset $offset" }
    return size.toInt()
}

actual fun zstdInputStream(input: InputStream): InputStream = ZstdInputStream(input)
