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
                // Вызываем метод загрузки из библиотеки supercell-swf.
                // ВАЖНО: swf.load(...) возвращает false, если парсинг не удался
                // (например неизвестная версия контейнера, битый тег, не найдена
                // _tex.sc текстура и т.п.). При false поля exports/movieClips/shapes/
                // textFields в SupercellSWF остаются null, и UI молча показывает
                // пустые списки без единой ошибки. Поэтому здесь нужно явно проверять
                // результат и поднимать исключение, чтобы ошибка не терялась.
                val loaded = swf.load(file.absolutePath, file.name, false)
                if (!loaded) {
                    throw AssetLoadingException(
                        IllegalStateException(
                            "Не удалось распарсить файл \"${file.name}\". " +
                                "Подробности смотри в логах (SLF4J LOGGER.error из supercell-swf) — " +
                                "обычно это неизвестная версия контейнера, повреждённый тег " +
                                "или не найден парный _tex.sc файл текстур."
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