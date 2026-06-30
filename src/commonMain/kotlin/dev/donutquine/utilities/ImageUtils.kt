package dev.donutquine.utilities

import androidx.compose.ui.graphics.ImageBitmap
import team.nulls.ntengine.assets.KhronosTexture

expect object ImageUtils {
    fun flipY(width: Int, height: Int, pixelArray: IntArray)
    fun cropPixelArray(pixelArray: IntArray, originalWidth: Int, originalHeight: Int, width: Int, height: Int, offsetX: Int, offsetY: Int): IntArray
    fun createBitmap(width: Int, height: Int, pixelArray: IntArray, isLuminanceAlpha: Boolean): ImageBitmap
    fun decompressKtx(ktx: KhronosTexture): ImageBitmap?
}

fun rgbaBytesToArgbInts(width: Int, height: Int, rgbaBytes: ByteArray): IntArray {
    val pixels = IntArray(width * height)
    for (i in pixels.indices) {
        val r = rgbaBytes[i * 4].toInt() and 0xFF
        val g = rgbaBytes[i * 4 + 1].toInt() and 0xFF
        val b = rgbaBytes[i * 4 + 2].toInt() and 0xFF
        val a = rgbaBytes[i * 4 + 3].toInt() and 0xFF
        pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    return pixels
}
