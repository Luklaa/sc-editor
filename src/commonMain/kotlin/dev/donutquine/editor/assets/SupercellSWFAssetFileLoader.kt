package dev.donutquine.editor.assets

import dev.donutquine.editor.assets.exceptions.AssetLoadingException
import dev.donutquine.swf.SupercellSWF
import java.io.File

class SupercellSWFAssetFileLoader(private val filePath: String) {
    fun load(): SupercellSWF {
        return loadInternal(filePath)
    }

    companion object {
        fun loadInternal(path: String): SupercellSWF {
            val swf = SupercellSWF()
            val file = File(path)
            try {
                val loaded = swf.load(file.absolutePath, file.name, false)
                if (!loaded) {
                    throw AssetLoadingException(
                        IllegalStateException(
                            "Не удалось распарсить файл \"${file.name}\". " +
                                "Подробности в логах (SLF4J LOGGER.error из supercell-swf) — " +
                                "обычно это неизвестная версия контейнера, повреждённый тег " +
                                "или не найден _tex.sc файл текстур."
                        )
                    )
                }
            } catch (e: AssetLoadingException) {
                throw e
            } catch (e: Exception) {
                throw AssetLoadingException(e)
            }
            return swf
        }
    }
}
