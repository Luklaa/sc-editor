package dev.donutquine.editor.assets

import java.nio.ByteBuffer

expect fun zstdDecompressFrameDirect(source: ByteBuffer, offset: Int): ByteBuffer
