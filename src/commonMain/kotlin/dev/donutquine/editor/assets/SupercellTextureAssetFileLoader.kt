package dev.donutquine.editor.assets

import dev.donutquine.sctx.FlatSctxTextureLoader
import dev.donutquine.sctx.SctxTexture

class SupercellTextureAssetFileLoader(private val filePath: String) {

    fun load(): SctxTexture {
        return loadInternal(filePath)
    }

    companion object {
        fun loadInternal(path: String): SctxTexture {
            val file = java.io.File(path)
            java.io.FileInputStream(file).use { inputStream ->
                return FlatSctxTextureLoader.getInstance().load(inputStream)
            }
        }
    }
}
