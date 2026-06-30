package dev.donutquine.utilities

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import dev.donutquine.math.MathHelper
import dev.donutquine.editor.renderer.impl.texture.khronos.KhronosTextureDataSaver
import team.nulls.ntengine.assets.KhronosTexture
import java.awt.image.*
import java.io.File
import java.io.FileOutputStream
import javax.imageio.ImageIO

actual object ImageUtils {
    private val RGBA_MODEL = DirectColorModel(32, 0xff, 0xff00, 0xff0000, -0x1000000)
    private val LUMINANCE_ALPHA_MODEL = DirectColorModel(32, 0xff00, 0xff00, 0xff00, 0xff)

    actual fun flipY(width: Int, height: Int, pixelArray: IntArray) {
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

    actual fun cropPixelArray(pixelArray: IntArray, originalWidth: Int, originalHeight: Int, width: Int, height: Int, offsetX: Int, offsetY: Int): IntArray {
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

    actual fun createBitmap(width: Int, height: Int, pixelArray: IntArray, isLuminanceAlpha: Boolean): ImageBitmap {
        val colorModel = if (isLuminanceAlpha) LUMINANCE_ALPHA_MODEL else RGBA_MODEL
        val sampleModel = colorModel.createCompatibleSampleModel(width, height)
        val dataBufferInt = DataBufferInt(pixelArray, pixelArray.size)
        val writableRaster = Raster.createWritableRaster(sampleModel, dataBufferInt, null)
        val bufferedImage = BufferedImage(colorModel, writableRaster, false, null)
        return bufferedImage.toComposeImageBitmap()
    }

    // Декомпрессия через внешние CLI инструменты KTX Software
    actual fun decompressKtx(ktx: KhronosTexture): ImageBitmap? {
        return try {
            val ktx1Data = KhronosTextureDataSaver.encodeKtx(ktx)
            val tempKtx1 = File.createTempFile("texture", ".ktx1")
            FileOutputStream(tempKtx1).use { it.write(ktx1Data) }

            val tempKtx2 = File(tempKtx1.absolutePath.substringBeforeLast(".") + ".ktx2")
            val tempPng = File(tempKtx1.absolutePath.substringBeforeLast(".") + ".png")

            // Шаг 1. Конвертируем KTX1 в KTX2
            val p1 = ProcessBuilder("ktx2ktx2", tempKtx1.absolutePath).start()
            if (p1.waitFor() != 0) {
                tempKtx1.delete()
                return null
            }
            tempKtx1.delete()

            // Шаг 2. Извлекаем PNG из KTX2
            val p2 = ProcessBuilder("ktx", "extract", tempKtx2.absolutePath, tempPng.absolutePath).start()
            if (p2.waitFor() != 0) {
                tempKtx2.delete()
                return null
            }
            tempKtx2.delete()

            // Шаг 3. Загружаем готовый PNG
            if (tempPng.exists()) {
                val bufferedImage = ImageIO.read(tempPng)
                tempPng.delete()
                bufferedImage.toComposeImageBitmap()
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
