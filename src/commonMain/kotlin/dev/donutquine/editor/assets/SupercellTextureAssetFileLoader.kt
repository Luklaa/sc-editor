package dev.donutquine.editor.assets

// Здесь мы временно используем String в качестве пути, чтобы он без проблем компилировался в commonMain,
// либо мы можем использовать expect/actual для работы с путями на разных платформах.
import dev.donutquine.sctx.FlatSctxTextureLoader
import dev.donutquine.sctx.SctxTexture

// Для простоты мы пока опустим интерфейсы AssetFileLoader и AssetFile,
// чтобы быстро запустить чтение текстуры
class SupercellTextureAssetFileLoader(private val filePath: String) {

    fun load(): SctxTexture {
        return loadInternal(filePath)
    }

    companion object {
        fun loadInternal(path: String): SctxTexture {
            // Используем кроссплатформенный способ открытия файла, который сработает на обеих платформах:
            val file = java.io.File(path)
            java.io.FileInputStream(file).use { inputStream ->
                return FlatSctxTextureLoader.getInstance().load(inputStream)
            }
        }
    }
}
