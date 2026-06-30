package dev.donutquine.utilities

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dev.donutquine.math.MathHelper
import team.nulls.ntengine.assets.KhronosTexture
actual object ImageUtils {
    actual fun flipY(width: Int, height: Int, pixelArray: IntArray) {
        // Логика аналогичная
        for (y in 0 until height / 2) {
            val topOffset = y * width
            val bottomOffset = (height - 1 - y) * width
            for (x in 0 until width) {
                val topIndex = topOffset + x
                val bottomIndex = bottomOffset + x
                val pixel = pixelArray[topIndex]
                pixelArray[topIndex] = pixelArray[bottomIndex]
                pixelArray[bottomIndex] = pixel
            }
        }
    }
    actual fun decompressKtx(ktx: KhronosTexture): ImageBitmap? {
        // Заглушка для Android
        return null
    }
    actual fun cropPixelArray(
        pixelArray: IntArray,
        originalWidth: Int,
        originalHeight: Int,
        width: Int,
        height: Int,
        offsetX: Int,
        offsetY: Int
    ): IntArray {
        val startX = MathHelper.clamp(originalWidth / 2 + offsetX, 0, originalWidth)
        val startY = MathHelper.clamp(originalHeight / 2 + offsetY, 0, originalHeight)
        val endX = MathHelper.clamp(startX + width, 0, originalWidth)
        val endY = MathHelper.clamp(startY + height, 0, originalHeight)

        val croppedPixelArray = IntArray(width * height)
        for (x in startX until endX) {
            for (y in startY until endY) {
                val pixelIndex = x + y * originalWidth
                val croppedPixelIndex = (x - startX) + (y - startY) * width
                croppedPixelArray[croppedPixelIndex] = pixelArray[pixelIndex]
            }
        }
        return croppedPixelArray
    }

    actual fun createBitmap(
        width: Int,
        height: Int,
        pixelArray: IntArray,
        isLuminanceAlpha: Boolean
    ): ImageBitmap {
        // На Android мы конвертируем пиксели в системный Bitmap, а затем в ImageBitmap для Compose!
        val bitmap = Bitmap.createBitmap(pixelArray, width, height, Bitmap.Config.ARGB_8888)
        return bitmap.asImageBitmap()
    }
}
